package ru.fell.notifylib.core.text;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Ширины символов ванильного шрифта {@code minecraft:default} (1.21.11), вытащенные из клиента утилитой
 * {@code notifylib-tools}. Символы, которых нет в растровых шрифтах, клиент рисует Unifont'ом; для них
 * ширина прикидывается.
 */
public final class GlyphWidths {

    public static final String RESOURCE = "notifylib/fonts/glyph-widths.bin";

    private static final int FORMAT_VERSION = 1;
    private static final int BMP_SIZE = 0x10000;
    private static final int ZERO_WIDTH = 0x80;
    private static final int UNIFONT_NARROW = 6;
    private static final int UNIFONT_WIDE = 9;

    private final byte[] advances;

    private GlyphWidths(byte[] advances) {
        this.advances = advances;
    }

    public static GlyphWidths load() {
        InputStream in = GlyphWidths.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException("В jar нет ресурса " + RESOURCE);
        }
        // read() закрывает поток сам.
        try {
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать " + RESOURCE, e);
        }
    }

    static GlyphWidths read(InputStream raw) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!"TLGW".equals(new String(magic, StandardCharsets.US_ASCII))
                    || in.readUnsignedByte() != FORMAT_VERSION) {
                throw new IOException("неподдерживаемый формат таблицы ширин глифов");
            }
            byte[] advances = new byte[BMP_SIZE];
            in.readFully(advances);
            return new GlyphWidths(advances);
        }
    }

    /** Ширина символа в обычном (не жирном) начертании, px. */
    public int advance(int codePoint) {
        if (codePoint < BMP_SIZE) {
            int value = advances[codePoint] & 0xFF;
            if (value == ZERO_WIDTH) {
                return 0;
            }
            if (value != 0) {
                return value;
            }
        }
        return isWide(codePoint) ? UNIFONT_WIDE : UNIFONT_NARROW;
    }

    /** Есть ли символ в ванильных растровых шрифтах, то есть точна ли его ширина. */
    public boolean isExact(int codePoint) {
        return codePoint < BMP_SIZE && advances[codePoint] != 0;
    }

    private static boolean isWide(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA || script == Character.UnicodeScript.HANGUL
                || codePoint >= 0x1F000;
    }
}
