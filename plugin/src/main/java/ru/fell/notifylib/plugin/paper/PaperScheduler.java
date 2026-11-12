package ru.fell.notifylib.plugin.paper;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import ru.fell.notifylib.core.scheduler.TaskScheduler;

public final class PaperScheduler implements TaskScheduler {

    private final Plugin plugin;

    public PaperScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Cancellable everyTick(Runnable task) {
        BukkitTask scheduled = Bukkit.getScheduler().runTaskTimer(plugin, task, 1L, 1L);
        return scheduled::cancel;
    }

    @Override
    public Cancellable later(Runnable task, long ticks) {
        BukkitTask scheduled = Bukkit.getScheduler().runTaskLater(plugin, task, ticks);
        return scheduled::cancel;
    }

    @Override
    public void main(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void async(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }
}
