package ru.fell.notifylib.core.engine;

import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.CloseReason;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.api.ToastHandle;

import java.util.UUID;

/** Обратные вызовы движка в платформу: события и логи. Только главный поток. */
public interface EngineHooks {

    /** Кидает событие показа. Возвращает тост (возможно, подменённый) или {@code null}, если отменили. */
    @Nullable Toast beforeShow(UUID playerId, ToastHandle handle, Toast toast);

    void afterClose(UUID playerId, ToastHandle handle, Toast toast, CloseReason reason);

    /** Колбэк чужого плагина бросил исключение. {@code owner} может быть неизвестен. */
    void callbackFailed(@Nullable String owner, Throwable error);

    /** Неожиданная ошибка внутри движка. Движок продолжает работать. */
    void engineFailed(String message, Throwable error);

    /** Вызывается только при включённом debug. */
    void trace(String message);

    /** Пустая реализация для тестов. */
    static EngineHooks noop() {
        return new EngineHooks() {
            @Override
            public Toast beforeShow(UUID playerId, ToastHandle handle, Toast toast) {
                return toast;
            }

            @Override
            public void afterClose(UUID playerId, ToastHandle handle, Toast toast, CloseReason reason) {
            }

            @Override
            public void callbackFailed(@Nullable String owner, Throwable error) {
            }

            @Override
            public void engineFailed(String message, Throwable error) {
            }

            @Override
            public void trace(String message) {
            }
        };
    }
}
