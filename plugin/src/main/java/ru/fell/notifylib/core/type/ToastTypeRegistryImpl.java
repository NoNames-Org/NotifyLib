package ru.fell.notifylib.core.type;

import net.kyori.adventure.key.Key;
import ru.fell.notifylib.api.ToastType;
import ru.fell.notifylib.api.ToastTypeRegistry;
import ru.fell.notifylib.api.ToastTypes;
import ru.fell.notifylib.core.protocol.Glyphs;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Встроенные типы плюс типы из конфига. Чтение идёт из неизменяемого снимка и не блокируется;
 * перезагрузка конфига подменяет снимок целиком.
 */
public final class ToastTypeRegistryImpl implements ToastTypeRegistry {

    /** Блоки глифов встроенных типов во встроенном ресурспаке. */
    private static final char[] BUILTIN_GLYPHS = {'', '', '', ''};
    /** Иконка 9 px плюс 1 px зазора. */
    public static final int DEFAULT_ICON_ADVANCE = 10;

    private volatile Map<Key, RegisteredType> snapshot = Map.of();

    public ToastTypeRegistryImpl() {
        replaceCustom(List.of());
    }

    /** Заменяет типы из конфига. Некорректные должны быть отсеяны заранее, при загрузке конфига. */
    public void replaceCustom(List<RegisteredType> custom) {
        Map<Key, RegisteredType> next = new LinkedHashMap<>();
        for (int i = 0; i < ToastTypes.ALL.size(); i++) {
            ToastType builtin = ToastTypes.ALL.get(i);
            next.put(builtin.key(), new RegisteredType(builtin, BUILTIN_GLYPHS[i], DEFAULT_ICON_ADVANCE));
        }
        for (RegisteredType type : custom) {
            next.putIfAbsent(type.type().key(), type);
        }
        snapshot = Collections.unmodifiableMap(next);
    }

    /** Занят ли блок глифов встроенным типом. */
    public static boolean isBuiltinGlyph(char base) {
        return base >= BUILTIN_GLYPHS[0] && base < BUILTIN_GLYPHS[BUILTIN_GLYPHS.length - 1] + Glyphs.TYPE_BLOCK;
    }

    public Optional<RegisteredType> lookup(Key key) {
        return Optional.ofNullable(snapshot.get(key));
    }

    /** Неизменяемый снимок, встроенные типы идут первыми. */
    public Collection<RegisteredType> registrations() {
        return snapshot.values();
    }

    @Override
    public Optional<ToastType> get(Key key) {
        return lookup(key).map(RegisteredType::type);
    }

    @Override
    public Collection<ToastType> all() {
        return snapshot.values().stream().map(RegisteredType::type).toList();
    }
}
