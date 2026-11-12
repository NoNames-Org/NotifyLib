package ru.fell.notifylib.plugin.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Сообщения команд в MiniMessage из {@code messages.yml}. Копия файла внутри jar задаёт список ключей
 * и значения для всего, чего нет в файле сервера. Всё, что приходит от игроков, вставляется через
 * {@code Placeholder.unparsed}, чтобы не парсилось как теги.
 */
public final class Messages {

    public static final String FILE = "messages.yml";

    private final MiniMessage miniMessage;
    private final Map<String, String> values;

    private Messages(MiniMessage miniMessage, Map<String, String> values) {
        this.miniMessage = miniMessage;
        this.values = Map.copyOf(values);
    }

    /** Накладывает файл сервера поверх встроенного; неизвестные ключи игнорируются с предупреждением. */
    public static Messages load(MiniMessage miniMessage, ConfigurationSection file, ConfigurationSection defaults,
                                Consumer<String> warn) {
        Map<String, String> values = new HashMap<>();
        for (String key : defaults.getKeys(false)) {
            values.put(key, defaults.getString(key, ""));
        }
        for (String key : file.getKeys(false)) {
            if (!values.containsKey(key)) {
                warn.accept("Неизвестный ключ " + key + " в " + FILE + " пропущен");
                continue;
            }
            String value = file.getString(key);
            if (value != null) {
                values.put(key, value);
            }
        }
        return new Messages(miniMessage, values);
    }

    public Component prefixed(String key, TagResolver... placeholders) {
        return raw("prefix").append(raw(key, placeholders));
    }

    public Component raw(String key, TagResolver... placeholders) {
        return miniMessage.deserialize(template(key), placeholders);
    }

    public String template(String key) {
        String template = values.get(key);
        if (template == null) {
            throw new IllegalArgumentException("неизвестное сообщение " + key);
        }
        return template;
    }
}
