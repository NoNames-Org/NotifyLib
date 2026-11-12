package ru.fell.notifylib.core.type;

import org.jspecify.annotations.Nullable;

/** Определяет плагин, вызвавший API, чтобы записать на него типы и тосты. */
@FunctionalInterface
public interface CallerResolver {

    /** Ни на кого не записывает (тесты, внутренние вызовы). */
    CallerResolver NONE = () -> null;

    /** Плагин ближайшего кадра стека вне NotifyLib, либо {@code null}, если вызвал не плагин. */
    @Nullable String callingPlugin();
}
