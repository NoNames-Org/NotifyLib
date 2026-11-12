package ru.fell.notifylib.api;

/** Что делать с новым тостом, если стек игрока заполнен. */
public enum Overflow {
    /** Ждать в очереди, пока не освободится место. Длина очереди задаётся в конфиге. */
    QUEUE,
    /** Самый старый видимый тост уезжает ({@link CloseReason#REPLACED}), новый встаёт на его место. */
    REPLACE_OLDEST,
    /** Новый тост отбрасывается ({@link CloseReason#OVERFLOW}). */
    DROP
}
