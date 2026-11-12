package ru.fell.notifylib.core.engine;

import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.api.ToastText;

import java.util.UUID;

/** Собирает содержимое тоста под конкретного получателя. Только главный поток. */
public interface ContentFactory {

    /** Возвращает {@code null}, если тип тоста уже сняли с регистрации. */
    @Nullable ToastContent create(UUID recipient, Toast toast);

    /** Меняет основной текст, остальное оставляет как было. */
    ToastContent withText(UUID recipient, ToastContent current, ToastText text);
}
