package ru.fell.notifylib.api;

/** Угол экрана, в котором складываются тосты игрока. */
public enum Corner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    /** {@code true}, если тосты выезжают с левого края. */
    public boolean left() {
        return this == TOP_LEFT || this == BOTTOM_LEFT;
    }

    /** {@code true}, если стек растёт вниз от верхнего края. */
    public boolean top() {
        return this == TOP_LEFT || this == TOP_RIGHT;
    }
}
