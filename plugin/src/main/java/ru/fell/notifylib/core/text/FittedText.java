package ru.fell.notifylib.core.text;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Текст, готовый для строки тоста: плоский, без шрифтов и интерактивных событий, обрезан по ширине.
 * Неизменяемый и общий через кэш содержимого, поэтому копии со шрифтом для рендера хранятся прямо здесь
 * и строятся один раз на текст, а не на каждый кадр.
 */
public final class FittedText {

    private final Component component;
    private final int width;
    private final boolean truncated;
    // Шрифтов максимум два (основной и второй строки). В каждой ячейке неизменяемая пара, поэтому читатель из
    // другого потока видит либо ничего, либо целую пару; гонка лишь построит копию дважды.
    private @Nullable Styled first;
    private @Nullable Styled second;

    private record Styled(Key font, Component component) {
    }

    /**
     * @param component текст без шрифта, шрифт задаёт строка
     * @param width     точная ширина, px
     * @param truncated было ли дописано "…"
     */
    public FittedText(Component component, int width, boolean truncated) {
        this.component = Objects.requireNonNull(component, "component");
        if (width < 0) {
            throw new IllegalArgumentException("ширина не может быть отрицательной");
        }
        this.width = width;
        this.truncated = truncated;
    }

    public Component component() {
        return component;
    }

    public int width() {
        return width;
    }

    public boolean truncated() {
        return truncated;
    }

    /** Текст в нужном шрифте, с кэшем. Шрифт сравнивается по ссылке: сюда передают только константы. */
    public Component inFont(Key font) {
        Styled a = first;
        if (a != null && a.font() == font) {
            return a.component();
        }
        Styled b = second;
        if (b != null && b.font() == font) {
            return b.component();
        }
        Styled styled = new Styled(font, component.font(font));
        if (a == null) {
            first = styled;
        } else {
            second = styled;
        }
        return styled.component();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FittedText that && width == that.width && truncated == that.truncated
                && component.equals(that.component);
    }

    @Override
    public int hashCode() {
        return Objects.hash(component, width, truncated);
    }

    @Override
    public String toString() {
        return "FittedText[width=" + width + ", truncated=" + truncated + ", component=" + component + "]";
    }
}
