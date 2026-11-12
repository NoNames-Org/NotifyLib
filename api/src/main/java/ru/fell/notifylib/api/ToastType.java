package ru.fell.notifylib.api;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.key.Keyed;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Тип тоста: цвет полоски таймера и звук по умолчанию. Иконка и текстура полоски лежат в ресурспаке,
 * свои типы объявляются в {@code config.yml} NotifyLib.
 *
 * @param key      уникальный ключ, например {@code mistik:donate}
 * @param barColor цвет полоски таймера (используется и резервным каналом)
 * @param sound    звук по умолчанию или {@code null}, если без звука
 */
public record ToastType(Key key, TextColor barColor, @Nullable Sound sound) implements Keyed {

    public ToastType {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(barColor, "barColor");
    }
}
