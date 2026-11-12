package ru.fell.notifylib.plugin;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.ToastApi;
import ru.fell.notifylib.core.content.DefaultContentFactory;
import ru.fell.notifylib.core.engine.ToastEngine;
import ru.fell.notifylib.core.engine.ToastPreferences;
import ru.fell.notifylib.core.protocol.Protocol;
import ru.fell.notifylib.core.render.ToastRenderer;
import ru.fell.notifylib.core.scheduler.IdleTicker;
import ru.fell.notifylib.core.scheduler.TaskScheduler;
import ru.fell.notifylib.core.service.OnlinePlayers;
import ru.fell.notifylib.core.service.ToastApiImpl;
import ru.fell.notifylib.core.settings.PdcPreferencesStorage;
import ru.fell.notifylib.core.settings.PreferencesStorage;
import ru.fell.notifylib.core.settings.PreferencesStore;
import ru.fell.notifylib.core.text.GlyphWidths;
import ru.fell.notifylib.core.text.TextFitter;
import ru.fell.notifylib.core.text.TextResolver;
import ru.fell.notifylib.core.type.ToastTypeRegistryImpl;
import ru.fell.notifylib.plugin.command.ToastCommand;
import ru.fell.notifylib.plugin.config.ConfigLoader;
import ru.fell.notifylib.plugin.config.Messages;
import ru.fell.notifylib.plugin.config.NotifyLibConfig;
import ru.fell.notifylib.plugin.listener.LifecycleListener;
import ru.fell.notifylib.plugin.listener.PlayerListener;
import ru.fell.notifylib.plugin.paper.BarSender;
import ru.fell.notifylib.plugin.paper.PackStatusTracker;
import ru.fell.notifylib.plugin.paper.PaperCallerResolver;
import ru.fell.notifylib.plugin.paper.PaperEngineHooks;
import ru.fell.notifylib.plugin.paper.PaperScheduler;
import ru.fell.notifylib.plugin.storage.MySqlPreferencesStorage;
import ru.fell.notifylib.plugin.storage.MySqlSettings;

import java.io.File;
import java.io.Reader;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

/**
 * Точка входа и сборка всех зависимостей. API регистрируется уже в {@code onLoad}, чтобы зависимые плагины
 * могли брать его в своём {@code onLoad}. Ресурспак здесь не собирается: он идёт отдельно, и выдаёт его
 * игрокам другой плагин.
 */
public final class NotifyLibPlugin extends JavaPlugin {

    // Имена боссбаров рендерятся и отправляются в этом потоке, главный поток только собирает кадры.
    private static final String BAR_THREAD = "NotifyLib-bars";
    /** Сколько onDisable ждёт, пока уйдут пакеты удаления боссбаров. */
    private static final Duration BAR_SHUTDOWN_WAIT = Duration.ofSeconds(2);

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private volatile @Nullable NotifyLibConfig config;
    private @Nullable Core core;
    private @Nullable Runtime runtime;

    private record Core(TaskScheduler scheduler, OnlinePlayers online, ToastTypeRegistryImpl types,
                        ExecutorService barThread, BarSender bars, PreferencesStore preferences, ToastEngine engine,
                        ToastApiImpl api) {
    }

    private record Runtime(IdleTicker ticker) {
    }

    @Override
    public void onLoad() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), Messages.FILE).exists()) {
            saveResource(Messages.FILE, false);
        }
        NotifyLibConfig loaded = loadConfig();
        config = loaded;
        TaskScheduler scheduler = new PaperScheduler(this);
        PaperCallerResolver callers = new PaperCallerResolver();
        ToastTypeRegistryImpl types = new ToastTypeRegistryImpl();
        types.replaceCustom(loaded.types());
        OnlinePlayers online = new OnlinePlayers();
        TextFitter fitter = new TextFitter(GlyphWidths.load());
        TextResolver resolver = new TextResolver(miniMessage);
        ExecutorService barThread = Threads.single(BAR_THREAD, getLogger());
        BarSender bars = new BarSender(barThread, new ToastRenderer(Protocol.PANEL_ADVANCE));
        PreferencesStore preferences = new PreferencesStore(openStorage(loaded.mysql()), scheduler, getLogger(),
                (id, prefs) -> persist(id));
        ToastEngine engine = new ToastEngine(loaded.engine(),
                new DefaultContentFactory(resolver, fitter, types, () -> config().engine()),
                new PaperEngineHooks(online, getLogger()), preferences);
        engine.fallback(loaded.fallback().delivery());
        ToastApiImpl api = new ToastApiImpl(engine, types, preferences, online, callers, () -> config().engine().corner());
        core = new Core(scheduler, online, types, barThread, bars, preferences, engine, api);
        Bukkit.getServicesManager().register(ToastApi.class, api, this, ServicePriority.Normal);
    }

    @Override
    public void onEnable() {
        Core c = core();
        // Тикер крутится, только пока есть уведомления или команды в очереди. Время берём из счётчика
        // тиков сервера, чтобы паузы простоя не сбивали таймеры.
        c.engine().clock(() -> Bukkit.getCurrentTick());
        IdleTicker ticker = new IdleTicker(c.scheduler(), c.engine()::tick, c.engine()::idle);
        runtime = new Runtime(ticker);
        c.engine().onWork(ticker::wake);
        ticker.wake();

        PlayerListener players = new PlayerListener(c.engine(), c.online(), c.preferences(),
                new PackStatusTracker(c.engine()), c.bars());
        getServer().getPluginManager().registerEvents(players, this);
        getServer().getPluginManager().registerEvents(new LifecycleListener(this, c.engine()), this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            players.register(player);
        }

        ToastCommand toast = new ToastCommand(c.api(), c.types(), c.online(), () -> config().messages(),
                this::reload);
        Messages messages = config().messages();
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(toast.node(), messages.template("command-notify"), List.of());
        });
        getLogger().info("Включён, типов уведомлений: " + c.types().registrations().size());
    }

    @Override
    public void onDisable() {
        Runtime r = runtime;
        if (r != null) {
            r.ticker().close();
        }
        if (core != null) {
            core.engine().shutdown();
            core.preferences().close();
            // Даём пакетам удаления боссбаров дойти до игроков и только потом гасим поток.
            Threads.stop(core.barThread(), BAR_SHUTDOWN_WAIT, getLogger(),
                    "Поток боссбаров не успел завершиться, часть уведомлений провисит до перезахода");
        }
        Bukkit.getServicesManager().unregisterAll(this);
        runtime = null;
    }

    /**
     * MySQL, если задан {@code storage.mysql.host}, иначе PDC игрока на этом сервере.
     * Если база недоступна, откатываемся на локальное хранилище, чтобы плагин продолжал работать.
     */
    private PreferencesStorage openStorage(@Nullable MySqlSettings mysql) {
        if (mysql == null) {
            getLogger().info("Настройки уведомлений хранятся локально (storage.mysql.host не задан)");
            return new PdcPreferencesStorage(getLogger());
        }
        try {
            MySqlPreferencesStorage storage = MySqlPreferencesStorage.open(mysql, getLogger());
            getLogger().info("Настройки уведомлений хранятся в MySQL " + mysql.host() + ":" + mysql.port()
                    + "/" + mysql.database());
            return storage;
        } catch (SQLException | RuntimeException e) {
            getLogger().log(Level.SEVERE, "MySQL недоступен, настройки уведомлений до рестарта хранятся локально", e);
            return new PdcPreferencesStorage(getLogger());
        }
    }

    // /notify reload: перечитывает конфиг, свои типы и сообщения. Смена хранилища требует рестарта.
    private void reload() {
        NotifyLibConfig next = loadConfig();
        config = next;
        Core c = core();
        c.types().replaceCustom(next.types());
        c.engine().settings(next.engine());
        c.engine().fallback(next.fallback().delivery());
    }

    private NotifyLibConfig loadConfig() {
        reloadConfig();
        File messageFile = new File(getDataFolder(), Messages.FILE);
        YamlConfiguration messages = YamlConfiguration.loadConfiguration(messageFile);
        return new ConfigLoader(miniMessage, message -> getLogger().warning(message))
                .load(getConfig(), messages, bundledMessages());
    }

    // loadConfiguration сам закрывает reader.
    private YamlConfiguration bundledMessages() {
        Reader reader = getTextResource(Messages.FILE);
        if (reader == null) {
            throw new IllegalStateException("В jar плагина нет " + Messages.FILE);
        }
        return YamlConfiguration.loadConfiguration(reader);
    }

    // Сохраняет настройки в главном потоке и перерисовывает уведомления игрока (мог смениться угол).
    private void persist(UUID playerId) {
        Core c = core();
        c.scheduler().main(() -> {
            Player player = c.online().get(playerId);
            if (player != null) {
                ToastPreferences latest = c.preferences().preferences(playerId);
                c.preferences().save(player, latest);
            }
            c.engine().preferencesChanged(playerId);
        });
    }

    private NotifyLibConfig config() {
        NotifyLibConfig current = config;
        if (current == null) {
            throw new IllegalStateException("Конфиг NotifyLib ещё не загружен");
        }
        return current;
    }

    private Core core() {
        Core current = core;
        if (current == null) {
            throw new IllegalStateException("NotifyLib ещё не загружен");
        }
        return current;
    }
}
