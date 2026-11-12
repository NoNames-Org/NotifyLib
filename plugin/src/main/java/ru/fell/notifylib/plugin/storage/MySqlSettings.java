package ru.fell.notifylib.plugin.storage;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Подключение к общей базе настроек ({@code storage.mysql} в config.yml).
 *
 * @param properties доп. параметры JDBC URL без ведущего {@code ?}
 */
public record MySqlSettings(String host, int port, String database, String user, String password, String table,
                            int poolSize, String properties) {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]{1,64}");

    public MySqlSettings {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(properties, "properties");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port должен быть от 1 до 65535");
        }
        if (!IDENTIFIER.matcher(database).matches() || !IDENTIFIER.matcher(table).matches()) {
            throw new IllegalArgumentException("database и table могут содержать только A-Z, a-z, 0-9 и _");
        }
        if (poolSize < 1 || poolSize > 32) {
            throw new IllegalArgumentException("pool-size должен быть от 1 до 32");
        }
    }

    public String jdbcUrl() {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database;
        return properties.isBlank() ? url : url + "?" + properties;
    }
}
