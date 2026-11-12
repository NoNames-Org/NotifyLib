package ru.fell.notifylib.core.settings;

import net.kyori.adventure.key.Key;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.Corner;
import ru.fell.notifylib.core.util.Enums;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/** Мягкий разбор сохранённых значений для всех хранилищ: битое значение пишется в лог и отбрасывается. */
public final class PreferencesCodec {

    private PreferencesCodec() {
    }

    /**
     * @param where чьё это значение, для лога
     * @return угол, либо {@code null}, то есть угол сервера по умолчанию
     */
    public static @Nullable Corner corner(@Nullable String raw, String where, Logger logger) {
        if (raw == null) {
            return null;
        }
        Corner corner = Enums.parse(Corner.class, raw);
        if (corner == null) {
            logger.warning("Неизвестный угол уведомлений '" + raw + "' у " + where + ", берём угол по умолчанию");
        }
        return corner;
    }

    /** Ключи типов; пустые и некорректные пропускаются. */
    public static Set<Key> keys(Collection<String> raw, String where, Logger logger) {
        Set<Key> keys = new HashSet<>();
        for (String value : raw) {
            String key = value.strip();
            if (key.isEmpty()) {
                continue;
            }
            if (Key.parseable(key)) {
                keys.add(Key.key(key));
            } else {
                logger.warning("Пропущен некорректный скрытый тип уведомлений '" + value + "' у " + where);
            }
        }
        return keys;
    }
}
