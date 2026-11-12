package ru.fell.notifylib.core.settings;

import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import ru.fell.notifylib.api.Corner;
import ru.fell.notifylib.core.engine.ToastPreferences;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/** Локальное хранилище в {@link PersistentDataContainer} игрока на этом сервере. Только главный поток. */
public final class PdcPreferencesStorage implements PreferencesStorage {

    private static final String NAMESPACE = "notifylib";

    private final NamespacedKey cornerKey = new NamespacedKey(NAMESPACE, "corner");
    private final NamespacedKey enabledKey = new NamespacedKey(NAMESPACE, "enabled");
    private final NamespacedKey soundKey = new NamespacedKey(NAMESPACE, "sound");
    private final NamespacedKey hiddenKey = new NamespacedKey(NAMESPACE, "hidden");
    private final Logger logger;

    public PdcPreferencesStorage(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public CompletableFuture<ToastPreferences> load(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        String where = player.getName();
        Corner corner = PreferencesCodec.corner(data.get(cornerKey, PersistentDataType.STRING), where, logger);
        boolean enabled = data.getOrDefault(enabledKey, PersistentDataType.BOOLEAN, true);
        boolean sound = data.getOrDefault(soundKey, PersistentDataType.BOOLEAN, true);
        List<String> hidden = data.getOrDefault(hiddenKey, PersistentDataType.LIST.strings(), List.of());
        return CompletableFuture.completedFuture(new ToastPreferences(corner, enabled, sound,
                PreferencesCodec.keys(hidden, where, logger)));
    }

    @Override
    public void save(Player player, ToastPreferences preferences) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        Corner corner = preferences.corner();
        if (corner == null) {
            data.remove(cornerKey);
        } else {
            data.set(cornerKey, PersistentDataType.STRING, corner.name());
        }
        data.set(enabledKey, PersistentDataType.BOOLEAN, preferences.enabled());
        data.set(soundKey, PersistentDataType.BOOLEAN, preferences.sound());
        if (preferences.hidden().isEmpty()) {
            data.remove(hiddenKey);
        } else {
            List<String> keys = preferences.hidden().stream().map(Key::asString).sorted().toList();
            data.set(hiddenKey, PersistentDataType.LIST.strings(), keys);
        }
    }
}
