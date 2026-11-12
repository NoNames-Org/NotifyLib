package ru.fell.notifylib.core.engine;

import ru.fell.notifylib.api.Corner;
import ru.fell.notifylib.api.Overflow;
import ru.fell.notifylib.core.protocol.Protocol;

import java.util.Objects;

/**
 * Настройки движка из конфига. При перезагрузке заменяются целиком.
 *
 * @param maxVisible сколько тостов одновременно на экране, {@code 1..4}
 * @param queueLimit сколько тостов может ждать у одного игрока
 * @param sound      глобальный выключатель звуков
 * @param debug      писать фазы тостов в лог
 */
public record EngineSettings(Corner corner, int defaultDurationTicks, int maxVisible, Overflow overflow,
                             int queueLimit, boolean sound, StackOrder order, boolean debug) {

    /** Совпадает со значениями из встроенного config.yml. */
    public static final EngineSettings DEFAULTS = new EngineSettings(Corner.TOP_LEFT, 100, Protocol.MAX_SLOTS,
            Overflow.QUEUE, 10, true, StackOrder.END, false);

    public EngineSettings {
        Objects.requireNonNull(corner, "corner");
        Objects.requireNonNull(overflow, "overflow");
        Objects.requireNonNull(order, "order");
        if (maxVisible < 1 || maxVisible > Protocol.MAX_SLOTS) {
            throw new IllegalArgumentException("maxVisible должен быть от 1 до " + Protocol.MAX_SLOTS);
        }
        if (defaultDurationTicks < 20 || defaultDurationTicks > Protocol.MAX_DURATION_TICKS) {
            throw new IllegalArgumentException("длительность по умолчанию вне допустимого диапазона");
        }
        if (queueLimit < 0) {
            throw new IllegalArgumentException("лимит очереди не может быть отрицательным");
        }
    }
}
