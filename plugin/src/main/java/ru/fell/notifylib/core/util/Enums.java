package ru.fell.notifylib.core.util;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class Enums {

    private Enums() {
    }

    /** Ищет константу без учёта регистра, {@code -} считается за {@code _}. Без исключений: нет такой — {@code null}. */
    public static <E extends Enum<E>> @Nullable E parse(Class<E> type, String raw) {
        String name = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        return null;
    }
}
