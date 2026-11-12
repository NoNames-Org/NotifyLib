package ru.fell.notifylib.core.protocol;

import ru.fell.notifylib.api.Corner;

import java.util.Objects;

/**
 * 14-битный код, который тост несёт в своей полосе по x.
 *
 * @param tick      игровой тик начала движения (mod 16)
 * @param position  целевая позиция в стопке
 * @param shiftAway только для SHIFT: тост едет от угла
 * @param shiftTall только для SHIFT: сдвиг проходит через двухстрочный тост
 */
public record ToastCode(Corner corner, ShaderPhase phase, int tick, StackPosition position,
                        boolean shiftAway, boolean shiftTall) {

    public ToastCode {
        Objects.requireNonNull(corner, "corner");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(position, "position");
        if (tick < 0 || tick >= Protocol.TICK_MOD) {
            throw new IllegalArgumentException("тик вне диапазона: " + tick);
        }
    }

    /** Код для любой фазы, кроме SHIFT. */
    public static ToastCode of(Corner corner, ShaderPhase phase, int tick, StackPosition position) {
        return new ToastCode(corner, phase, tick, position, false, false);
    }

    /** Код SHIFT из {@code from} в {@code to}; позиции должны отличаться на один шаг. */
    public static ToastCode shift(Corner corner, int tick, StackPosition from, StackPosition to) {
        if (!from.isOneStepTo(to)) {
            throw new IllegalArgumentException("это не сдвиг на одну позицию: " + from + " -> " + to);
        }
        return new ToastCode(corner, ShaderPhase.SHIFT, tick, to, to.slot() > from.slot(),
                to.tallBefore() != from.tallBefore());
    }

    /** Упаковывает поля, результат в {@code [0, CODES)}. */
    public int encode() {
        return corner.ordinal() << Protocol.SHIFT_CORNER
                | phase.ordinal() << Protocol.SHIFT_PHASE
                | tick << Protocol.SHIFT_TICK
                | position.slot() << Protocol.SHIFT_SLOT
                | position.tallBefore() << Protocol.SHIFT_TALL_BEFORE
                | (shiftAway ? 1 : 0) << Protocol.SHIFT_SHIFT_AWAY
                | (shiftTall ? 1 : 0) << Protocol.SHIFT_SHIFT_TALL;
    }

    /** Смещение элементов тоста по x от начала строки, px. */
    public int bandOffset() {
        return (Protocol.BAND_BASE + encode()) * Protocol.X_STEP;
    }

    public static ToastCode decode(int code) {
        if (code < 0 || code >= Protocol.CODES) {
            throw new IllegalArgumentException("код вне диапазона: " + code);
        }
        Corner corner = Corner.values()[code >> Protocol.SHIFT_CORNER & 3];
        ShaderPhase phase = ShaderPhase.values()[code >> Protocol.SHIFT_PHASE & 3];
        int tick = code >> Protocol.SHIFT_TICK & 15;
        StackPosition position = new StackPosition(code >> Protocol.SHIFT_SLOT & 3, code >> Protocol.SHIFT_TALL_BEFORE & 3);
        return new ToastCode(corner, phase, tick, position, (code >> Protocol.SHIFT_SHIFT_AWAY & 1) == 1,
                (code >> Protocol.SHIFT_SHIFT_TALL & 1) == 1);
    }
}
