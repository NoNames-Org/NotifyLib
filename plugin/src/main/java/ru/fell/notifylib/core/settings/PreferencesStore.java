package ru.fell.notifylib.core.settings;

import org.bukkit.entity.Player;
import ru.fell.notifylib.core.engine.PreferencesSource;
import ru.fell.notifylib.core.engine.ToastPreferences;
import ru.fell.notifylib.core.scheduler.TaskScheduler;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Личные настройки: держатся в памяти для чтения без блокировок и сохраняются через {@link PreferencesStorage}
 * (данные игрока или общая база). Игрок попадает в кэш только после загрузки настроек; до этого чтение
 * отдаёт значения по умолчанию, а изменения игнорируются.
 */
public final class PreferencesStore implements PreferencesSource {

    private final Map<UUID, ToastPreferences> cache = new ConcurrentHashMap<>();
    private final PreferencesStorage storage;
    private final TaskScheduler scheduler;
    private final Logger logger;
    private final BiConsumer<UUID, ToastPreferences> writer;

    /**
     * @param scheduler нужен, чтобы вернуть в главный поток асинхронно завершившуюся загрузку
     * @param writer    сохраняет изменённые настройки в главном потоке (поиск игрока + {@link #save})
     */
    public PreferencesStore(PreferencesStorage storage, TaskScheduler scheduler, Logger logger,
                            BiConsumer<UUID, ToastPreferences> writer) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public ToastPreferences preferences(UUID playerId) {
        return cache.getOrDefault(playerId, ToastPreferences.DEFAULT);
    }

    /** Читает настройки заранее, до входа. Асинхронный поток пре-логина, может блокироваться. */
    public void prefetch(UUID playerId) {
        storage.prefetch(playerId);
    }

    /**
     * Загружает настройки зашедшего игрока. Главный поток. Локальные и заранее прочитанные применяются сразу,
     * остальные на одном из следующих тиков, если игрок ещё в сети.
     *
     * @param loaded вызывается в главном потоке, когда настройки на месте
     */
    public void load(Player player, Consumer<UUID> loaded) {
        String name = player.getName();
        CompletableFuture<ToastPreferences> future = storage.load(player).exceptionally(error -> {
            logger.log(Level.WARNING, "Не удалось загрузить настройки уведомлений " + name
                    + ", используются стандартные", error.getCause() != null ? error.getCause() : error);
            return ToastPreferences.DEFAULT;
        });
        if (future.isDone()) {
            apply(player, future.join(), loaded);
        } else {
            future.thenAccept(preferences -> scheduler.main(() -> apply(player, preferences, loaded)));
        }
    }

    private void apply(Player player, ToastPreferences preferences, Consumer<UUID> loaded) {
        // Если игрок вышел (или перезашёл, это уже другой объект Player) во время загрузки, результат выкидываем.
        if (!player.isOnline()) {
            return;
        }
        cache.put(player.getUniqueId(), preferences);
        loaded.accept(player.getUniqueId());
    }

    public void forget(UUID playerId) {
        cache.remove(playerId);
    }

    /**
     * Атомарно меняет настройки и отдаёт их на сохранение через writer. Потокобезопасно. Для игроков не в сети
     * или ещё не загруженных ничего не делает.
     *
     * @return новые настройки
     */
    public ToastPreferences update(UUID playerId, UnaryOperator<ToastPreferences> change) {
        // Только загруженные игроки: запись, созданная после выхода, так и осталась бы в кэше навсегда.
        ToastPreferences next = cache.computeIfPresent(playerId, (id, current) -> change.apply(current));
        if (next == null) {
            return ToastPreferences.DEFAULT;
        }
        writer.accept(playerId, next);
        return next;
    }

    /** Главный поток. */
    public void save(Player player, ToastPreferences preferences) {
        storage.save(player, preferences);
    }

    /** Дописывает отложенные записи хранилища. При выключении плагина. */
    public void close() {
        storage.close();
    }
}
