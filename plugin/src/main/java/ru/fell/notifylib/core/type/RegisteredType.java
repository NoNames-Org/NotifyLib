package ru.fell.notifylib.core.type;

import ru.fell.notifylib.api.ToastType;
import ru.fell.notifylib.core.protocol.Glyphs;

import java.util.Objects;

/**
 * Тип вместе с его глифами в ресурспаке.
 *
 * @param glyphBase   первый символ его блока из четырёх в {@code notifylib:toast}
 * @param iconAdvance advance иконки на клиенте (ширина иконки + 1)
 */
public record RegisteredType(ToastType type, char glyphBase, int iconAdvance) {

    public RegisteredType {
        Objects.requireNonNull(type, "type");
    }

    /** @param alt для второй строки двухстрочного тоста */
    public char iconGlyph(boolean alt) {
        return alt ? Glyphs.iconAlt(glyphBase) : Glyphs.icon(glyphBase);
    }

    /** @param alt для второй строки двухстрочного тоста */
    public char barGlyph(boolean alt) {
        return alt ? Glyphs.barAlt(glyphBase) : Glyphs.bar(glyphBase);
    }
}
