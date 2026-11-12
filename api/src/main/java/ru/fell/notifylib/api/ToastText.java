package ru.fell.notifylib.api;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/**
 * Текст строки тоста: готовый компонент или строка MiniMessage (парсится один раз и кэшируется,
 * в том числе при рассылке всем).
 */
public sealed interface ToastText permits ToastText.Rich, ToastText.Markup {

    /** Максимальная длина строки MiniMessage. */
    int MAX_MARKUP_LENGTH = 4096;

    static ToastText of(Component component) {
        return new Rich(component);
    }

    /**
     * @throws IllegalArgumentException если строка длиннее {@link #MAX_MARKUP_LENGTH}
     */
    static ToastText miniMessage(String miniMessage) {
        return new Markup(miniMessage);
    }

    /**
     * Готовый компонент Adventure.
     *
     * @param component при показе его шрифт заменяется на {@code minecraft:default}
     */
    record Rich(Component component) implements ToastText {

        public Rich {
            Objects.requireNonNull(component, "component");
        }
    }

    /** Строка MiniMessage, парсится для каждого получателя. */
    record Markup(String miniMessage) implements ToastText {

        public Markup {
            Objects.requireNonNull(miniMessage, "miniMessage");
            if (miniMessage.length() > MAX_MARKUP_LENGTH) {
                throw new IllegalArgumentException("текст уведомления длиннее " + MAX_MARKUP_LENGTH + " символов");
            }
        }
    }
}
