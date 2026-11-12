package ru.fell.notifylib.api;

import net.kyori.adventure.text.Component;

import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Тост конкретного игрока: видимый, в очереди или уже закрытый. Все методы потокобезопасны: изменения
 * применяются в главном потоке на следующем тике и игнорируются, если тост уже закрыт.
 */
public interface ToastHandle {

    UUID playerId();

    /** Описание, с которым тост был показан. */
    Toast toast();

    /** Меняет текст на месте (без повторного выезда) и перезапускает таймер. */
    void text(Component text);

    /** То же, что {@link #text(Component)}, но текст задаётся строкой MiniMessage. */
    void text(String miniMessage);

    /**
     * Продлевает тост. Общая длительность ограничена {@link Toast#MAX_DURATION}; на постоянный тост не влияет.
     *
     * @throws IllegalArgumentException если {@code extra} нулевой или отрицательный
     */
    void extend(Duration extra);

    /** Убирает тост с анимацией и закрывает его с {@link CloseReason#DISMISSED}. */
    void dismiss();

    /** {@code true}, пока тост видим или стоит в очереди. */
    boolean isActive();

    /**
     * Добавляет слушатель закрытия. Вызывается в главном потоке ровно один раз; если тост уже закрыт,
     * то на следующем тике. Исключения логируются с именем плагина-владельца.
     *
     * @return этот же хэндл
     */
    ToastHandle onClose(Consumer<CloseReason> listener);
}
