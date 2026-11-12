package ru.fell.notifylib.core.engine;

import net.kyori.adventure.sound.Sound;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.CloseReason;
import ru.fell.notifylib.api.Corner;
import ru.fell.notifylib.api.Overflow;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.api.ToastHandle;
import ru.fell.notifylib.api.ToastText;
import ru.fell.notifylib.core.protocol.Protocol;
import ru.fell.notifylib.core.protocol.ShaderPhase;
import ru.fell.notifylib.core.protocol.StackPosition;
import ru.fell.notifylib.core.render.ToastFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Стопка и очередь одного игрока, машина состояний тостов. Только главный поток.
 *
 * <p>Сеть: тост стоит по пакету боссбара на смену фазы (add с IN, STAY, OUT, remove) плюс SHIFT и STAY
 * на каждый шаг по стопке. Шейдер хранит время движения по модулю 16 тиков, поэтому законченное движение
 * надо закрепить через STAY. Несколько изменений тоста за тик склеиваются флагом dirty в одно обновление
 * имени в {@link #flush(long)}.
 *
 * <p>Реентерабельность: закрытие тоста кидает события и колбэки, а они могут кикнуть игрока. После
 * каждого такого вызова проверяем {@link #removed} и больше не трогаем (уже очищенное) состояние.
 */
final class PlayerToasts {

    /** Если пак качается дольше, считаем, что загрузка провалилась. */
    static final int PENDING_TIMEOUT_TICKS = 60 * 20;
    /** Тосты, появившиеся чаще этого, делят один звук, чтобы пачка не играла звуки друг на друга. */
    static final int SOUND_GAP_TICKS = 4;

    final ToastViewer viewer;
    private final ToastEngine engine;
    private final List<ActiveToast> stack = new ArrayList<>(Protocol.MAX_SLOTS);
    private final WaitingQueue queue = new WaitingQueue();
    private DeliveryState delivery;
    private long pendingSince;
    private boolean structureChanged;
    private volatile List<ToastHandle> snapshot = List.of();
    private long lastSoundTick = Long.MIN_VALUE / 2;
    /**
     * До этого тика движок игрока пропускает: без команд раньше ничего не изменится, а любая команда
     * сбрасывает значение в 0 (см. {@code ToastEngine.markBusy}).
     */
    long nextWake;
    boolean busy;
    boolean removed;

    PlayerToasts(ToastEngine engine, ToastViewer viewer, DeliveryState delivery, long now) {
        this.engine = engine;
        this.viewer = viewer;
        this.delivery = delivery;
        this.pendingSince = now;
    }

    boolean hasWork() {
        return !stack.isEmpty() || !queue.isEmpty();
    }

    List<ToastHandle> snapshot() {
        return snapshot;
    }

    // Входящие команды.

    void offer(ActiveToast toast, long now) {
        if (toast.isClosed()) {
            return;
        }
        if (removed) {
            close(toast, CloseReason.QUIT);
            return;
        }
        Toast spec = engine.hooks().beforeShow(viewer.id(), toast, toast.toast());
        if (removed) {
            close(toast, CloseReason.QUIT);
            return;
        }
        if (spec == null) {
            close(toast, CloseReason.CANCELLED);
            return;
        }
        toast.toast(spec);
        ToastPreferences prefs = engine.preferences(viewer.id());
        if (!prefs.enabled() || prefs.hidden().contains(spec.type().key())) {
            close(toast, CloseReason.CANCELLED);
            return;
        }
        ToastContent content = engine.contents().create(viewer.id(), spec);
        if (content == null) {
            close(toast, CloseReason.CANCELLED);
            return;
        }
        toast.content = content;
        if (spec.replaceKey() != null && replaceByKey(toast, spec.replaceKey(), now)) {
            return;
        }
        if (removed) {
            close(toast, CloseReason.QUIT);
            return;
        }
        if (delivery == DeliveryState.FAILED) {
            engine.fallback().deliver(viewer, content);
            close(toast, CloseReason.EXPIRED);
            return;
        }
        admit(toast);
    }

    void updateText(ActiveToast toast, ToastText text, long now) {
        ToastContent content = toast.content;
        if (toast.isClosed() || content == null || toast.state == ToastState.LEAVING) {
            return;
        }
        toast.toast(toast.toast().toBuilder().text(text).build());
        toast.content = engine.contents().withText(viewer.id(), content, text);
        if (isOnScreen(toast)) {
            startTimer(toast, now);
            toast.dirty = true;
        }
    }

    void extend(ActiveToast toast, int extraTicks, long now) {
        ToastContent content = toast.content;
        if (toast.isClosed() || content == null || content.persistent() || toast.state == ToastState.LEAVING) {
            return;
        }
        if (!isOnScreen(toast)) {
            int duration = (int) Math.min(Protocol.MAX_DURATION_TICKS, (long) content.durationTicks() + extraTicks);
            toast.content = new ToastContent(content.type(), content.text(), content.title(), duration, content.sound());
            return;
        }
        long remaining = Math.max(0, toast.expireTick - now) + extraTicks;
        long elapsed = now - toast.timerStartTick;
        if (elapsed + remaining <= Protocol.MAX_DURATION_TICKS) {
            toast.timerTicks = (int) (elapsed + remaining);
        } else {
            toast.timerStartTick = now;
            toast.timerTicks = (int) Math.min(remaining, Protocol.MAX_DURATION_TICKS);
        }
        toast.expireTick = toast.timerStartTick + toast.timerTicks;
        toast.dirty = true;
    }

    void request(ActiveToast toast, CloseReason reason) {
        switch (toast.state) {
            case QUEUED -> {
                queue.remove(toast);
                close(toast, reason);
            }
            case RESERVED -> {
                stack.remove(toast);
                relayout();
                close(toast, reason);
            }
            case ENTERING, SHOWN, SHIFTING -> {
                if (toast.closeRequest == null) {
                    toast.closeRequest = reason;
                }
            }
            default -> {
                // SUBMITTED сюда не доходит, LEAVING и CLOSED и так уже уходят.
            }
        }
    }

    void dismissAll() {
        queue.removeIf(t -> true, t -> close(t, CloseReason.DISMISSED));
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (i < stack.size()) {
                request(stack.get(i), CloseReason.DISMISSED);
            }
        }
    }

    /** Закрывает подходящие тосты сразу, без анимации. Возвращает, закрылось ли хоть что-то. */
    boolean closeNow(Predicate<ActiveToast> filter, CloseReason reason) {
        boolean any = queue.removeIf(filter, t -> close(t, reason));
        boolean fromStack = false;
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (i < stack.size() && filter.test(stack.get(i))) {
                close(stack.remove(i), reason);
                fromStack = true;
            }
        }
        if (fromStack) {
            relayout();
        }
        return any || fromStack;
    }

    void delivery(DeliveryState state, long now) {
        DeliveryState previous = delivery;
        delivery = state;
        if (state == DeliveryState.PENDING && previous != DeliveryState.PENDING) {
            pendingSince = now;
        }
        if (state == DeliveryState.FAILED && previous != DeliveryState.FAILED) {
            failAll();
        }
    }

    void markAllDirty() {
        for (int i = 0; i < stack.size(); i++) {
            stack.get(i).dirty = true;
        }
    }

    // Каждый тик.

    void update(long now) {
        removeLeft(now);
        if (removed) {
            return;
        }
        if (delivery == DeliveryState.PENDING && now - pendingSince > PENDING_TIMEOUT_TICKS) {
            delivery(DeliveryState.FAILED, now);
            if (removed) {
                return;
            }
        }
        admitFromQueue();
        if (stepAll(now)) {
            enterReserved(now);
        }
    }

    /** Убирает тосты, которые уже уехали с экрана. */
    private void removeLeft(long now) {
        boolean leftStack = false;
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (i >= stack.size()) {
                continue;
            }
            ActiveToast toast = stack.get(i);
            if (toast.state == ToastState.LEAVING && !toast.animating(now)) {
                stack.remove(i);
                close(toast, closeReasonOf(toast));
                leftStack = true;
                if (removed) {
                    return;
                }
            }
        }
        if (leftStack) {
            relayout();
        }
    }

    /** Двигает тосты на экране. Возвращает {@code true}, если все стоят на своих местах. */
    private boolean stepAll(long now) {
        boolean settled = true;
        for (int i = 0; i < stack.size(); i++) {
            ActiveToast toast = stack.get(i);
            if (toast.state == ToastState.RESERVED || toast.state == ToastState.LEAVING) {
                continue;
            }
            step(toast, now);
            if (toast.state == ToastState.SHIFTING || toast.displayed != toast.target) {
                settled = false;
            }
        }
        return settled;
    }

    private void enterReserved(long now) {
        for (int i = 0; i < stack.size(); i++) {
            ActiveToast toast = stack.get(i);
            if (toast.state == ToastState.RESERVED && !occupied(toast)) {
                enter(toast, now);
            }
        }
    }

    void flush(long now) {
        if (removed) {
            return;
        }
        long gameTime = 0;
        boolean timeRead = false;
        for (int i = 0; i < stack.size(); i++) {
            ActiveToast toast = stack.get(i);
            UUID bar = toast.bar;
            if (!toast.dirty || bar == null) {
                continue;
            }
            if (!timeRead) {
                gameTime = viewer.gameTime();
                timeRead = true;
            }
            // Здесь только собираем кадр. Рисует и отправляет имя viewer, уже не в главном потоке.
            viewer.showBar(bar, frame(toast, now, gameTime));
            toast.barShown = true;
            toast.dirty = false;
        }
        if (structureChanged) {
            structureChanged = false;
            List<ToastHandle> handles = new ArrayList<>(stack.size() + queue.size());
            handles.addAll(stack);
            queue.addAllTo(handles);
            snapshot = List.copyOf(handles);
        }
        nextWake = computeNextWake(now);
    }

    /**
     * Ближайший тик, когда {@link #update} может что-то поменять без внешних команд: конец анимации,
     * истечение тоста или таймаут пака. Поэтому висящий тост ничего не стоит за тик. Если что-то
     * поменяется уже в следующем тике, будим игрока на нём.
     */
    private long computeNextWake(long now) {
        long next = Long.MAX_VALUE;
        for (int i = 0; i < stack.size(); i++) {
            ActiveToast toast = stack.get(i);
            if (toast.dirty) {
                return now + 1;
            }
            if (toast.animating(now)) {
                next = Math.min(next, toast.animEndTick);
            } else if (toast.state == ToastState.SHOWN && toast.closeRequest == null
                    && toast.displayed == toast.target) {
                next = Math.min(next, toast.expireTick);
            } else {
                // RESERVED ждёт слот, движение начинается или заканчивается, просят закрыть или OUT доиграл.
                return now + 1;
            }
        }
        if (delivery == DeliveryState.PENDING && hasWork()) {
            next = Math.min(next, pendingSince + PENDING_TIMEOUT_TICKS + 1);
        }
        if (!queue.isEmpty() && delivery == DeliveryState.READY && hasFreeSlot()) {
            return now + 1;
        }
        return next;
    }

    // Машина состояний.

    private void step(ActiveToast toast, long now) {
        if (toast.animating(now)) {
            return;
        }
        if (toast.closeRequest != null || now >= toast.expireTick) {
            toast.state = ToastState.LEAVING;
            toast.shiftFrom = null;
            startPhase(toast, ShaderPhase.OUT, now);
            if (tracing()) {
                trace(toast, "OUT");
            }
            return;
        }
        if (toast.displayed != toast.target) {
            StackPosition next = toast.displayed.stepTowards(toast.target);
            if (toast.displayed.isOneStepTo(next)) {
                toast.shiftFrom = toast.displayed;
                toast.displayed = next;
                toast.state = ToastState.SHIFTING;
                startPhase(toast, ShaderPhase.SHIFT, now);
                if (tracing()) {
                    trace(toast, "SHIFT to " + next);
                }
            } else {
                toast.displayed = next;
                settle(toast, now);
            }
            return;
        }
        if (toast.state == ToastState.ENTERING || toast.state == ToastState.SHIFTING) {
            settle(toast, now);
        }
    }

    private void settle(ActiveToast toast, long now) {
        toast.state = ToastState.SHOWN;
        toast.shiftFrom = null;
        startPhase(toast, ShaderPhase.STAY, now);
        if (tracing()) {
            trace(toast, "STAY at " + toast.displayed);
        }
    }

    private void enter(ActiveToast toast, long now) {
        toast.bar = UUID.randomUUID();
        toast.barShown = false;
        toast.state = ToastState.ENTERING;
        toast.displayed = toast.target;
        startPhase(toast, ShaderPhase.IN, now);
        startTimer(toast, now);
        playSound(toast, now);
        if (tracing()) {
            trace(toast, "IN at " + toast.displayed);
        }
    }

    /** Из зарезервированного слота ещё уезжает другой тост. Если въехать сейчас, они наложатся. */
    private boolean occupied(ActiveToast reserved) {
        for (int i = 0; i < stack.size(); i++) {
            ActiveToast other = stack.get(i);
            if (other != reserved && other.state == ToastState.LEAVING && other.displayed == reserved.target) {
                return true;
            }
        }
        return false;
    }

    private static void startPhase(ActiveToast toast, ShaderPhase phase, long now) {
        toast.phase = phase;
        toast.phaseStartTick = now;
        toast.animEndTick = phase == ShaderPhase.STAY ? now : now + Protocol.PHASE_TICKS;
        toast.dirty = true;
    }

    private static void startTimer(ActiveToast toast, long now) {
        ToastContent content = Objects.requireNonNull(toast.content);
        if (content.persistent()) {
            toast.timerTicks = 0;
            toast.expireTick = Long.MAX_VALUE;
        } else {
            toast.timerStartTick = now;
            toast.timerTicks = content.durationTicks();
            toast.expireTick = now + content.durationTicks();
        }
    }

    private void admit(ActiveToast toast) {
        boolean free = hasFreeSlot();
        if (free && delivery == DeliveryState.READY && queue.isEmpty()) {
            place(toast);
            return;
        }
        if (free || delivery != DeliveryState.READY) {
            enqueue(toast);
            return;
        }
        Overflow policy = Objects.requireNonNullElse(toast.toast().overflow(), engine.settings().overflow());
        switch (policy) {
            case QUEUE -> enqueue(toast);
            case DROP -> close(toast, CloseReason.OVERFLOW);
            case REPLACE_OLDEST -> {
                ActiveToast victim = oldestEvictable();
                if (victim == null) {
                    enqueue(toast);
                } else {
                    victim.closeRequest = CloseReason.REPLACED;
                    toast.urgent = true;
                    toast.state = ToastState.QUEUED;
                    queue.add(toast);
                    structureChanged = true;
                }
            }
        }
    }

    private void admitFromQueue() {
        // Сначала очередь: обычно она пустая, и тогда проверка бесплатная.
        while (!queue.isEmpty() && delivery == DeliveryState.READY && hasFreeSlot()) {
            ActiveToast next = queue.pollBest();
            if (next == null) {
                return;
            }
            place(next);
        }
    }

    private void place(ActiveToast toast) {
        int index = engine.settings().order() == StackOrder.CORNER ? 0 : stack.size();
        stack.add(index, toast);
        toast.state = ToastState.RESERVED;
        relayout();
        toast.displayed = toast.target;
        structureChanged = true;
    }

    private void enqueue(ActiveToast toast) {
        toast.state = ToastState.QUEUED;
        queue.add(toast);
        structureChanged = true;
        ActiveToast dropped = queue.trim(engine.settings().queueLimit());
        if (dropped != null) {
            close(dropped, CloseReason.OVERFLOW);
        }
    }

    private boolean replaceByKey(ActiveToast toast, String key, long now) {
        ActiveToast old = findByKey(key, toast);
        if (old == null) {
            return false;
        }
        if (old.state == ToastState.QUEUED || old.state == ToastState.RESERVED) {
            request(old, CloseReason.REPLACED);
            return false;
        }
        takeOver(old, toast, now);
        return true;
    }

    private @Nullable ActiveToast findByKey(String key, ActiveToast except) {
        for (int i = 0; i < stack.size(); i++) {
            ActiveToast candidate = stack.get(i);
            if (candidate != except && candidate.state != ToastState.LEAVING
                    && key.equals(candidate.toast().replaceKey())) {
                return candidate;
            }
        }
        for (int i = 0; i < queue.size(); i++) {
            ActiveToast candidate = queue.get(i);
            if (candidate != except && key.equals(candidate.toast().replaceKey())) {
                return candidate;
            }
        }
        return null;
    }

    /** Новый тост забирает у старого слот, боссбар и анимацию. Меняются только текст и таймер. */
    private void takeOver(ActiveToast old, ActiveToast toast, long now) {
        stack.set(stack.indexOf(old), toast);
        toast.state = old.state;
        toast.bar = old.bar;
        toast.barShown = old.barShown;
        toast.phase = old.phase;
        toast.phaseStartTick = old.phaseStartTick;
        toast.animEndTick = old.animEndTick;
        toast.displayed = old.displayed;
        toast.target = old.target;
        toast.shiftFrom = old.shiftFrom;
        startTimer(toast, now);
        toast.dirty = true;
        old.bar = null;
        old.barShown = false;
        if (old.tall() != toast.tall()) {
            relayout();
        }
        playSound(toast, now);
        structureChanged = true;
        if (tracing()) {
            trace(toast, "заменил " + old);
        }
        close(old, CloseReason.REPLACED);
    }

    private @Nullable ActiveToast oldestEvictable() {
        ActiveToast oldest = null;
        for (int i = 0; i < stack.size(); i++) {
            ActiveToast candidate = stack.get(i);
            boolean evictable = candidate.closeRequest == null && candidate.state != ToastState.LEAVING
                    && candidate.state != ToastState.RESERVED;
            if (evictable && (oldest == null || candidate.seq < oldest.seq)) {
                oldest = candidate;
            }
        }
        return oldest;
    }

    private boolean hasFreeSlot() {
        if (stack.size() >= Protocol.MAX_SLOTS) {
            return false;
        }
        int maxVisible = engine.settings().maxVisible();
        if (stack.size() < maxVisible) {
            return true;
        }
        int staying = 0;
        for (int i = 0; i < stack.size(); i++) {
            ActiveToast toast = stack.get(i);
            if (toast.state != ToastState.LEAVING && toast.closeRequest == null) {
                staying++;
            }
        }
        return staying < maxVisible;
    }

    private void relayout() {
        int tall = 0;
        for (int i = 0; i < stack.size(); i++) {
            ActiveToast toast = stack.get(i);
            toast.target = StackPosition.of(i, tall);
            if (toast.tall()) {
                tall++;
            }
        }
        structureChanged = true;
    }

    /** Пак не загрузился: невидимые тосты уходят в запасной канал, уходящие просто закрываются. */
    private void failAll() {
        queue.removeIf(t -> true, this::fallback);
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (i >= stack.size()) {
                continue;
            }
            ActiveToast toast = stack.remove(i);
            if (toast.state == ToastState.LEAVING || toast.closeRequest != null) {
                close(toast, closeReasonOf(toast));
            } else {
                fallback(toast);
            }
        }
        structureChanged = true;
    }

    private void fallback(ActiveToast toast) {
        ToastContent content = toast.content;
        if (content != null) {
            engine.fallback().deliver(viewer, content);
        }
        close(toast, CloseReason.EXPIRED);
    }

    private static CloseReason closeReasonOf(ActiveToast toast) {
        return toast.closeRequest != null ? toast.closeRequest : CloseReason.EXPIRED;
    }

    private boolean isOnScreen(ActiveToast toast) {
        return toast.state == ToastState.ENTERING || toast.state == ToastState.SHOWN
                || toast.state == ToastState.SHIFTING;
    }

    private void playSound(ActiveToast toast, long now) {
        ToastContent content = toast.content;
        if (content == null || content.sound() == null || now - lastSoundTick < SOUND_GAP_TICKS
                || !engine.settings().sound() || !engine.preferences(viewer.id()).sound()) {
            return;
        }
        Sound sound = content.sound();
        lastSoundTick = now;
        viewer.playSound(sound);
    }

    private ToastFrame frame(ActiveToast toast, long now, long gameTime) {
        ToastContent content = Objects.requireNonNull(toast.content);
        Corner corner = engine.preferences(viewer.id()).cornerOr(engine.settings().corner());
        int phaseTick = (int) Math.floorMod(gameTime - (now - toast.phaseStartTick), (long) Protocol.TICK_MOD);
        ToastFrame.Timer timer = toast.timerTicks > 0
                ? new ToastFrame.Timer(gameTime - (now - toast.timerStartTick), toast.timerTicks)
                : null;
        StackPosition from = toast.phase == ShaderPhase.SHIFT ? toast.shiftFrom : null;
        return new ToastFrame(corner, toast.phase, phaseTick, toast.displayed, from,
                content.type(), content.text(), content.title(), timer);
    }

    private void close(ActiveToast toast, CloseReason reason) {
        UUID bar = toast.bar;
        if (bar != null && toast.barShown) {
            viewer.hideBar(bar);
        }
        toast.bar = null;
        toast.barShown = false;
        structureChanged = true;
        if (tracing()) {
            trace(toast, "закрыт: " + reason);
        }
        toast.finish(reason, engine.hooks());
    }

    private boolean tracing() {
        return engine.settings().debug();
    }

    private void trace(ActiveToast toast, String what) {
        engine.hooks().trace(viewer.id() + " " + toast + ": " + what);
    }
}
