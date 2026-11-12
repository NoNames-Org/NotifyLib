package ru.fell.notifylib.plugin.paper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.core.engine.ToastViewer;
import ru.fell.notifylib.core.render.ToastFrame;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Зритель на основе игрока Paper. Боссбары это чисто клиентские пакеты, их рендерит и шлёт {@link BarSender}
 * вне главного потока; звуки и сообщения идут через Paper. Живёт, пока игрок онлайн.
 */
public final class PaperViewer implements ToastViewer {

    private final Player player;
    private final UUID id;
    private final BarSender bars;
    /** Бары, которые есть у игрока. Только поток баров. */
    private final Set<UUID> shown = new HashSet<>();
    /** Только главный поток. Берётся лениво: вход раньше, чем PacketEvents узнал игрока, ничего не ломает. */
    private @Nullable User user;

    public PaperViewer(Player player, BarSender bars) {
        this.player = player;
        this.id = player.getUniqueId();
        this.bars = bars;
    }

    private @Nullable User user() {
        if (user == null) {
            user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        }
        return user;
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public long gameTime() {
        return player.getWorld().getGameTime();
    }

    @Override
    public void showBar(UUID bar, ToastFrame frame) {
        User connection = user();
        if (connection != null) {
            bars.show(connection, shown, bar, frame);
        }
    }

    @Override
    public void hideBar(UUID bar) {
        User connection = user();
        if (connection != null) {
            bars.hide(connection, shown, bar);
        }
    }

    @Override
    public void playSound(Sound sound) {
        player.playSound(sound, Sound.Emitter.self());
    }

    @Override
    public void sendActionBar(Component message) {
        player.sendActionBar(message);
    }

    @Override
    public void sendMessage(Component message) {
        player.sendMessage(message);
    }
}
