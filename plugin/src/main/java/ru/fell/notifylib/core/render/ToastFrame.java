package ru.fell.notifylib.core.render;

import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.Corner;
import ru.fell.notifylib.core.protocol.ShaderPhase;
import ru.fell.notifylib.core.protocol.StackPosition;
import ru.fell.notifylib.core.text.FittedText;
import ru.fell.notifylib.core.type.RegisteredType;

import java.util.Objects;

/**
 * Всё, что кодирует одно имя боссбара.
 *
 * @param phaseTick игровое время (mod 16) начала фазы
 * @param position  позиция в стопке (для SHIFT это цель)
 * @param shiftFrom только для SHIFT: предыдущая позиция
 * @param title     заголовок двухстрочного тоста или {@code null}
 * @param timer     {@code null} у бессрочного тоста
 */
public record ToastFrame(Corner corner, ShaderPhase phase, int phaseTick, StackPosition position,
                         @Nullable StackPosition shiftFrom, RegisteredType type, FittedText text,
                         @Nullable FittedText title, @Nullable Timer timer) {

    public ToastFrame {
        Objects.requireNonNull(corner, "corner");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(text, "text");
        if ((phase == ShaderPhase.SHIFT) != (shiftFrom != null)) {
            throw new IllegalArgumentException("shiftFrom задаётся только для SHIFT и обязателен для него");
        }
    }

    /** @param startGameTime время мира на старте таймера */
    public record Timer(long startGameTime, int durationTicks) {
    }
}
