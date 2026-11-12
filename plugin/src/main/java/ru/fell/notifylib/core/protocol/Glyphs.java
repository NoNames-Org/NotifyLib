package ru.fell.notifylib.core.protocol;

import net.kyori.adventure.key.Key;

/**
 * Символы Private Use Area шрифта {@code notifylib:toast}. Постоянные глифы лежат ниже {@link #TYPE_FIRST};
 * дальше каждый тип тоста получает блок из {@link #TYPE_BLOCK} символов. Блок выдаётся один раз и
 * сохраняется в {@code glyphs.yml}.
 */
public final class Glyphs {

    /** Шрифт панелей, иконок, полосок и пробелов. */
    public static final Key FONT_TOAST = Key.key("notifylib", "toast");
    /** Копия {@code minecraft:default}, опущенная на вторую строку двухстрочного тоста. */
    public static final Key FONT_ALT = Key.key("notifylib", "alt");
    public static final Key FONT_DEFAULT = Key.key("minecraft", "default");

    /** Однострочная панель 160x18. */
    public static final char PANEL = '';
    /** Двухстрочная панель для верхних углов: строки 0..17, основная строка. */
    public static final char PANEL_TALL_TOP_MAIN = '';
    /** Двухстрочная панель для верхних углов: строки 18..27, вторая строка. */
    public static final char PANEL_TALL_TOP_ALT = '';
    /** Двухстрочная панель для нижних углов: строки 0..9, вторая строка. */
    public static final char PANEL_TALL_BOTTOM_ALT = '';
    /** Двухстрочная панель для нижних углов: строки 10..27, основная строка. */
    public static final char PANEL_TALL_BOTTOM_MAIN = '';

    /** {@code SPACE_POSITIVE + bit} сдвигает перо на {@code 2^bit}. */
    public static final char SPACE_POSITIVE = '';
    /** {@code SPACE_NEGATIVE + bit} сдвигает перо на {@code -2^bit}. */
    public static final char SPACE_NEGATIVE = '';

    /** Первый символ, доступный типам тостов. */
    public static final char TYPE_FIRST = '';
    /** Последний символ Private Use Area. */
    public static final char TYPE_LAST = '';
    /** Символов на тип: иконка, иконка второй строки, полоска, полоска второй строки. */
    public static final int TYPE_BLOCK = 4;
    public static final int MAX_TYPES = (TYPE_LAST - TYPE_FIRST + 1) / TYPE_BLOCK;

    private Glyphs() {
    }

    /** Иконка блока типа. */
    public static char icon(char base) {
        return base;
    }

    public static char iconAlt(char base) {
        return (char) (base + 1);
    }

    public static char bar(char base) {
        return (char) (base + 2);
    }

    public static char barAlt(char base) {
        return (char) (base + 3);
    }
}
