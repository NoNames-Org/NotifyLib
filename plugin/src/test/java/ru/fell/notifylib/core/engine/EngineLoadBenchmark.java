package ru.fell.notifylib.core.engine;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.api.ToastTypes;
import ru.fell.notifylib.core.content.DefaultContentFactory;
import ru.fell.notifylib.core.protocol.Protocol;
import ru.fell.notifylib.core.render.ToastFrame;
import ru.fell.notifylib.core.render.ToastRenderer;
import ru.fell.notifylib.core.text.GlyphWidths;
import ru.fell.notifylib.core.text.TextFitter;
import ru.fell.notifylib.core.text.TextResolver;
import ru.fell.notifylib.core.type.ToastTypeRegistryImpl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Нагрузка движка на главный поток при большом онлайне. Это не юнит-тест, запуск вручную:
 * {@code mvn -o test -Dbench=true -Dtest=EngineLoadBenchmark -Dsurefire.failIfNoSpecifiedTests=false}.
 * Рендер боссбаров меряется отдельно, он идёт в потоке баров, а не в главном.
 */
@EnabledIfSystemProperty(named = "bench", matches = "true")
class EngineLoadBenchmark {

    private static final int PLAYERS = 250;
    private static final int WARMUP_TICKS = 6_000;
    private static final int MEASURED_TICKS = 6_000;

    /** Игрок, у которого кадры боссбаров просто копятся, как их получил бы поток баров. */
    private static final class Viewer implements ToastViewer {
        private final UUID id = UUID.randomUUID();
        final List<ToastFrame> frames;

        Viewer(List<ToastFrame> frames) {
            this.frames = frames;
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public long gameTime() {
            return 0;
        }

        @Override
        public void showBar(UUID bar, ToastFrame frame) {
            frames.add(frame);
        }

        @Override
        public void hideBar(UUID bar) {
        }

        @Override
        public void playSound(Sound sound) {
        }

        @Override
        public void sendActionBar(Component message) {
        }

        @Override
        public void sendMessage(Component message) {
        }
    }

    private interface Load {
        void apply(ToastEngine engine, List<Viewer> viewers, int tick, Random random);
    }

    @Test
    void mainThreadCostWith250Players() {
        // В среднем каждый игрок получает уведомление раз в 10 с (убийства, награды, квесты): ~25 в секунду всего.
        report("steady: every player ~1 toast / 10 s", (engine, viewers, tick, random) -> {
            for (Viewer viewer : viewers) {
                if (random.nextInt(200) == 0) {
                    engine.show(viewer.id(), toast("Вас убил <yellow>qwened</yellow> с 15 ХП"), null);
                }
            }
        });
        // Тяжёлый случай: каждому игроку раз в 2 с.
        report("heavy: every player ~1 toast / 2 s", (engine, viewers, tick, random) -> {
            for (Viewer viewer : viewers) {
                if (random.nextInt(40) == 0) {
                    engine.show(viewer.id(), toast("Убийство x" + random.nextInt(9)), null);
                }
            }
        });
        // Рассылка всем 250 сразу раз в 5 с (старт ивента, объявления).
        report("broadcast: all 250 at once every 5 s", (engine, viewers, tick, random) -> {
            if (tick % 100 == 0) {
                Toast toast = toast("<gold>Ивент начался!</gold> Телепорт через 10 секунд");
                for (Viewer viewer : viewers) {
                    engine.show(viewer.id(), toast, null);
                }
            }
        });
    }

    private static Toast toast(String text) {
        return Toast.builder().type(ToastTypes.INFO).text(text).duration(Duration.ofSeconds(4)).build();
    }

    private static void report(String name, Load load) {
        List<ToastFrame> frames = new ArrayList<>();
        ToastTypeRegistryImpl types = new ToastTypeRegistryImpl();
        ToastEngine engine = new ToastEngine(EngineSettings.DEFAULTS,
                new DefaultContentFactory(new TextResolver(MiniMessage.miniMessage()),
                        new TextFitter(GlyphWidths.load()), types, () -> EngineSettings.DEFAULTS),
                EngineHooks.noop(), PreferencesSource.DEFAULTS);
        List<Viewer> viewers = new ArrayList<>();
        for (int i = 0; i < PLAYERS; i++) {
            Viewer viewer = new Viewer(frames);
            viewers.add(viewer);
            engine.joinNow(viewer, DeliveryState.READY);
        }
        Random random = new Random(42);
        for (int tick = 0; tick < WARMUP_TICKS; tick++) {
            load.apply(engine, viewers, tick, random);
            engine.tick();
        }
        frames.clear();
        long[] nanos = new long[MEASURED_TICKS];
        for (int tick = 0; tick < MEASURED_TICKS; tick++) {
            long start = System.nanoTime();
            load.apply(engine, viewers, tick, random);
            engine.tick();
            nanos[tick] = System.nanoTime() - start;
        }
        Arrays.sort(nanos);
        double mean = Arrays.stream(nanos).average().orElse(0) / 1_000_000.0;
        double p99 = nanos[(int) (MEASURED_TICKS * 0.99)] / 1_000_000.0;
        double max = nanos[MEASURED_TICKS - 1] / 1_000_000.0;
        double barMsPerSecond = barThreadMillis(frames) / (MEASURED_TICKS / 20.0);
        System.out.printf("%-40s main thread per tick: mean %.3f ms, p99 %.3f ms, max %.3f ms (budget 50 ms)%n"
                        + "%-40s bar thread: %d frames in %d s, %.1f ms of work per second (budget 1000 ms)%n",
                name, mean, p99, max, "", frames.size(), MEASURED_TICKS / 20, barMsPerSecond);
    }

    /** Сколько потоку баров нужно на рендер этих кадров (сами пакеты пишет Netty). */
    private static double barThreadMillis(List<ToastFrame> frames) {
        // Прогреваем JIT на одном рендерере, меряем на новом: кэши пустые, как на сервере.
        ToastRenderer warm = new ToastRenderer(Protocol.PANEL_ADVANCE);
        for (ToastFrame frame : frames) {
            warm.render(frame);
        }
        ToastRenderer renderer = new ToastRenderer(Protocol.PANEL_ADVANCE);
        long start = System.nanoTime();
        for (ToastFrame frame : frames) {
            renderer.render(frame);
        }
        return (System.nanoTime() - start) / 1_000_000.0;
    }
}
