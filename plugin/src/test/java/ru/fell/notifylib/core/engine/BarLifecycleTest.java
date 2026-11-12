package ru.fell.notifylib.core.engine;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;
import ru.fell.notifylib.api.CloseReason;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.api.ToastTypes;
import ru.fell.notifylib.core.content.DefaultContentFactory;
import ru.fell.notifylib.core.protocol.ShaderPhase;
import ru.fell.notifylib.core.render.ToastFrame;
import ru.fell.notifylib.core.text.GlyphWidths;
import ru.fell.notifylib.core.text.TextFitter;
import ru.fell.notifylib.core.text.TextResolver;
import ru.fell.notifylib.core.type.ToastTypeRegistryImpl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Движок отдаёт зрителю боссбары как id и кадры: один id от IN до удаления. */
class BarLifecycleTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final int SAFETY_TICKS = 400;

    // Запоминает вызовы баров по порядку.
    private static final class RecordingViewer implements ToastViewer {
        final List<String> calls = new ArrayList<>();
        final List<ToastFrame> frames = new ArrayList<>();
        UUID lastBar;

        @Override
        public UUID id() {
            return PLAYER;
        }

        @Override
        public long gameTime() {
            return 1000;
        }

        @Override
        public void showBar(UUID bar, ToastFrame frame) {
            calls.add("show " + bar);
            frames.add(frame);
            lastBar = bar;
        }

        @Override
        public void hideBar(UUID bar) {
            calls.add("hide " + bar);
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

    private static ToastEngine engine() {
        ToastTypeRegistryImpl types = new ToastTypeRegistryImpl();
        return new ToastEngine(EngineSettings.DEFAULTS,
                new DefaultContentFactory(new TextResolver(MiniMessage.miniMessage()),
                        new TextFitter(GlyphWidths.load()), types, () -> EngineSettings.DEFAULTS),
                EngineHooks.noop(), PreferencesSource.DEFAULTS);
    }

    @Test
    void toastUsesOneBarFromEnterToRemovalAndHidesItOnce() {
        ToastEngine engine = engine();
        RecordingViewer viewer = new RecordingViewer();
        engine.joinNow(viewer, DeliveryState.READY);
        AtomicReference<CloseReason> closed = new AtomicReference<>();

        engine.show(PLAYER, Toast.builder().type(ToastTypes.INFO).text(Component.text("hello"))
                .duration(Duration.ofSeconds(1)).build(), null).onClose(closed::set);
        for (int i = 0; i < SAFETY_TICKS && closed.get() == null; i++) {
            engine.tick();
        }

        assertEquals(CloseReason.EXPIRED, closed.get());
        UUID bar = viewer.lastBar;
        assertTrue(viewer.calls.stream().allMatch(call -> call.endsWith(bar.toString())), viewer.calls.toString());
        assertEquals("hide " + bar, viewer.calls.get(viewer.calls.size() - 1));
        assertEquals(1, viewer.calls.stream().filter(call -> call.startsWith("hide")).count());
        assertEquals(ShaderPhase.IN, viewer.frames.get(0).phase());
        assertEquals(ShaderPhase.OUT, viewer.frames.get(viewer.frames.size() - 1).phase());
        assertTrue(engine.idle(), "nothing left to tick after the toast is gone");
    }

    @Test
    void twoToastsGetDifferentBars() {
        ToastEngine engine = engine();
        RecordingViewer viewer = new RecordingViewer();
        engine.joinNow(viewer, DeliveryState.READY);

        engine.show(PLAYER, Toast.builder().type(ToastTypes.INFO).text(Component.text("a")).build(), null);
        engine.show(PLAYER, Toast.builder().type(ToastTypes.ERROR).text(Component.text("b")).build(), null);
        engine.tick();
        engine.tick();

        long distinct = viewer.calls.stream().filter(call -> call.startsWith("show")).distinct().count();
        assertEquals(2, distinct);
        assertFalse(engine.idle());
    }

    @Test
    void quitHidesTheVisibleBar() {
        ToastEngine engine = engine();
        RecordingViewer viewer = new RecordingViewer();
        engine.joinNow(viewer, DeliveryState.READY);
        engine.show(PLAYER, Toast.builder().type(ToastTypes.INFO).text(Component.text("x")).build(), null);
        engine.tick();

        engine.quitNow(PLAYER);

        assertEquals("hide " + viewer.lastBar, viewer.calls.get(viewer.calls.size() - 1));
    }
}
