package ru.fell.notifylib.core.settings;

import net.kyori.adventure.key.Key;
import ru.fell.notifylib.api.Corner;
import ru.fell.notifylib.api.PlayerToastSettings;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Живое представление настроек одного игрока поверх {@link PreferencesStore}. */
public final class PlayerToastSettingsImpl implements PlayerToastSettings {

    private final UUID playerId;
    private final PreferencesStore store;
    private final Supplier<Corner> defaultCorner;

    public PlayerToastSettingsImpl(UUID playerId, PreferencesStore store, Supplier<Corner> defaultCorner) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.store = Objects.requireNonNull(store, "store");
        this.defaultCorner = Objects.requireNonNull(defaultCorner, "defaultCorner");
    }

    @Override
    public UUID playerId() {
        return playerId;
    }

    @Override
    public Corner corner() {
        return store.preferences(playerId).cornerOr(defaultCorner.get());
    }

    @Override
    public void corner(Corner corner) {
        Objects.requireNonNull(corner, "corner");
        store.update(playerId, prefs -> prefs.withCorner(corner));
    }

    @Override
    public void resetCorner() {
        store.update(playerId, prefs -> prefs.withCorner(null));
    }

    @Override
    public boolean enabled() {
        return store.preferences(playerId).enabled();
    }

    @Override
    public void enabled(boolean enabled) {
        store.update(playerId, prefs -> prefs.withEnabled(enabled));
    }

    @Override
    public boolean sound() {
        return store.preferences(playerId).sound();
    }

    @Override
    public void sound(boolean sound) {
        store.update(playerId, prefs -> prefs.withSound(sound));
    }

    @Override
    public Set<Key> hiddenTypes() {
        return store.preferences(playerId).hidden();
    }

    @Override
    public void hidden(Key type, boolean hidden) {
        Objects.requireNonNull(type, "type");
        store.update(playerId, prefs -> prefs.withHidden(type, hidden));
    }
}
