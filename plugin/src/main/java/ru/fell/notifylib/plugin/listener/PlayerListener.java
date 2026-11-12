package ru.fell.notifylib.plugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import ru.fell.notifylib.core.engine.ToastEngine;
import ru.fell.notifylib.core.service.OnlinePlayers;
import ru.fell.notifylib.core.settings.PreferencesStore;
import ru.fell.notifylib.plugin.paper.BarSender;
import ru.fell.notifylib.plugin.paper.PackStatusTracker;
import ru.fell.notifylib.plugin.paper.PaperViewer;

import java.util.UUID;

/** Держит движок в курсе игроков: вход, выход, смена мира, статус пака. */
public final class PlayerListener implements Listener {

    private final ToastEngine engine;
    private final OnlinePlayers online;
    private final PreferencesStore preferences;
    private final PackStatusTracker packStatus;
    private final BarSender bars;

    public PlayerListener(ToastEngine engine, OnlinePlayers online, PreferencesStore preferences,
                          PackStatusTracker packStatus, BarSender bars) {
        this.engine = engine;
        this.online = online;
        this.preferences = preferences;
        this.packStatus = packStatus;
        this.bars = bars;
    }

    /** Вход игрока, либо игрок уже был онлайн, когда плагин включился позже. */
    public void register(Player player) {
        online.join(player);
        engine.joinNow(new PaperViewer(player, bars), packStatus.initialState());
        preferences.load(player, engine::preferencesChanged);
    }

    // Общее хранилище читает настройки здесь, вне главного потока, чтобы к входу они уже были готовы.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            preferences.prefetch(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        register(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        engine.quitNow(id);
        preferences.forget(id);
        online.quit(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        engine.worldChanged(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        packStatus.onStatus(event);
    }
}
