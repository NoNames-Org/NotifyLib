package ru.fell.notifylib.core.engine;

import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.CloseReason;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.api.ToastHandle;
import ru.fell.notifylib.api.ToastText;
import ru.fell.notifylib.core.protocol.ShaderPhase;
import ru.fell.notifylib.core.protocol.StackPosition;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Тост одного игрока и его публичный хэндл.
 *
 * <p>Публичные методы потокобезопасны: они только ставят команды в очередь движка. Package-private поля
 * трогает только главный поток.
 */
public final class ActiveToast implements ToastHandle {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final long MILLIS_PER_TICK = 50;

    final long seq = SEQUENCE.incrementAndGet();
    private final ToastEngine engine;
    private final UUID playerId;
    private final @Nullable String owner;
    private volatile Toast toast;
    private volatile boolean closed;

    // Дальше только главный поток.
    ToastState state = ToastState.SUBMITTED;
    @Nullable ToastContent content;
    /** Id боссбара, пока тост на экране. */
    @Nullable UUID bar;
    boolean barShown;
    ShaderPhase phase = ShaderPhase.STAY;
    long phaseStartTick;
    long animEndTick;
    StackPosition displayed = StackPosition.CORNER;
    StackPosition target = StackPosition.CORNER;
    @Nullable StackPosition shiftFrom;
    long expireTick = Long.MAX_VALUE;
    long timerStartTick;
    int timerTicks;
    @Nullable CloseReason closeRequest;
    boolean urgent;
    boolean dirty;
    private @Nullable List<Consumer<CloseReason>> listeners;
    private volatile @Nullable CloseReason closedWith;

    ActiveToast(ToastEngine engine, UUID playerId, Toast toast, @Nullable String owner) {
        this.engine = engine;
        this.playerId = playerId;
        this.toast = toast;
        this.owner = owner;
    }

    @Override
    public UUID playerId() {
        return playerId;
    }

    @Override
    public Toast toast() {
        return toast;
    }

    /** Плагин, который показал тост, если известен. */
    public @Nullable String owner() {
        return owner;
    }

    @Override
    public void text(Component text) {
        submitText(ToastText.of(text));
    }

    @Override
    public void text(String miniMessage) {
        submitText(ToastText.miniMessage(miniMessage));
    }

    private void submitText(ToastText value) {
        engine.execute(() -> engine.updateText(this, value));
    }

    @Override
    public void extend(Duration extra) {
        Objects.requireNonNull(extra, "extra");
        if (extra.isNegative() || extra.isZero()) {
            throw new IllegalArgumentException("продление должно быть положительным");
        }
        long ticks = Math.min(Integer.MAX_VALUE, (extra.toMillis() + MILLIS_PER_TICK - 1) / MILLIS_PER_TICK);
        engine.execute(() -> engine.extend(this, (int) ticks));
    }

    @Override
    public void dismiss() {
        engine.execute(() -> engine.request(this, CloseReason.DISMISSED));
    }

    @Override
    public boolean isActive() {
        return !closed;
    }

    @Override
    public ToastHandle onClose(Consumer<CloseReason> listener) {
        Objects.requireNonNull(listener, "listener");
        CloseReason abandoned = closedWith;
        if (abandoned != null && state == ToastState.CLOSED && content == null && engine.isStopped()) {
            notify(listener, abandoned, engine.hooks());
            return this;
        }
        engine.execute(() -> addListener(listener));
        return this;
    }

    @Override
    public String toString() {
        return "Toast#" + seq + "[" + state + ", " + toast.type().key().asString() + "]";
    }

    // Главный поток.

    void toast(Toast toast) {
        this.toast = toast;
    }

    boolean animating(long now) {
        return now < animEndTick;
    }

    boolean tall() {
        ToastContent current = content;
        return current != null && current.tall();
    }

    boolean isClosed() {
        return closed;
    }

    /**
     * Закрывает тост без событий: движок остановлен или завален и до тоста уже не дойдёт.
     * Слушатели, добавленные после этого, вызываются сразу в потоке, который их добавил.
     */
    void abandon() {
        state = ToastState.CLOSED;
        closedWith = engine.isStopped() ? CloseReason.PLUGIN_DISABLED : CloseReason.OVERFLOW;
        closed = true;
    }

    /** Закрывает тост и оповещает событие и слушателей. Ошибка одного не мешает остальным. */
    void finish(CloseReason reason, EngineHooks hooks) {
        if (closed) {
            return;
        }
        state = ToastState.CLOSED;
        closed = true;
        closedWith = reason;
        content = null;
        bar = null;
        // Исключения слушателей события Bukkit ловит и логирует сам.
        hooks.afterClose(playerId, this, toast, reason);
        List<Consumer<CloseReason>> current = listeners;
        listeners = null;
        if (current != null) {
            for (Consumer<CloseReason> listener : current) {
                notify(listener, reason, hooks);
            }
        }
    }

    private void addListener(Consumer<CloseReason> listener) {
        CloseReason reason = closedWith;
        if (reason != null) {
            notify(listener, reason, engine.hooks());
            return;
        }
        if (listeners == null) {
            listeners = new ArrayList<>(1);
        }
        listeners.add(listener);
    }

    private void notify(Consumer<CloseReason> listener, CloseReason reason, EngineHooks hooks) {
        try {
            listener.accept(reason);
        } catch (RuntimeException | LinkageError e) {
            hooks.callbackFailed(owner, e);
        }
    }
}
