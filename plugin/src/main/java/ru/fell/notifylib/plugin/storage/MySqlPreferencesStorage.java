package ru.fell.notifylib.plugin.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;
import ru.fell.notifylib.api.Corner;
import ru.fell.notifylib.core.engine.ToastPreferences;
import ru.fell.notifylib.core.settings.PreferencesCodec;
import ru.fell.notifylib.core.settings.PreferencesStorage;
import ru.fell.notifylib.plugin.Threads;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Общее хранилище: одна строка MySQL на игрока, все сервера за прокси видят одни и те же настройки.
 *
 * <p>Игрок в каждый момент на одном сервере, поэтому живая синхронизация не нужна: читаем на pre-login,
 * пишем при изменении. Все запросы идут в одном потоке, так запись и последующее чтение одного игрока
 * не перепутаются (вышел и быстро перезашёл).
 */
public final class MySqlPreferencesStorage implements PreferencesStorage {

    private static final long PREFETCH_TIMEOUT_SECONDS = 5;
    private static final long PREFETCH_TTL_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final Duration CLOSE_TIMEOUT = Duration.ofSeconds(10);
    private static final int CONNECTION_TIMEOUT_MILLIS = 5000;
    /** Скрытые типы храним через запятую: в ключе запятой быть не может. */
    private static final String SEPARATOR = ",";

    private final HikariDataSource pool;
    private final Logger logger;
    private final ExecutorService worker;
    private final String selectSql;
    private final String upsertSql;
    /** Прочитано на pre-login и ждёт входа. Протухшие записи остаются от отменённых логинов. */
    private final Map<UUID, Prefetched> prefetched = new ConcurrentHashMap<>();

    private record Prefetched(ToastPreferences preferences, long readAt) {
    }

    private MySqlPreferencesStorage(HikariDataSource pool, String table, Logger logger) {
        this.pool = pool;
        this.logger = logger;
        this.worker = Threads.single("NotifyLib-MySQL", logger);
        this.selectSql = "SELECT corner, enabled, sound, hidden FROM `" + table + "` WHERE uuid = ?";
        this.upsertSql = "INSERT INTO `" + table + "` (uuid, corner, enabled, sound, hidden) VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE corner = VALUES(corner), enabled = VALUES(enabled), "
                + "sound = VALUES(sound), hidden = VALUES(hidden)";
    }

    /**
     * Подключается и создаёт таблицу, если её нет. Блокирующий вызов. Если база недоступна, Hikari бросает
     * unchecked-исключение.
     */
    public static MySqlPreferencesStorage open(MySqlSettings settings, Logger logger) throws SQLException {
        HikariDataSource pool = new HikariDataSource(poolConfig(settings));
        try {
            createTable(pool, settings.table());
        } catch (SQLException e) {
            pool.close();
            throw e;
        }
        return new MySqlPreferencesStorage(pool, settings.table(), logger);
    }

    private static HikariConfig poolConfig(MySqlSettings settings) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("NotifyLib");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(settings.jdbcUrl());
        config.setUsername(settings.user());
        config.setPassword(settings.password());
        config.setMaximumPoolSize(settings.poolSize());
        config.setMinimumIdle(1);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
        return config;
    }

    // Имя таблицы уже проверено в MySqlSettings, поэтому его можно подставлять в SQL напрямую.
    private static void createTable(HikariDataSource pool, String table) throws SQLException {
        try (Connection connection = pool.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS `" + table + "` ("
                    + "uuid CHAR(36) NOT NULL PRIMARY KEY, "
                    + "corner VARCHAR(16) NULL, "
                    + "enabled BOOLEAN NOT NULL, "
                    + "sound BOOLEAN NOT NULL, "
                    + "hidden TEXT NOT NULL, "
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") CHARACTER SET ascii");
        }
    }

    @Override
    public void prefetch(UUID playerId) {
        long now = System.nanoTime();
        prefetched.values().removeIf(entry -> now - entry.readAt() > PREFETCH_TTL_NANOS);
        try {
            ToastPreferences preferences = worker.submit(() -> select(playerId))
                    .get(PREFETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            prefetched.put(playerId, new Prefetched(preferences, now));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            // load() попробует ещё раз при входе
            logger.log(Level.WARNING, "Не удалось заранее прочитать настройки уведомлений " + playerId, e);
        }
    }

    @Override
    public CompletableFuture<ToastPreferences> load(Player player) {
        UUID id = player.getUniqueId();
        Prefetched ready = prefetched.remove(id);
        if (ready != null) {
            return CompletableFuture.completedFuture(ready.preferences());
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return select(id);
            } catch (SQLException e) {
                throw new IllegalStateException("Не удалось прочитать настройки уведомлений " + player.getName(), e);
            }
        }, worker);
    }

    @Override
    public void save(Player player, ToastPreferences preferences) {
        UUID id = player.getUniqueId();
        String name = player.getName();
        worker.execute(() -> {
            try {
                upsert(id, preferences);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Не удалось сохранить настройки уведомлений " + name, e);
            }
        });
    }

    @Override
    public void close() {
        Threads.stop(worker, CLOSE_TIMEOUT, logger,
                "Часть настроек уведомлений не сохранилась: MySQL не ответил вовремя");
        pool.close();
    }

    private ToastPreferences select(UUID playerId) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return ToastPreferences.DEFAULT;
                }
                String where = playerId.toString();
                String hidden = row.getString("hidden");
                return new ToastPreferences(PreferencesCodec.corner(row.getString("corner"), where, logger),
                        row.getBoolean("enabled"), row.getBoolean("sound"),
                        PreferencesCodec.keys(hidden == null ? List.of() : Arrays.asList(hidden.split(SEPARATOR)),
                                where, logger));
            }
        }
    }

    private void upsert(UUID playerId, ToastPreferences preferences) throws SQLException {
        try (Connection connection = pool.getConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSql)) {
            statement.setString(1, playerId.toString());
            Corner corner = preferences.corner();
            if (corner == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, corner.name());
            }
            statement.setBoolean(3, preferences.enabled());
            statement.setBoolean(4, preferences.sound());
            statement.setString(5, String.join(SEPARATOR,
                    preferences.hidden().stream().map(Key::asString).sorted().toList()));
            statement.executeUpdate();
        }
    }
}
