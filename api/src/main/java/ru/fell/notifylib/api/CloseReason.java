package ru.fell.notifylib.api;

/** Почему тост закрылся. */
public enum CloseReason {
    /** Истёк таймер. */
    EXPIRED,
    /** Вызван {@link ToastHandle#dismiss()} или {@link ToastApi#dismissAll(org.bukkit.entity.Player)}. */
    DISMISSED,
    /** Вытеснен более новым тостом с {@link Overflow#REPLACE_OLDEST}. */
    REPLACED,
    /** Отброшен: стек и очередь заполнены. */
    OVERFLOW,
    /** Игрок вышел с сервера. */
    QUIT,
    /** Выключен плагин, показавший тост, или сам NotifyLib. */
    PLUGIN_DISABLED,
    /**
     * Тост так и не был показан: {@link ru.fell.notifylib.api.event.ToastShowEvent} отменён, игрок отключил
     * тосты или этот тип, либо игрок не в сети.
     *
     * @since 1.0.0
     */
    CANCELLED
}
