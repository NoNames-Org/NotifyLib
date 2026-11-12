package ru.fell.notifylib.core.engine;

import net.kyori.adventure.sound.Sound;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.core.text.FittedText;
import ru.fell.notifylib.core.type.RegisteredType;

import java.util.Objects;

/**
 * Тост, собранный под одного получателя: тексты разобраны и подогнаны по ширине, дефолты применены.
 *
 * @param title         заголовок двухстрочного тоста или {@code null}
 * @param durationTicks длина таймера, 0 у постоянного тоста
 */
public record ToastContent(RegisteredType type, FittedText text, @Nullable FittedText title, int durationTicks,
                           @Nullable Sound sound) {

    public ToastContent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
        if (durationTicks < 0) {
            throw new IllegalArgumentException("длительность не может быть отрицательной");
        }
    }

    public boolean tall() {
        return title != null;
    }

    public boolean persistent() {
        return durationTicks == 0;
    }
}
