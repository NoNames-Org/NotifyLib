package ru.fell.notifylib.core.protocol;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.core.text.FittedText;

/**
 * Собирает имена боссбаров нулевой ширины. Тело тоста рисуется один раз: элементы ставятся по абсолютному x
 * внутри тоста, перо двигается пробелами. На каждом кадре анимации тело только оборачивается в смещение
 * полосы и возврат к началу строки ({@link #name}).
 *
 * <p>Подряд идущие глифы {@code notifylib:toast} одного цвета склеиваются в один компонент, поэтому тело
 * занимает примерно пять компонентов в пакете.
 */
public final class ToastLine {

    private static final TextColor GLYPH_COLOR = NamedTextColor.WHITE;
    // Шрифт и белый цвет задаются один раз на корне и наследуются, чтобы не повторять их в каждом куске.
    private static final Style ROOT_STYLE = Style.style()
            .shadowColor(ShadowColor.shadowColor(0))
            .font(Glyphs.FONT_TOAST)
            .color(GLYPH_COLOR)
            .build();

    private final TextComponent.Builder root = Component.text();
    private final StringBuilder run = new StringBuilder();
    private @Nullable TextColor runColor;
    private int pen;

    /**
     * Готовое имя боссбара: перо уходит в полосу, рисуется тело, перо возвращается в начало строки.
     *
     * @param body    тело из {@link #buildBody()}
     * @param bodyPen позиция пера в конце тела, {@link #pen()}
     */
    public static Component name(int bandOffset, Component body, int bodyPen) {
        return Component.text()
                .style(ROOT_STYLE)
                .append(Component.text(spaces(bandOffset)))
                .append(body)
                .append(Component.text(spaces(-bodyPen - bandOffset)))
                .build();
    }

    /** Переносит перо на {@code x} px от левого края тоста. */
    public ToastLine moveTo(int x) {
        appendSpaces(run, x - pen);
        if (runColor == null && !run.isEmpty()) {
            runColor = GLYPH_COLOR;
        }
        pen = x;
        return this;
    }

    public ToastLine glyph(char glyph, int advance) {
        return glyph(glyph, advance, GLYPH_COLOR);
    }

    /** Глиф {@code notifylib:toast} с цветом (у полосок таймера цвет это данные). */
    public ToastLine glyph(char glyph, int advance, TextColor color) {
        if (runColor != null && !runColor.equals(color)) {
            flush();
        }
        runColor = color;
        run.append(glyph);
        pen += advance;
        return this;
    }

    /** Текст в шрифте {@code minecraft:default} или шрифте второй строки. */
    public ToastLine text(FittedText text, Key font) {
        flush();
        root.append(text.inFont(font));
        pen += text.width();
        return this;
    }

    /** Позиция пера после последнего элемента, px от левого края тоста. */
    public int pen() {
        return pen;
    }

    /** Тело без смещения полосы и без возврата в начало строки. Стиль берёт от корня из {@link #name}. */
    public Component buildBody() {
        flush();
        return root.build();
    }

    private static String spaces(int pixels) {
        StringBuilder out = new StringBuilder(Protocol.SPACE_BITS);
        appendSpaces(out, pixels);
        return out.toString();
    }

    private static void appendSpaces(StringBuilder out, int pixels) {
        if (pixels == 0) {
            return;
        }
        int abs = Math.abs(pixels);
        if (abs >= 1 << Protocol.SPACE_BITS) {
            throw new IllegalArgumentException("слишком широкий пробел: " + pixels);
        }
        char base = pixels > 0 ? Glyphs.SPACE_POSITIVE : Glyphs.SPACE_NEGATIVE;
        for (int bits = abs; bits != 0; bits &= bits - 1) {
            out.append((char) (base + Integer.numberOfTrailingZeros(bits)));
        }
    }

    private void flush() {
        if (run.isEmpty()) {
            return;
        }
        TextColor color = GLYPH_COLOR.equals(runColor) ? null : runColor;
        root.append(Component.text(run.toString(), color));
        run.setLength(0);
        runColor = null;
    }
}
