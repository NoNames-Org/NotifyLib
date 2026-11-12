package ru.fell.notifylib.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import ru.fell.notifylib.api.CloseReason;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.api.ToastHandle;

import java.util.Objects;

/** Вызывается в главном потоке после закрытия тоста. */
public final class ToastCloseEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ToastHandle handle;
    private final Toast toast;
    private final CloseReason reason;

    /** Создаётся только самим NotifyLib. */
    public ToastCloseEvent(Player player, ToastHandle handle, Toast toast, CloseReason reason) {
        super(player);
        this.handle = Objects.requireNonNull(handle, "handle");
        this.toast = Objects.requireNonNull(toast, "toast");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public ToastHandle getHandle() {
        return handle;
    }

    /** Тост в том виде, в котором он был показан. */
    public Toast getToast() {
        return toast;
    }

    public CloseReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
