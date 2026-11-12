package ru.fell.notifylib.core.engine;

/** Доставка тоста игроку без ресурспака. */
@FunctionalInterface
public interface FallbackDelivery {

    /** Просто выбрасывает тост. */
    FallbackDelivery NONE = (viewer, content) -> {
    };

    void deliver(ToastViewer viewer, ToastContent content);
}
