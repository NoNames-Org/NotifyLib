package ru.fell.notifylib.core.scheduler;

/** Обёртка над планировщиком платформы, чтобы под Folia можно было подложить свою реализацию, не трогая ядро. */
public interface TaskScheduler {

    /** Каждый тик в главном потоке. */
    Cancellable everyTick(Runnable task);

    /** В главном потоке через {@code ticks} тиков. */
    Cancellable later(Runnable task, long ticks);

    /** В главном потоке как можно скорее; если уже в нём, то сразу. */
    void main(Runnable task);

    /** Вне главного потока. */
    void async(Runnable task);

    @FunctionalInterface
    interface Cancellable {
        /** Повторная отмена или отмена уже выполненной задачи ничего не делает. */
        void cancel();
    }
}
