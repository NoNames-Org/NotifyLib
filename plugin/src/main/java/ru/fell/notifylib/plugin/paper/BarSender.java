package ru.fell.notifylib.plugin.paper;

import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBossBar;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import ru.fell.notifylib.core.render.ToastFrame;
import ru.fell.notifylib.core.render.ToastRenderer;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Рендерит имена боссбаров и шлёт пакеты в одном фоновом потоке, главный поток только передаёт готовые кадры.
 * Задачи идут строго по очереди: добавление, обновления и удаление бара не перемешиваются, а
 * {@link ToastRenderer} может держать кэши без блокировок.
 */
public final class BarSender {

    private static final float FULL = 1F;

    private final ExecutorService thread;
    private final ToastRenderer renderer;

    // thread однопоточный, им владеет вызывающий; renderer используется только из него.
    public BarSender(ExecutorService thread, ToastRenderer renderer) {
        this.thread = thread;
        this.renderer = renderer;
    }

    /** Показывает бар или переименовывает, если он у игрока уже есть. {@code shown} трогает только поток баров. */
    void show(User user, Set<UUID> shown, UUID bar, ToastFrame frame) {
        submit(() -> {
            Component name = renderer.render(frame);
            user.sendPacket(shown.add(bar) ? addPacket(bar, name) : renamePacket(bar, name));
        });
    }

    void hide(User user, Set<UUID> shown, UUID bar) {
        submit(() -> {
            if (shown.remove(bar)) {
                user.sendPacket(new WrapperPlayServerBossBar(bar, WrapperPlayServerBossBar.Action.REMOVE));
            }
        });
    }

    private static WrapperPlayServerBossBar addPacket(UUID bar, Component name) {
        WrapperPlayServerBossBar add = new WrapperPlayServerBossBar(bar, WrapperPlayServerBossBar.Action.ADD);
        add.setTitle(name);
        add.setHealth(FULL);
        add.setColor(BossBar.Color.WHITE);
        add.setOverlay(BossBar.Overlay.PROGRESS);
        add.setFlags(EnumSet.noneOf(BossBar.Flag.class));
        return add;
    }

    private static WrapperPlayServerBossBar renamePacket(UUID bar, Component name) {
        WrapperPlayServerBossBar rename = new WrapperPlayServerBossBar(bar,
                WrapperPlayServerBossBar.Action.UPDATE_TITLE);
        rename.setTitle(name);
        return rename;
    }

    // Вызывается и выключается только из главного потока, так что гонки с shutdown нет.
    // После выключения клиент всё равно сбросит боссбары при выходе. Ошибки задач логирует сам поток.
    private void submit(Runnable task) {
        if (!thread.isShutdown()) {
            thread.execute(task);
        }
    }
}
