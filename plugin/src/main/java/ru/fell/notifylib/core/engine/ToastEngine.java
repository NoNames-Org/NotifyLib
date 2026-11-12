package ru.fell.notifylib.core.engine;

import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.CloseReason;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.api.ToastHandle;
import ru.fell.notifylib.api.ToastText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * Движок тостов. Один на сервер, крутится одной повторяющейся задачей, которая зовёт {@link #tick()}.
 *
 * <p>Потоки: публичные методы, меняющие состояние, только кладут команду в lock-free очередь. Команды
 * выполняются на главном потоке в начале следующего тика, так что состояние игроков между потоками не
 * делится. Методы {@code *Now} для обработчиков событий на главном потоке, которым надо сразу.
 *
 * <p>Цена: тик разбирает очередь команд (с лимитом по времени) и трогает только игроков, у которых что-то
 * наступило: кончилась анимация, истёк тост, пришла команда. Тосты, которые просто висят, стоят одно
 * сравнение за тик. Без тостов тик проверяет пустую очередь и выходит.
 *
 * <p>Реентерабельность: слушатели событий и колбэки закрытия выполняются внутри тика и могут кикнуть
 * игрока или выключить плагин. Удаление игрока во время тика только ставит флаг, а из списка busy его
 * убирает цикл тика.
 */
public final class ToastEngine {

    /** Если команд в очереди больше, новые отклоняются: какой-то плагин заваливает API. */
    static final int MAX_PENDING_COMMANDS = 50_000;
    /** Сколько времени за тик можно тратить на очередь команд. Остальное ждёт следующего тика. */
    private static final long DRAIN_BUDGET_NANOS = 2_000_000;
    private static final int DRAIN_CLOCK_EVERY = 16;

    private final Queue<Runnable> commands = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();
    private final Map<UUID, PlayerToasts> players = new ConcurrentHashMap<>();
    private final List<PlayerToasts> busy = new ArrayList<>();
    private final ContentFactory contents;
    private final EngineHooks hooks;
    private final PreferencesSource preferences;
    private volatile EngineSettings settings;
    private volatile FallbackDelivery fallback = FallbackDelivery.NONE;
    private volatile boolean stopped;
    private volatile boolean overflowReported;
    /** Вызывается, когда появилась работа: пришла команда или игрок стал busy. */
    private volatile Runnable onWork = () -> {
    };
    private boolean ticking;
    private long tick;
    private @Nullable LongSupplier clock;
    private long clockBase;

    public ToastEngine(EngineSettings settings, ContentFactory contents, EngineHooks hooks,
                       PreferencesSource preferences) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.contents = Objects.requireNonNull(contents, "contents");
        this.hooks = Objects.requireNonNull(hooks, "hooks");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    // Любой поток.

    /**
     * Отправляет тост. Валидировать его надо до вызова. Если движок остановлен или завален,
     * возвращается уже закрытый хэндл.
     */
    public ActiveToast show(UUID playerId, Toast toast, @Nullable String owner) {
        ActiveToast handle = new ActiveToast(this, playerId, toast, owner);
        if (stopped || !accept(() -> offer(handle))) {
            handle.abandon();
        }
        return handle;
    }

    /** Игрок вышел: все его тосты закрываются с {@link CloseReason#QUIT}. */
    public void quit(UUID playerId) {
        execute(() -> quitNow(playerId));
    }

    /** Перерисовать тосты под часы нового мира. */
    public void worldChanged(UUID playerId) {
        withPlayer(playerId, PlayerToasts::markAllDirty);
    }

    /** Перерисовать тосты после смены настроек (угла). */
    public void preferencesChanged(UUID playerId) {
        withPlayer(playerId, PlayerToasts::markAllDirty);
    }

    public void deliveryChanged(UUID playerId, DeliveryState state) {
        withPlayer(playerId, player -> player.delivery(state, tick));
    }

    public void dismissAll(UUID playerId) {
        withPlayer(playerId, PlayerToasts::dismissAll);
    }

    /** Закрывает подходящие тосты у всех игроков разом, без анимации. */
    public void closeWhere(Predicate<ActiveToast> filter, CloseReason reason) {
        execute(() -> closeWhereNow(filter, reason));
    }

    /** Видимые и ждущие тосты игрока на момент прошлого тика. */
    public List<ToastHandle> active(UUID playerId) {
        PlayerToasts player = players.get(playerId);
        return player == null ? List.of() : player.snapshot();
    }

    public void settings(EngineSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    public void fallback(FallbackDelivery fallback) {
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    /**
     * Колбэк, который снова запускает тики, когда появилась работа
     * (см. {@link ru.fell.notifylib.core.scheduler.IdleTicker}). Может вызываться из любого потока.
     */
    public void onWork(Runnable listener) {
        this.onWork = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Синхронизирует счётчик тиков движка с сервером, пока движок простаивает и не тикает. Иначе таймауты
     * в тиках (ожидание ресурспака) растянулись бы. Без часов счётчик растёт только в {@link #tick()}.
     * Главный поток, до первого тика.
     */
    public void clock(LongSupplier serverTicks) {
        this.clock = Objects.requireNonNull(serverTicks, "serverTicks");
        this.clockBase = serverTicks.getAsLong() - tick;
    }

    /** Тик ничего не сделает: команд нет и тостов ни у кого нет. Главный поток. */
    public boolean idle() {
        return commands.isEmpty() && busy.isEmpty();
    }

    public boolean isStopped() {
        return stopped;
    }

    // Главный поток.

    /**
     * Регистрирует зашедшего игрока. Делается сразу в обработчике входа, чтобы кик до следующего тика
     * не оставил висящую запись.
     */
    public void joinNow(ToastViewer viewer, DeliveryState delivery) {
        PlayerToasts previous = players.put(viewer.id(), new PlayerToasts(this, viewer, delivery, now()));
        if (previous != null) {
            remove(previous, CloseReason.QUIT);
        }
    }

    /**
     * Забывает игрока сразу, пока объект игрока ещё годится для событий закрытия. Может вызываться
     * повторно изнутри тика.
     */
    public void quitNow(UUID playerId) {
        PlayerToasts player = players.remove(playerId);
        if (player != null) {
            remove(player, CloseReason.QUIT);
        }
    }

    /**
     * Закрывает подходящие тосты сразу. Нужно при выключении плагина, чтобы его слушатели закрытия
     * успели отработать до выгрузки классов. Сначала разбираются ждущие команды, чтобы поймать и тосты,
     * отправленные этим плагином в этом же тике.
     */
    public void closeWhereNow(Predicate<ActiveToast> filter, CloseReason reason) {
        if (!ticking) {
            drain(Long.MAX_VALUE);
        }
        for (PlayerToasts player : players.values()) {
            if (player.closeNow(filter, reason)) {
                markBusy(player);
            }
        }
    }

    /** Выполняет команды, двигает busy-игроков и отправляет изменившиеся имена боссбаров. */
    public void tick() {
        if (stopped) {
            return;
        }
        ticking = true;
        try {
            // Догоняем тики сервера, пропущенные, пока движок простаивал.
            long previous = Math.max(tick, serverTicks() - 1);
            tick = previous;
            if (!commands.isEmpty()) {
                drain(System.nanoTime() + DRAIN_BUDGET_NANOS);
            }
            tick = Math.max(previous + 1, serverTicks());
            advanceBusy();
        } finally {
            ticking = false;
        }
    }

    /**
     * Останавливает движок: выполняет уже отправленные команды, прячет все тосты и закрывает их с
     * {@link CloseReason#PLUGIN_DISABLED}. Всё, что придёт потом, закрывается сразу.
     */
    public void shutdown() {
        drain(Long.MAX_VALUE);
        stopped = true;
        for (PlayerToasts player : players.values()) {
            player.closeNow(t -> true, CloseReason.PLUGIN_DISABLED);
        }
        players.clear();
        busy.clear();
        drain(Long.MAX_VALUE);
    }

    /** Сколько игроков сейчас с тостами. Для метрик и тестов. */
    public int busyPlayers() {
        return busy.size();
    }

    // Для ActiveToast и PlayerToasts.

    void execute(Runnable command) {
        if (!accept(command)) {
            reportOverflow();
        }
    }

    void updateText(ActiveToast toast, ToastText text) {
        withPlayerNow(toast, player -> player.updateText(toast, text, now()));
    }

    void extend(ActiveToast toast, int extraTicks) {
        withPlayerNow(toast, player -> player.extend(toast, extraTicks, now()));
    }

    void request(ActiveToast toast, CloseReason reason) {
        withPlayerNow(toast, player -> player.request(toast, reason));
    }

    EngineSettings settings() {
        return settings;
    }

    FallbackDelivery fallback() {
        return fallback;
    }

    EngineHooks hooks() {
        return hooks;
    }

    ContentFactory contents() {
        return contents;
    }

    ToastPreferences preferences(UUID playerId) {
        return preferences.preferences(playerId);
    }

    // Внутреннее.

    private void advanceBusy() {
        for (int i = 0; i < busy.size(); ) {
            PlayerToasts player = busy.get(i);
            if (!player.removed && player.nextWake > tick) {
                // Ничего не наступило, тосты просто висят (см. PlayerToasts.computeNextWake).
                i++;
                continue;
            }
            advance(player);
            if (player.hasWork() && !player.removed) {
                i++;
            } else {
                player.busy = false;
                player.nextWake = 0;
                int last = busy.size() - 1;
                busy.set(i, busy.get(last));
                busy.remove(last);
            }
        }
    }

    /** Тики сервера с момента установки {@link #clock} в шкале движка. Без часов это собственный счётчик. */
    private long serverTicks() {
        LongSupplier current = clock;
        return current == null ? tick : current.getAsLong() - clockBase;
    }

    /** Текущий тик для работы вне {@link #tick()}: никогда не отстаёт от сервера. */
    private long now() {
        if (!ticking) {
            tick = Math.max(tick, serverTicks());
        }
        return tick;
    }

    private boolean accept(Runnable command) {
        if (pending.incrementAndGet() > MAX_PENDING_COMMANDS) {
            pending.decrementAndGet();
            reportOverflow();
            return false;
        }
        commands.add(command);
        onWork.run();
        return true;
    }

    private void drain(long deadline) {
        int processed = 0;
        Runnable command;
        while ((command = commands.poll()) != null) {
            pending.decrementAndGet();
            try {
                command.run();
            } catch (RuntimeException e) {
                hooks.engineFailed("команда уведомления", e);
            }
            if (++processed % DRAIN_CLOCK_EVERY == 0 && System.nanoTime() > deadline) {
                return;
            }
        }
        overflowReported = false;
    }

    private void offer(ActiveToast handle) {
        PlayerToasts player = players.get(handle.playerId());
        if (player == null) {
            handle.finish(CloseReason.CANCELLED, hooks);
            return;
        }
        player.offer(handle, tick);
        markBusy(player);
    }

    private void advance(PlayerToasts player) {
        if (player.removed) {
            return;
        }
        try {
            player.update(tick);
            player.flush(tick);
        } catch (RuntimeException e) {
            hooks.engineFailed("уведомления игрока " + player.viewer.id(), e);
            player.closeNow(t -> true, CloseReason.DISMISSED);
        }
    }

    /** Закрывает всё у удалённого игрока. Из списка busy его выкинет цикл тика. */
    private void remove(PlayerToasts player, CloseReason reason) {
        player.removed = true;
        player.closeNow(t -> true, reason);
        if (!ticking && player.busy) {
            player.busy = false;
            busy.remove(player);
        }
    }

    private void reportOverflow() {
        if (!overflowReported) {
            overflowReported = true;
            hooks.engineFailed("в очереди больше " + MAX_PENDING_COMMANDS + " команд, новые уведомления "
                    + "отклоняются, пока очередь не разгрузится (какой-то плагин спамит API?)",
                    new IllegalStateException("очередь команд уведомлений переполнена"));
        }
    }

    private void withPlayer(UUID playerId, Consumer<PlayerToasts> action) {
        execute(() -> {
            PlayerToasts player = players.get(playerId);
            if (player != null) {
                action.accept(player);
                markBusy(player);
            }
        });
    }

    private void withPlayerNow(ActiveToast toast, Consumer<PlayerToasts> action) {
        PlayerToasts player = players.get(toast.playerId());
        if (player != null && !toast.isClosed()) {
            action.accept(player);
            markBusy(player);
        }
    }

    /** Будит игрока после команды. Любое внешнее изменение состояния игрока проходит через этот метод. */
    private void markBusy(PlayerToasts player) {
        player.nextWake = 0;
        if (!player.busy && !player.removed) {
            player.busy = true;
            busy.add(player);
            onWork.run();
        }
    }
}
