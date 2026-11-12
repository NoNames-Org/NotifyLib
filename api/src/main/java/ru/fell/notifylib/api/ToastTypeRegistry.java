package ru.fell.notifylib.api;

import net.kyori.adventure.key.Key;

import java.util.Collection;
import java.util.Optional;

/**
 * Известные типы тостов: встроенные ({@link ToastTypes}) и объявленные в {@code config.yml} NotifyLib.
 * Потокобезопасен; типы из конфига меняются при {@code /notify reload}.
 */
public interface ToastTypeRegistry {

    Optional<ToastType> get(Key key);

    /**
     * Все типы, встроенные идут первыми.
     *
     * @return неизменяемый снимок
     */
    Collection<ToastType> all();
}
