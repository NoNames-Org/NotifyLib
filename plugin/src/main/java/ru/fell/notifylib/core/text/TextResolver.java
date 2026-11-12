package ru.fell.notifylib.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Objects;

/** Парсит MiniMessage-тексты тостов. */
public final class TextResolver {

    private final MiniMessage miniMessage;

    public TextResolver(MiniMessage miniMessage) {
        this.miniMessage = Objects.requireNonNull(miniMessage, "miniMessage");
    }

    public Component parse(String input) {
        return miniMessage.deserialize(input);
    }
}
