package ru.fell.notifylib.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.api.ToastHandle;

import java.util.Objects;

/**
 * Вызывается в главном потоке перед показом тоста или постановкой его в очередь. Отмена закрывает тост с
 * {@link ru.fell.notifylib.api.CloseReason#CANCELLED}; через {@link #setToast(Toast)} можно подменить
 * то, что будет показано.
 */
public final class ToastShowEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ToastHandle handle;
    private Toast toast;
    private boolean cancelled;

    /** Создаётся только самим NotifyLib. */
    public ToastShowEvent(Player player, ToastHandle handle, Toast toast) {
        super(player);
        this.handle = Objects.requireNonNull(handle, "handle");
        this.toast = Objects.requireNonNull(toast, "toast");
    }

    public ToastHandle getHandle() {
        return handle;
    }

    /** Тост, который будет показан. */
    public Toast getToast() {
        return toast;
    }

    /**
     * Подменяет тост, который будет показан.
     *
     * @param toast если его тип не зарегистрирован, тост закроется с
     *              {@link ru.fell.notifylib.api.CloseReason#CANCELLED}
     */
    public void setToast(Toast toast) {
        this.toast = Objects.requireNonNull(toast, "toast");
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
