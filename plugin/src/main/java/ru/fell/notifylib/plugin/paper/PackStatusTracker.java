package ru.fell.notifylib.plugin.paper;

import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import ru.fell.notifylib.core.engine.DeliveryState;
import ru.fell.notifylib.core.engine.ToastEngine;

/**
 * Переводит статусы ресурспака в {@link DeliveryState}. Пак выдаёт другой плагин, поэтому учитываем любой
 * статус: пока не сказано обратного, считаем, что пак есть. Во время загрузки уведомления ждут, при отказе
 * или ошибке загрузки уходят в запасной канал.
 */
public final class PackStatusTracker {

    private final ToastEngine engine;

    public PackStatusTracker(ToastEngine engine) {
        this.engine = engine;
    }

    public DeliveryState initialState() {
        return DeliveryState.READY;
    }

    public void onStatus(PlayerResourcePackStatusEvent event) {
        DeliveryState state = switch (event.getStatus()) {
            case ACCEPTED, DOWNLOADED -> DeliveryState.PENDING;
            case SUCCESSFULLY_LOADED -> DeliveryState.READY;
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> DeliveryState.FAILED;
            // Старая копия заменённого пака, новый пришлёт свой статус следом.
            case DISCARDED -> null;
        };
        if (state != null) {
            engine.deliveryChanged(event.getPlayer().getUniqueId(), state);
        }
    }
}
