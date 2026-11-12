package ru.fell.notifylib.plugin.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.CloseReason;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.api.ToastHandle;
import ru.fell.notifylib.api.event.ToastCloseEvent;
import ru.fell.notifylib.api.event.ToastShowEvent;
import ru.fell.notifylib.core.engine.EngineHooks;
import ru.fell.notifylib.core.service.OnlinePlayers;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** События Bukkit и логи для движка. Исключения слушателей Bukkit логирует сам. */
public final class PaperEngineHooks implements EngineHooks {

    private final OnlinePlayers online;
    private final Logger logger;

    public PaperEngineHooks(OnlinePlayers online, Logger logger) {
        this.online = online;
        this.logger = logger;
    }

    @Override
    public @Nullable Toast beforeShow(UUID playerId, ToastHandle handle, Toast toast) {
        Player player = online.get(playerId);
        if (player == null) {
            return null;
        }
        if (ToastShowEvent.getHandlerList().getRegisteredListeners().length == 0) {
            return toast;
        }
        ToastShowEvent event = new ToastShowEvent(player, handle, toast);
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled() ? null : event.getToast();
    }

    @Override
    public void afterClose(UUID playerId, ToastHandle handle, Toast toast, CloseReason reason) {
        if (ToastCloseEvent.getHandlerList().getRegisteredListeners().length == 0) {
            return;
        }
        Player player = online.get(playerId);
        if (player == null) {
            // Игрок вышел и уже удалён из OnlinePlayers, а PlayerEvent без объекта игрока не создать.
            player = Bukkit.getPlayer(playerId);
        }
        if (player != null) {
            Bukkit.getPluginManager().callEvent(new ToastCloseEvent(player, handle, toast, reason));
        }
    }

    @Override
    public void callbackFailed(@Nullable String owner, Throwable error) {
        logger.log(Level.WARNING, "Слушатель закрытия уведомления из плагина " + (owner == null ? "<неизвестно>" : owner)
                + " выбросил исключение", error);
    }

    @Override
    public void engineFailed(String message, Throwable error) {
        logger.log(Level.SEVERE, "Ошибка движка уведомлений: " + message, error);
    }

    @Override
    public void trace(String message) {
        logger.info("[debug] " + message);
    }
}
