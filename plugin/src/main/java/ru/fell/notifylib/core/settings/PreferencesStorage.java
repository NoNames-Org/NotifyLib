package ru.fell.notifylib.core.settings;

import org.bukkit.entity.Player;
import ru.fell.notifylib.core.engine.ToastPreferences;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Где лежат личные настройки: локально в данных игрока на этом сервере или в общей базе, чтобы все серверы
 * за прокси видели одно и то же.
 */
public interface PreferencesStorage {

    /**
     * Вызывается в асинхронном потоке пре-логина, когда объекта игрока ещё нет. Можно блокироваться.
     * Общее хранилище читает настройки здесь, чтобы к входу они уже были готовы.
     */
    default void prefetch(UUID playerId) {
    }

    /**
     * Главный поток; future может завершиться в любом потоке.
     *
     * @return настройки, либо значения по умолчанию, если у игрока их нет
     */
    CompletableFuture<ToastPreferences> load(Player player);

    /** Главный поток. Общее хранилище пишет асинхронно. */
    void save(Player player, ToastPreferences preferences);

    /** Дописывает отложенные записи и освобождает ресурсы. Один раз при выключении плагина. */
    default void close() {
    }
}
