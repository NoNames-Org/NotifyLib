package ru.fell.notifylib.plugin;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Фоновые однопоточные исполнители плагина. */
public final class Threads {

    private Threads() {
    }

    /** Демон-поток; упавшая задача пишется в лог, следующие задачи выполняются дальше. */
    public static ExecutorService single(String name, Logger logger) {
        return Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, error) ->
                    logger.log(Level.SEVERE, "Ошибка в потоке " + name, error));
            return thread;
        });
    }

    /** Даёт доделать уже поставленные задачи. Не успели — пишет {@code timeoutMessage} и прерывает поток. */
    public static void stop(ExecutorService executor, Duration wait, Logger logger, String timeoutMessage) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(wait.toMillis(), TimeUnit.MILLISECONDS)) {
                logger.warning(timeoutMessage);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
