package ru.fell.notifylib.plugin.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.Plugin;
import ru.fell.notifylib.api.CloseReason;
import ru.fell.notifylib.core.engine.ToastEngine;

/** Закрывает уведомления плагинов, которые выключаются. */
public final class LifecycleListener implements Listener {

    private final Plugin self;
    private final ToastEngine engine;

    public LifecycleListener(Plugin self, ToastEngine engine) {
        this.self = self;
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        Plugin plugin = event.getPlugin();
        if (plugin == self) {
            return;
        }
        String name = plugin.getName();
        engine.closeWhereNow(toast -> name.equals(toast.owner()), CloseReason.PLUGIN_DISABLED);
    }
}
