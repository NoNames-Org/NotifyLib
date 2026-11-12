package ru.fell.notifylib.core.protocol;

/**
 * Место тоста в стопке.
 *
 * @param slot       номер от угла, {@code 0..3}
 * @param tallBefore сколько двухстрочных тостов между ним и углом, {@code 0..3}
 */
public record StackPosition(int slot, int tallBefore) {

    private static final StackPosition[][] CACHE = new StackPosition[Protocol.MAX_SLOTS][Protocol.MAX_SLOTS];

    static {
        for (int slot = 0; slot < Protocol.MAX_SLOTS; slot++) {
            for (int tall = 0; tall <= slot; tall++) {
                CACHE[slot][tall] = new StackPosition(slot, tall);
            }
        }
    }

    public static final StackPosition CORNER = CACHE[0][0];

    public StackPosition {
        validate(slot, tallBefore);
    }

    /** Общий экземпляр из кэша. */
    public static StackPosition of(int slot, int tallBefore) {
        validate(slot, tallBefore);
        return CACHE[slot][tallBefore];
    }

    private static void validate(int slot, int tallBefore) {
        if (slot < 0 || slot >= Protocol.MAX_SLOTS || tallBefore < 0 || tallBefore > slot) {
            throw new IllegalArgumentException("некорректная позиция в стопке " + slot + "/" + tallBefore);
        }
    }

    /**
     * Следующая позиция на пути к {@code target}, которую шейдер умеет анимировать за один сдвиг:
     * один слот и не больше одного двухстрочного тоста в ту же сторону.
     *
     * @return следующий шаг, либо сам {@code target}, если анимировать нельзя (тост перепрыгнет)
     */
    public StackPosition stepTowards(StackPosition target) {
        int slotDelta = Integer.signum(target.slot - slot);
        int tallDelta = Integer.signum(target.tallBefore - tallBefore);
        if (slotDelta == 0 || (tallDelta != 0 && tallDelta != slotDelta)) {
            return target;
        }
        return of(slot + slotDelta, tallBefore + tallDelta);
    }

    /** Можно ли перейти в {@code next} одной фазой SHIFT. */
    public boolean isOneStepTo(StackPosition next) {
        int slotDelta = next.slot - slot;
        int tallDelta = next.tallBefore - tallBefore;
        return Math.abs(slotDelta) == 1 && (tallDelta == 0 || tallDelta == slotDelta);
    }
}
