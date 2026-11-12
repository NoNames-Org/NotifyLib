package ru.fell.notifylib.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Меряет и обрезает стилизованный текст под строку тоста.
 *
 * <p>Дерево компонентов раскладывается в куски с полностью вычисленным стилем. Шрифты, click/hover и insertion
 * выкидываются (строка ставит свой шрифт, а боссбар не интерактивен); цвета, градиенты и декорации остаются.
 * Жирные глифы на 1 px шире, как в клиенте.
 */
public final class TextFitter {

    private static final int ELLIPSIS = '…';
    private static final int REPLACEMENT = '?';
    private static final Map<TextDecoration, TextDecoration.State> ALT_UNSAFE = Map.of(
            TextDecoration.ITALIC, TextDecoration.State.FALSE,
            TextDecoration.UNDERLINED, TextDecoration.State.FALSE,
            TextDecoration.STRIKETHROUGH, TextDecoration.State.FALSE);

    private final GlyphWidths widths;

    public TextFitter(GlyphWidths widths) {
        this.widths = Objects.requireNonNull(widths, "widths");
    }

    /** Вписывает текст в {@code maxWidth} px; если не влезает, дописывает "…" в стиле обрезанного куска. */
    public FittedText fit(Component text, int maxWidth) {
        return fit(flatten(text), maxWidth);
    }

    /**
     * {@link #fit} для строки двухстрочного тоста. Её может рисовать {@code notifylib:alt}, копия стандартного
     * шрифта, опущенная через ascent. Поэтому символы вне ванильных растровых шрифтов (их рисует Unifont,
     * а его не сдвинуть) заменяются на {@code ?}, а курсив, подчёркивание и зачёркивание снимаются: курсив
     * перекашивается на огромное смещение глифа, а линии ascent игнорируют.
     */
    public FittedText fitAltSafe(Component text, int maxWidth) {
        List<Run> runs = flatten(text);
        List<Run> safe = new ArrayList<>(runs.size());
        for (Run run : runs) {
            safe.add(new Run(bitmapOnly(run.text), run.style.decorations(ALT_UNSAFE)));
        }
        return fit(safe, maxWidth);
    }

    private FittedText fit(List<Run> runs, int maxWidth) {
        int total = 0;
        for (Run run : runs) {
            total += measure(run.text, run.bold());
        }
        if (total <= maxWidth) {
            return new FittedText(build(runs), total, false);
        }
        return cut(runs, maxWidth);
    }

    /** Ширина строки без стилей, px. */
    public int measure(String text, boolean bold) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            width += advance(cp, bold);
            i += Character.charCount(cp);
        }
        return width;
    }

    private String bitmapOnly(String text) {
        StringBuilder out = null;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int next = i + Character.charCount(cp);
            if (!widths.isExact(cp)) {
                if (out == null) {
                    out = new StringBuilder(text.length()).append(text, 0, i);
                }
                out.appendCodePoint(REPLACEMENT);
            } else if (out != null) {
                out.appendCodePoint(cp);
            }
            i = next;
        }
        return out == null ? text : out.toString();
    }

    private int advance(int codePoint, boolean bold) {
        return widths.advance(codePoint) + (bold ? 1 : 0);
    }

    private FittedText cut(List<Run> runs, int maxWidth) {
        List<Run> kept = new ArrayList<>();
        int width = 0;
        Style lastStyle = Style.empty();
        outer:
        for (Run run : runs) {
            int ellipsis = advance(ELLIPSIS, run.bold());
            StringBuilder part = new StringBuilder();
            for (int i = 0; i < run.text.length(); ) {
                int cp = run.text.codePointAt(i);
                int w = advance(cp, run.bold());
                if (width + w + ellipsis > maxWidth) {
                    keep(kept, run.style, part);
                    lastStyle = run.style;
                    break outer;
                }
                part.appendCodePoint(cp);
                width += w;
                i += Character.charCount(cp);
            }
            keep(kept, run.style, part);
            lastStyle = run.style;
        }
        width -= trimTrailingSpaces(kept);
        String dots = new String(Character.toChars(ELLIPSIS));
        Run ellipsis = new Run(dots, lastStyle);
        width += measure(ellipsis.text, ellipsis.bold());
        Run last = kept.isEmpty() ? null : kept.get(kept.size() - 1);
        if (last != null && last.style.equals(lastStyle)) {
            kept.set(kept.size() - 1, new Run(last.text + dots, lastStyle));
        } else {
            kept.add(ellipsis);
        }
        return new FittedText(build(kept), width, true);
    }

    private static void keep(List<Run> kept, Style style, StringBuilder part) {
        if (!part.isEmpty()) {
            kept.add(new Run(part.toString(), style));
        }
    }

    private int trimTrailingSpaces(List<Run> kept) {
        int removed = 0;
        while (!kept.isEmpty()) {
            Run last = kept.get(kept.size() - 1);
            String stripped = last.text.stripTrailing();
            removed += measure(last.text.substring(stripped.length()), last.bold());
            if (!stripped.isEmpty()) {
                kept.set(kept.size() - 1, new Run(stripped, last.style));
                break;
            }
            kept.remove(kept.size() - 1);
        }
        return removed;
    }

    private static Component build(List<Run> runs) {
        if (runs.size() == 1) {
            Run only = runs.get(0);
            return Component.text(only.text, only.style);
        }
        TextComponent.Builder builder = Component.text();
        for (Run run : runs) {
            builder.append(Component.text(run.text, run.style));
        }
        return builder.build();
    }

    private static List<Run> flatten(Component text) {
        Collector collector = new Collector();
        ComponentFlattener.basic().flatten(text, collector);
        return collector.runs;
    }

    private record Run(String text, Style style) {
        boolean bold() {
            return style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
        }
    }

    /** Собирает куски, сливая стили вниз по дереву; соседние куски с одинаковым стилем склеиваются. */
    private static final class Collector implements FlattenerListener {

        private final Deque<Style> styles = new ArrayDeque<>();
        private final List<Run> runs = new ArrayList<>();

        @Override
        public void pushStyle(Style style) {
            Style parent = styles.isEmpty() ? Style.empty() : styles.peek();
            styles.push(clean(style).merge(parent, Style.Merge.Strategy.IF_ABSENT_ON_TARGET));
        }

        @Override
        public void component(String text) {
            String sanitized = sanitize(text);
            if (sanitized.isEmpty()) {
                return;
            }
            Style style = styles.isEmpty() ? Style.empty() : styles.peek();
            if (!runs.isEmpty() && runs.get(runs.size() - 1).style.equals(style)) {
                Run last = runs.remove(runs.size() - 1);
                runs.add(new Run(last.text + sanitized, style));
            } else {
                runs.add(new Run(sanitized, style));
            }
        }

        @Override
        public void popStyle(Style style) {
            styles.pop();
        }

        private static Style clean(Style style) {
            return style.font(null).clickEvent(null).hoverEvent(null).insertion(null);
        }

        private static String sanitize(String text) {
            StringBuilder out = null;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isISOControl(c)) {
                    if (out == null) {
                        out = new StringBuilder(text.length()).append(text, 0, i);
                    }
                    out.append(c == '\n' || c == '\t' ? ' ' : "");
                } else if (out != null) {
                    out.append(c);
                }
            }
            return out == null ? text : out.toString();
        }
    }
}
