package ru.fell.notifylib.core.service;

import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасный список онлайн-игроков, ведётся по событиям входа и выхода. Позволяет вызовам API из
 * асинхронных потоков брать снимок, не трогая список игроков сервера.
 */
public final class OnlinePlayers {

    private final Map<UUID, Player> players = new ConcurrentHashMap<>();

    /** Главный поток. */
    public void join(Player player) {
        players.put(player.getUniqueId(), player);
    }

    /** Главный поток. */
    public void quit(UUID playerId) {
        players.remove(playerId);
    }

    /** {@code null}, если игрок не в сети. */
    public @Nullable Player get(UUID playerId) {
        return players.get(playerId);
    }

    public List<Player> snapshot() {
        return List.copyOf(players.values());
    }

    /** Живое слабо согласованное представление, без копирования. */
    public Collection<Player> view() {
        return players.values();
    }
}
