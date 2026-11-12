package ru.fell.notifylib.core.engine;

import net.kyori.adventure.key.Key;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.Corner;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Личные настройки игрока. Неизменяемые, методы {@code with*} возвращают копию.
 *
 * @param corner выбранный угол или {@code null}, если игрок не выбирал
 * @param hidden ключи скрытых типов
 */
public record ToastPreferences(@Nullable Corner corner, boolean enabled, boolean sound, Set<Key> hidden) {

    public static final ToastPreferences DEFAULT = new ToastPreferences(null, true, true, Set.of());

    public ToastPreferences {
        hidden = Set.copyOf(Objects.requireNonNull(hidden, "hidden"));
    }

    public Corner cornerOr(Corner fallback) {
        return corner != null ? corner : fallback;
    }

    public ToastPreferences withCorner(@Nullable Corner corner) {
        return new ToastPreferences(corner, enabled, sound, hidden);
    }

    public ToastPreferences withEnabled(boolean enabled) {
        return new ToastPreferences(corner, enabled, sound, hidden);
    }

    public ToastPreferences withSound(boolean sound) {
        return new ToastPreferences(corner, enabled, sound, hidden);
    }

    public ToastPreferences withHidden(Key type, boolean hide) {
        Set<Key> next = new HashSet<>(hidden);
        if (hide) {
            next.add(type);
        } else {
            next.remove(type);
        }
        return new ToastPreferences(corner, enabled, sound, next);
    }
}
