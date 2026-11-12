package ru.fell.notifylib.core.engine;

import java.util.UUID;

/** Закэшированные настройки игроков. Читается из любого потока без блокировок. */
@FunctionalInterface
public interface PreferencesSource {

    PreferencesSource DEFAULTS = playerId -> ToastPreferences.DEFAULT;

    /** Для неизвестных игроков возвращает настройки по умолчанию. */
    ToastPreferences preferences(UUID playerId);
}
