package ru.fell.notifylib.core.protocol;

import net.kyori.adventure.text.format.TextColor;

/** Данные таймера в RGB глифа полоски: {@code (startTick mod DAY) << 9 | duration / 2}. */
public final class TimerColor {

    private TimerColor() {
    }

    public static TextColor encode(long startGameTime, int durationTicks) {
        return TextColor.color(value(startGameTime, durationTicks));
    }

    /**
     * То же, что {@link #encode}, но без создания объекта цвета.
     *
     * @param startGameTime время мира на старте таймера
     * @param durationTicks длина, {@code 1..MAX_DURATION_TICKS}
     * @return 24-битное RGB
     */
    public static int value(long startGameTime, int durationTicks) {
        if (durationTicks < 1 || durationTicks > Protocol.MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("длительность таймера вне диапазона: " + durationTicks);
        }
        int start = (int) Math.floorMod(startGameTime, (long) Protocol.DAY);
        int units = Math.max(1, durationTicks / Protocol.DURATION_UNIT);
        return start << Protocol.DURATION_BITS | units;
    }
}
