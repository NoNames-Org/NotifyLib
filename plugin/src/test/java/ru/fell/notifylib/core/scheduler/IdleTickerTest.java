package ru.fell.notifylib.core.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdleTickerTest {

    /** Ручной главный поток: {@link #tick()} один раз прогоняет все живые повторяющиеся задачи. */
    private static final class FakeScheduler implements TaskScheduler {
        final List<Runnable> repeating = new ArrayList<>();

        @Override
        public Cancellable everyTick(Runnable task) {
            repeating.add(task);
            return () -> repeating.remove(task);
        }

        @Override
        public Cancellable later(Runnable task, long ticks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void main(Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            throw new UnsupportedOperationException();
        }

        void tick() {
            for (Runnable task : List.copyOf(repeating)) {
                task.run();
            }
        }
    }

    private final FakeScheduler scheduler = new FakeScheduler();
    private final AtomicInteger work = new AtomicInteger();
    private final AtomicInteger ticks = new AtomicInteger();
    private final IdleTicker ticker = new IdleTicker(scheduler, () -> {
        ticks.incrementAndGet();
        if (work.get() > 0) {
            work.decrementAndGet();
        }
    }, () -> work.get() == 0);

    @Test
    void ticksWhileThereIsWorkThenStopsCompletely() {
        work.set(3);
        ticker.wake();

        for (int i = 0; i < 10; i++) {
            scheduler.tick();
        }

        assertEquals(3, ticks.get(), "no ticks after the work is done");
        assertTrue(scheduler.repeating.isEmpty(), "the repeating task is cancelled when idle");
    }

    @Test
    void wakeAfterIdleStartsTickingAgain() {
        ticker.wake();
        scheduler.tick();
        assertTrue(scheduler.repeating.isEmpty());

        work.set(2);
        ticker.wake();
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();

        assertEquals(3, ticks.get());
        assertTrue(scheduler.repeating.isEmpty());
    }

    @Test
    void repeatedWakesKeepASingleTask() {
        work.set(5);
        ticker.wake();
        ticker.wake();
        ticker.wake();

        assertEquals(1, scheduler.repeating.size());
    }

    @Test
    void workArrivingWhileGoingIdleIsNotLost() {
        // Проверка сказала "простой", но работа появилась до сброса флага: её wake видит, что тикер ещё
        // крутится, и ничего не делает. Значит, тикер должен заметить работу сам.
        AtomicInteger checks = new AtomicInteger();
        IdleTicker racy = new IdleTicker(scheduler, ticks::incrementAndGet, () -> {
            if (checks.incrementAndGet() == 1) {
                work.set(1);
                return true;
            }
            return work.get() == 0;
        });
        racy.wake();

        scheduler.tick();

        assertEquals(1, scheduler.repeating.size(), "restarted for the work that arrived during the race");
    }

    @Test
    void closeStopsAndIgnoresLaterWakes() {
        work.set(5);
        ticker.wake();
        ticker.close();
        ticker.wake();

        assertTrue(scheduler.repeating.isEmpty());
    }
}
