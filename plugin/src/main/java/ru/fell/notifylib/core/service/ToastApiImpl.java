package ru.fell.notifylib.core.service;

import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.Corner;
import ru.fell.notifylib.api.PlayerToastSettings;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.api.ToastApi;
import ru.fell.notifylib.api.ToastHandle;
import ru.fell.notifylib.api.ToastType;
import ru.fell.notifylib.api.ToastTypeRegistry;
import ru.fell.notifylib.core.engine.ToastEngine;
import ru.fell.notifylib.core.settings.PlayerToastSettingsImpl;
import ru.fell.notifylib.core.settings.PreferencesStore;
import ru.fell.notifylib.core.type.CallerResolver;
import ru.fell.notifylib.core.type.ToastTypeRegistryImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Фасад API: проверяет аргументы в потоке вызывающего, определяет плагин-источник и передаёт работу движку.
 */
public final class ToastApiImpl implements ToastApi {

    private final ToastEngine engine;
    private final ToastTypeRegistryImpl types;
    private final PreferencesStore preferences;
    private final OnlinePlayers online;
    private final CallerResolver callers;
    private final Supplier<Corner> defaultCorner;

    public ToastApiImpl(ToastEngine engine, ToastTypeRegistryImpl types, PreferencesStore preferences,
                        OnlinePlayers online, CallerResolver callers, Supplier<Corner> defaultCorner) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.types = Objects.requireNonNull(types, "types");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.online = Objects.requireNonNull(online, "online");
        this.callers = Objects.requireNonNull(callers, "callers");
        this.defaultCorner = Objects.requireNonNull(defaultCorner, "defaultCorner");
    }

    @Override
    public ToastHandle show(Player player, ToastType type, String miniMessage) {
        Toast toast = Toast.builder().type(type).text(miniMessage).build();
        return show(player, toast, callers.callingPlugin());
    }

    @Override
    public ToastHandle show(Player player, ToastType type, Component text) {
        Toast toast = Toast.builder().type(type).text(text).build();
        return show(player, toast, callers.callingPlugin());
    }

    @Override
    public ToastHandle show(Player player, Toast toast) {
        return show(player, toast, callers.callingPlugin());
    }

    /** Показ от имени известного плагина (команды, внутренние вызовы). */
    public ToastHandle show(Player player, Toast toast, @Nullable String owner) {
        Objects.requireNonNull(player, "player");
        validate(toast);
        return engine.show(player.getUniqueId(), toast, owner);
    }

    @Override
    public List<ToastHandle> broadcast(Toast toast) {
        return send(online.snapshot(), toast, callers.callingPlugin());
    }

    @Override
    public List<ToastHandle> broadcast(Collection<? extends Player> players, Toast toast) {
        Objects.requireNonNull(players, "players");
        return send(List.copyOf(players), toast, callers.callingPlugin());
    }

    @Override
    public List<ToastHandle> broadcast(Predicate<? super Player> filter, Toast toast) {
        Objects.requireNonNull(filter, "filter");
        return sendMatching(filter, toast, callers.callingPlugin());
    }

    @Override
    public List<ToastHandle> broadcastPermission(String permission, Toast toast) {
        Objects.requireNonNull(permission, "permission");
        return sendMatching(player -> player.hasPermission(permission), toast, callers.callingPlugin());
    }

    @Override
    public List<ToastHandle> broadcast(World world, Toast toast) {
        Objects.requireNonNull(world, "world");
        return sendMatching(player -> world.equals(player.getWorld()), toast, callers.callingPlugin());
    }

    @Override
    public ToastTypeRegistry types() {
        return types;
    }

    @Override
    public PlayerToastSettings settings(Player player) {
        Objects.requireNonNull(player, "player");
        return new PlayerToastSettingsImpl(player.getUniqueId(), preferences, defaultCorner);
    }

    @Override
    public List<ToastHandle> active(Player player) {
        Objects.requireNonNull(player, "player");
        return engine.active(player.getUniqueId());
    }

    @Override
    public void dismissAll(Player player) {
        Objects.requireNonNull(player, "player");
        engine.dismissAll(player.getUniqueId());
    }

    // Проверяем тост до обхода игроков: ошибка должна вылетать, даже если фильтру никто не подошёл.
    private List<ToastHandle> sendMatching(Predicate<? super Player> filter, Toast toast, @Nullable String owner) {
        validate(toast);
        List<Player> recipients = new ArrayList<>();
        for (Player player : online.view()) {
            if (filter.test(player)) {
                recipients.add(player);
            }
        }
        return send(recipients, toast, owner);
    }

    private List<ToastHandle> send(List<? extends Player> players, Toast toast, @Nullable String owner) {
        validate(toast);
        List<ToastHandle> handles = new ArrayList<>(players.size());
        for (Player player : players) {
            handles.add(engine.show(player.getUniqueId(), toast, owner));
        }
        return List.copyOf(handles);
    }

    private void validate(Toast toast) {
        Objects.requireNonNull(toast, "toast");
        if (types.lookup(toast.type().key()).isEmpty()) {
            throw new IllegalArgumentException("тип уведомления " + toast.type().key().asString() + " не зарегистрирован");
        }
    }
}
