package ru.fell.notifylib.core.engine;

enum ToastState {
    /** Создан вызовом API, главный поток его ещё не обработал. */
    SUBMITTED,
    QUEUED,
    /** Слот занят, ждём, пока соседи доедут, и только потом выезжаем. */
    RESERVED,
    ENTERING,
    SHOWN,
    /** Сдвигается на шаг по стопке. */
    SHIFTING,
    LEAVING,
    CLOSED
}
