package ru.fell.notifylib.core.engine;

/** Куда встаёт новый тост в стопке. */
public enum StackOrder {
    /** В конец: старые тосты остаются на местах. */
    END,
    /** В угол: старые сдвигаются на шаг дальше. */
    CORNER
}
