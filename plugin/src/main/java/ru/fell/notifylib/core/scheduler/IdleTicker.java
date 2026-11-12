package ru.fell.notifylib.core.scheduler;

import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Гоняет задачу каждый тик, только пока есть работа. Если после тика делать нечего, повторяющаяся задача
 * снимается, а {@link #wake()} запускает её снова. Без тостов плагин вообще не нагружает главный поток.
 *
 * <p>{@link #wake()} можно звать из любого потока. Задача всегда стартует в главном потоке, поэтому не
 * запустится раньше, чем сохранится её хэндл. Пробуждение, совпавшее с уходом в простой, не теряется:
 * сняв флаг, тик ещё раз проверяет, нет ли работы.
 */
public final class IdleTicker {

    private final TaskScheduler scheduler;
    private final Runnable tick;
    private final BooleanSupplier idle;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean closed;
    // Только главный поток.
    private TaskScheduler.@Nullable Cancellable task;

    /** @param idle true, если работы не осталось; проверяется в главном потоке после каждого тика */
    public IdleTicker(TaskScheduler scheduler, Runnable tick, BooleanSupplier idle) {
        this.scheduler = scheduler;
        this.tick = tick;
        this.idle = idle;
    }

    /** Появилась работа: запустить задачу, если она стоит. Любой поток. */
    public void wake() {
        if (!closed && running.compareAndSet(false, true)) {
            scheduler.main(this::start);
        }
    }

    /** Окончательная остановка при выключении плагина. Главный поток. */
    public void close() {
        closed = true;
        stop();
    }

    private void start() {
        if (closed) {
            running.set(false);
            return;
        }
        task = scheduler.everyTick(this::run);
    }

    private void run() {
        tick.run();
        if (!idle.getAsBoolean()) {
            return;
        }
        stop();
        running.set(false);
        // Работа, пришедшая между проверкой и сбросом флага, видела его поднятым и нас не разбудила.
        if (!idle.getAsBoolean()) {
            wake();
        }
    }

    private void stop() {
        TaskScheduler.Cancellable current = task;
        task = null;
        if (current != null) {
            current.cancel();
        }
    }
}
