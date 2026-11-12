package ru.fell.notifylib.plugin.config;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.Corner;
import ru.fell.notifylib.api.Overflow;
import ru.fell.notifylib.api.ToastType;
import ru.fell.notifylib.api.ToastTypes;
import ru.fell.notifylib.core.content.FallbackMode;
import ru.fell.notifylib.core.engine.EngineSettings;
import ru.fell.notifylib.core.engine.StackOrder;
import ru.fell.notifylib.core.protocol.Glyphs;
import ru.fell.notifylib.core.protocol.Protocol;
import ru.fell.notifylib.core.type.RegisteredType;
import ru.fell.notifylib.core.type.ToastTypeRegistryImpl;
import ru.fell.notifylib.core.util.Enums;
import ru.fell.notifylib.plugin.storage.MySqlSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Разбор {@code config.yml}. Никогда не падает: кривое значение уходит в {@code warn} и заменяется дефолтом. */
public final class ConfigLoader {

    private static final Pattern DURATION = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(ms|s|t)?");
    private static final int TICKS_PER_SECOND = 20;
    private static final int MAX_ICON_ADVANCE = 16;
    private static final EngineSettings DEFAULTS = EngineSettings.DEFAULTS;

    private final MiniMessage miniMessage;
    private final Consumer<String> warn;

    public ConfigLoader(MiniMessage miniMessage, Consumer<String> warn) {
        this.miniMessage = miniMessage;
        this.warn = warn;
    }

    public NotifyLibConfig load(ConfigurationSection root, ConfigurationSection messageFile,
                                ConfigurationSection messageDefaults) {
        EngineSettings engine = new EngineSettings(
                enumValue(root, "defaults.corner", Corner.class, DEFAULTS.corner()),
                duration(root, "defaults.duration", DEFAULTS.defaultDurationTicks()),
                intValue(root, "defaults.max-visible", 1, Protocol.MAX_SLOTS, DEFAULTS.maxVisible()),
                enumValue(root, "defaults.overflow", Overflow.class, DEFAULTS.overflow()),
                intValue(root, "defaults.queue-limit", 0, 100, DEFAULTS.queueLimit()),
                bool(root, "defaults.sound", DEFAULTS.sound()),
                enumValue(root, "defaults.new-position", StackOrder.class, DEFAULTS.order()),
                bool(root, "debug", DEFAULTS.debug()));
        FallbackMode fallback = enumValue(root, "fallback", FallbackMode.class, FallbackMode.ACTIONBAR);
        Messages messages = Messages.load(miniMessage, messageFile, messageDefaults, warn);
        return new NotifyLibConfig(engine, fallback, types(root), messages,
                mysql(root.getConfigurationSection("storage.mysql")));
    }

    /**
     * Свои типы для команд. Поля: {@code key}, {@code glyph} (первый из четырёх PUA-символов в паке, hex),
     * {@code bar-color}, необязательные {@code icon-width} и {@code sound}. Кривые записи пропускаются.
     */
    private List<RegisteredType> types(ConfigurationSection root) {
        List<RegisteredType> types = new ArrayList<>();
        Set<Key> keys = new HashSet<>();
        Set<Character> glyphs = new HashSet<>();
        List<Map<?, ?>> entries = root.getMapList("types");
        for (int i = 0; i < entries.size(); i++) {
            try {
                types.add(type(entries.get(i), keys, glyphs));
            } catch (RuntimeException e) {
                warn.accept("types[" + i + "] пропущен: " + e.getMessage());
            }
        }
        return types;
    }

    private static RegisteredType type(Map<?, ?> entry, Set<Key> keys, Set<Character> glyphs) {
        Key key = Key.key(String.valueOf(entry.get("key")));
        if (ToastTypes.NAMESPACE.equals(key.namespace()) || !keys.add(key)) {
            throw new IllegalArgumentException("ключ " + key.asString() + " зарезервирован или повторяется");
        }
        char glyph = glyph(String.valueOf(entry.get("glyph")));
        if (ToastTypeRegistryImpl.isBuiltinGlyph(glyph) || !glyphs.add(glyph)) {
            throw new IllegalArgumentException("глиф " + entry.get("glyph") + " уже занят");
        }
        TextColor color = TextColor.fromHexString(String.valueOf(entry.get("bar-color")));
        if (color == null) {
            throw new IllegalArgumentException("bar-color должен быть вида \"#FFB000\"");
        }
        Object width = entry.get("icon-width");
        int iconAdvance = width == null ? ToastTypeRegistryImpl.DEFAULT_ICON_ADVANCE
                : Integer.parseInt(String.valueOf(width)) + 1;
        if (iconAdvance < 1 || iconAdvance > MAX_ICON_ADVANCE) {
            throw new IllegalArgumentException("icon-width должен быть от 0 до 15");
        }
        Object soundKey = entry.get("sound");
        Sound sound = soundKey == null ? null
                : Sound.sound(Key.key(String.valueOf(soundKey)), Sound.Source.MASTER, 1F, 1F);
        return new RegisteredType(new ToastType(key, color, sound), glyph, iconAdvance);
    }

    /** Пустой {@code host} значит локальное хранилище; при кривых настройках тоже откатываемся на него. */
    private @Nullable MySqlSettings mysql(@Nullable ConfigurationSection section) {
        if (section == null || section.getString("host", "").isBlank()) {
            return null;
        }
        try {
            return new MySqlSettings(section.getString("host", "").strip(), section.getInt("port", 3306),
                    section.getString("database", "minecraft"), section.getString("user", "root"),
                    section.getString("password", ""), section.getString("table", "notifylib_settings"),
                    section.getInt("pool-size", 4), section.getString("properties", ""));
        } catch (IllegalArgumentException e) {
            warn.accept("storage.mysql настроен неверно (" + e.getMessage() + "), настройки хранятся локально");
            return null;
        }
    }

    private static char glyph(String raw) {
        String hex = raw.startsWith("U+") || raw.startsWith("0x") ? raw.substring(2) : raw;
        int value = Integer.parseInt(hex.trim(), 16);
        if (value < Glyphs.TYPE_FIRST || value + Glyphs.TYPE_BLOCK - 1 > Glyphs.TYPE_LAST) {
            throw new IllegalArgumentException("глиф должен быть в диапазоне E200..F8FC");
        }
        return (char) value;
    }

    private <E extends Enum<E>> E enumValue(ConfigurationSection root, String path, Class<E> type, E fallback) {
        String raw = root.getString(path);
        if (raw == null) {
            return fallback;
        }
        E value = Enums.parse(type, raw);
        if (value == null) {
            warn.accept(path + ": неизвестное значение '" + raw + "', используется " + fallback);
            return fallback;
        }
        return value;
    }

    private int intValue(ConfigurationSection root, String path, int min, int max, int fallback) {
        if (!root.contains(path)) {
            return fallback;
        }
        Object raw = root.get(path);
        if (!(raw instanceof Number number) || number.intValue() < min || number.intValue() > max) {
            warn.accept(path + ": '" + raw + "' должно быть числом от " + min + " до " + max + ", используется " + fallback);
            return fallback;
        }
        return number.intValue();
    }

    private boolean bool(ConfigurationSection root, String path, boolean fallback) {
        if (!root.contains(path)) {
            return fallback;
        }
        Object raw = root.get(path);
        if (!(raw instanceof Boolean value)) {
            warn.accept(path + ": '" + raw + "' должно быть true или false, используется " + fallback);
            return fallback;
        }
        return value;
    }

    /** Понимает {@code 5s}, {@code 2.5s}, {@code 100t}, {@code 4000ms}; число без единиц считается секундами. */
    private int duration(ConfigurationSection root, String path, int fallback) {
        String raw = root.getString(path);
        if (raw == null) {
            return fallback;
        }
        Matcher matcher = DURATION.matcher(raw.trim().toLowerCase(Locale.ROOT));
        if (matcher.matches()) {
            double value = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2) == null ? "s" : matcher.group(2);
            long ticks = switch (unit) {
                case "t" -> Math.round(value);
                case "ms" -> Math.round(value / 50.0);
                default -> Math.round(value * TICKS_PER_SECOND);
            };
            if (ticks >= TICKS_PER_SECOND && ticks <= Protocol.MAX_DURATION_TICKS) {
                return (int) ticks;
            }
        }
        warn.accept(path + ": '" + raw + "' должно быть от 1s до 51s, используется " + fallback / TICKS_PER_SECOND + "s");
        return fallback;
    }
}
