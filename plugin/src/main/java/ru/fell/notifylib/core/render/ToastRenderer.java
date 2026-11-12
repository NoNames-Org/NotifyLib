package ru.fell.notifylib.core.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.core.protocol.Glyphs;
import ru.fell.notifylib.core.protocol.Protocol;
import ru.fell.notifylib.core.protocol.StackPosition;
import ru.fell.notifylib.core.protocol.TimerColor;
import ru.fell.notifylib.core.protocol.ToastCode;
import ru.fell.notifylib.core.protocol.ToastLine;
import ru.fell.notifylib.core.text.FittedText;
import ru.fell.notifylib.core.type.RegisteredType;

/**
 * Превращает {@link ToastFrame} в имя боссбара. Держит кэши, поэтому живёт в одном потоке (потоке боссбаров).
 *
 * <p>Два кэша не дают рисовать тост повторно:
 * <ul>
 *   <li><b>тела</b>: панель, иконка, таймер и тексты зависят только от содержимого и таймера, а не от фазы.
 *       Тост рисуется один раз, а каждая фаза (IN, STAY, SHIFT, OUT) лишь оборачивает тело в смещение полосы;</li>
 *   <li><b>кадры</b>: рассылка за один тик выставляет сотням боссбаров одинаковые кадры.</li>
 * </ul>
 *
 * <p>Двухстрочный тост оставляет основной блок 18 px на позиции в стопке и растёт от угла на
 * {@link Protocol#TALL_EXTRA}. В верхних углах вторая строка снизу (заголовок на основной, текст на второй),
 * в нижних вторая строка сверху, и в ней заголовок.
 */
public final class ToastRenderer {

    private static final int BAR_ADVANCE = Protocol.BAR_WIDTH + 1;
    private static final int FRAME_CACHE_SIZE = 8;
    // Прямое отображение: ячейка на хэш, при коллизии просто перерисуем. Должна быть степенью двойки.
    private static final int BODY_CACHE_SIZE = 256;
    private static final int NO_TIMER = -1;

    private final int panelAdvance;
    private final ToastFrame[] cachedFrames = new ToastFrame[FRAME_CACHE_SIZE];
    private final Component[] cachedNames = new Component[FRAME_CACHE_SIZE];
    private int frameNext;
    private final Body[] bodies = new Body[BODY_CACHE_SIZE];

    /** Нарисованный тост и ключ, под который он рисовался. Тексты и тип общие, сравниваются по ссылке. */
    private record Body(RegisteredType type, FittedText text, @Nullable FittedText title, boolean top, int timer,
                        Component component, int pen) {

        boolean matches(RegisteredType type, FittedText text, @Nullable FittedText title, boolean top, int timer) {
            return this.type == type && this.text == text && this.title == title && this.top == top
                    && this.timer == timer;
        }
    }

    /** @param panelAdvance advance глифов панели на клиенте */
    public ToastRenderer(int panelAdvance) {
        this.panelAdvance = panelAdvance;
    }

    public Component render(ToastFrame frame) {
        for (int i = 0; i < FRAME_CACHE_SIZE; i++) {
            ToastFrame cached = cachedFrames[i];
            if (cached != null && sameFrame(cached, frame)) {
                return cachedNames[i];
            }
        }
        Body body = body(frame);
        Component name = ToastLine.name(code(frame).bandOffset(), body.component(), body.pen());
        cachedFrames[frameNext] = frame;
        cachedNames[frameNext] = name;
        frameNext = (frameNext + 1) % FRAME_CACHE_SIZE;
        return name;
    }

    // Тексты сравниваются по ссылке: они общие из кэша содержимого.
    private static boolean sameFrame(ToastFrame a, ToastFrame b) {
        return a.corner() == b.corner() && a.phase() == b.phase() && a.phaseTick() == b.phaseTick()
                && a.position() == b.position() && a.shiftFrom() == b.shiftFrom() && a.type() == b.type()
                && a.text() == b.text() && a.title() == b.title() && timerValue(a) == timerValue(b);
    }

    private static ToastCode code(ToastFrame frame) {
        StackPosition from = frame.shiftFrom();
        return from != null
                ? ToastCode.shift(frame.corner(), frame.phaseTick(), from, frame.position())
                : ToastCode.of(frame.corner(), frame.phase(), frame.phaseTick(), frame.position());
    }

    private Body body(ToastFrame frame) {
        RegisteredType type = frame.type();
        FittedText text = frame.text();
        FittedText title = frame.title();
        // Угол важен только двухстрочным: от него зависит, какая строка вторая.
        boolean top = title == null || frame.corner().top();
        int timer = timerValue(frame);
        int hash = System.identityHashCode(text) * 31 + System.identityHashCode(title);
        hash = hash * 31 + timer + (top ? 1 : 0);
        int slot = (hash ^ hash >>> 16) & BODY_CACHE_SIZE - 1;
        Body cached = bodies[slot];
        if (cached != null && cached.matches(type, text, title, top, timer)) {
            return cached;
        }
        ToastLine line = new ToastLine();
        draw(line, type, text, title, timer, top);
        Body drawn = new Body(type, text, title, top, timer, line.buildBody(), line.pen());
        bodies[slot] = drawn;
        return drawn;
    }

    private static int timerValue(ToastFrame frame) {
        ToastFrame.Timer timer = frame.timer();
        return timer == null ? NO_TIMER : TimerColor.value(timer.startGameTime(), timer.durationTicks());
    }

    private void draw(ToastLine line, RegisteredType type, FittedText text, @Nullable FittedText title, int timer,
                      boolean top) {
        if (title == null) {
            line.moveTo(0).glyph(Glyphs.PANEL, panelAdvance);
            line.moveTo(Protocol.ICON_X).glyph(type.iconGlyph(false), type.iconAdvance());
            bar(line, type, timer, false);
            line.moveTo(Protocol.TEXT_X).text(text, Glyphs.FONT_DEFAULT);
        } else if (top) {
            line.moveTo(0).glyph(Glyphs.PANEL_TALL_TOP_MAIN, panelAdvance);
            line.moveTo(0).glyph(Glyphs.PANEL_TALL_TOP_ALT, panelAdvance);
            line.moveTo(Protocol.ICON_X).glyph(type.iconGlyph(false), type.iconAdvance());
            bar(line, type, timer, true);
            line.moveTo(Protocol.TEXT_X).text(title, Glyphs.FONT_DEFAULT);
            line.moveTo(Protocol.TEXT_X).text(text, Glyphs.FONT_ALT);
        } else {
            line.moveTo(0).glyph(Glyphs.PANEL_TALL_BOTTOM_ALT, panelAdvance);
            line.moveTo(0).glyph(Glyphs.PANEL_TALL_BOTTOM_MAIN, panelAdvance);
            line.moveTo(Protocol.ICON_X).glyph(type.iconGlyph(true), type.iconAdvance());
            bar(line, type, timer, false);
            line.moveTo(Protocol.TEXT_X).text(title, Glyphs.FONT_ALT);
            line.moveTo(Protocol.TEXT_X).text(text, Glyphs.FONT_DEFAULT);
        }
    }

    private static void bar(ToastLine line, RegisteredType type, int timer, boolean alt) {
        if (timer == NO_TIMER) {
            return;
        }
        line.moveTo(Protocol.BAR_X).glyph(type.barGlyph(alt), BAR_ADVANCE, TextColor.color(timer));
    }
}
