package ru.fell.notifylib.core.content;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import ru.fell.notifylib.core.engine.FallbackDelivery;
import ru.fell.notifylib.core.engine.ToastContent;
import ru.fell.notifylib.core.text.FittedText;

/** Как доставлять тосты игрокам без ресурспака. */
public enum FallbackMode {
    /** Строка над хотбаром: цветной маркер, заголовок, текст. */
    ACTIONBAR {
        @Override
        public FallbackDelivery delivery() {
            return (viewer, content) -> viewer.sendActionBar(plain(content));
        }
    },
    /** Сообщение в чат в том же формате. */
    CHAT {
        @Override
        public FallbackDelivery delivery() {
            return (viewer, content) -> viewer.sendMessage(plain(content));
        }
    },
    /** Не показывать ничего. */
    NONE {
        @Override
        public FallbackDelivery delivery() {
            return FallbackDelivery.NONE;
        }
    };

    private static final String MARKER = "■ ";
    private static final String TITLE_SEPARATOR = ": ";

    public abstract FallbackDelivery delivery();

    static Component plain(ToastContent content) {
        TextColor color = content.type().type().barColor();
        Component line = Component.text(MARKER, color);
        FittedText title = content.title();
        if (title != null) {
            line = line.append(title.component()).append(Component.text(TITLE_SEPARATOR, color));
        }
        return line.append(content.text().component());
    }
}
