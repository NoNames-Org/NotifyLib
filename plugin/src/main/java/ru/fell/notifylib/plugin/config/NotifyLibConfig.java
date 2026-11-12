package ru.fell.notifylib.plugin.config;

import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.core.content.FallbackMode;
import ru.fell.notifylib.core.engine.EngineSettings;
import ru.fell.notifylib.core.type.RegisteredType;
import ru.fell.notifylib.plugin.storage.MySqlSettings;

import java.util.List;
import java.util.Objects;

/**
 * Разобранный {@code config.yml}. Неизменяемый, reload создаёт новый экземпляр.
 *
 * @param fallback канал для игроков без ресурспака
 * @param types    свои типы уведомлений (их глифы нужно добавить в пак вручную)
 * @param mysql    общая база настроек или {@code null}, если настройки хранятся локально
 */
public record NotifyLibConfig(EngineSettings engine, FallbackMode fallback, List<RegisteredType> types,
                             Messages messages, @Nullable MySqlSettings mysql) {

    public NotifyLibConfig {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(fallback, "fallback");
        types = List.copyOf(types);
        Objects.requireNonNull(messages, "messages");
    }
}
