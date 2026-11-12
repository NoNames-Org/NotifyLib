package ru.fell.notifylib.api;

import net.kyori.adventure.key.Key;

import java.util.Set;
import java.util.UUID;

/**
 * Личные настройки тостов игрока, хранятся в его persistent data container. Чтение отдаёт кэшированное
 * значение; запись потокобезопасна, сохранение идёт в главном потоке.
 */
public interface PlayerToastSettings {

    UUID playerId();

    /** Угол, в котором появляются тосты. */
    Corner corner();

    void corner(Corner corner);

    /** Сбрасывает выбранный угол: игрок снова получает угол по умолчанию с сервера. */
    void resetCorner();

    /** Показываются ли игроку тосты вообще. */
    boolean enabled();

    void enabled(boolean enabled);

    /** Играют ли звуки тостов. */
    boolean sound();

    void sound(boolean sound);

    /**
     * Типы, которые игрок скрыл.
     *
     * @return неизменяемый снимок ключей
     */
    Set<Key> hiddenTypes();

    default boolean isHidden(Key type) {
        return hiddenTypes().contains(type);
    }

    /** Скрывает ({@code hidden = true}) или снова показывает тосты этого типа. */
    void hidden(Key type, boolean hidden);
}
