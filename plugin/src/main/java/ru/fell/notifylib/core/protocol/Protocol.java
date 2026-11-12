package ru.fell.notifylib.core.protocol;

/**
 * Контракт между сервером и шейдером {@code toast.glsl} из ресурспака. Каждая константа, которую читает
 * шейдер, лежит там же с префиксом {@code T_} в блоке "Generated from Protocol" файла
 * {@code resourcepack/assets/minecraft/shaders/include/toast.glsl} (и его копий в оверлеях).
 * <b>Меняй обе стороны одновременно</b>, иначе тосты разъедутся по экрану.
 *
 * <h2>Как кодируется тост</h2>
 * Тост это имя белого боссбара. Суммарная ширина имени нулевая (перо возвращается назад пробелами из
 * {@code notifylib:toast}), поэтому игра ставит его в {@code floor(guiWidth / 2)}.
 * <ul>
 *   <li><b>Полоса по x.</b> Каждый элемент сдвинут вправо на {@code (BAND_BASE + code) * X_STEP} px. Шейдер
 *       восстанавливает {@code code} и переносит элемент в угол. У обычного текста полоса 0.</li>
 *   <li><b>Полоса по y.</b> Глифы шрифтов {@code notifylib} могут быть опущены на {@code flags * Y_STEP} px
 *       через ascent: {@link #Y_FLAG_TIMER} помечает полоску таймера (в её цвете лежат данные о времени),
 *       {@link #Y_FLAG_ALT} помечает вторую строку двухстрочного тоста.</li>
 * </ul>
 * Все координаты целые и меньше 2^23, так что float32 в шейдере считает их без потерь.
 */
public final class Protocol {

    // ---- полосы по x ----
    public static final int X_STEP = 256;
    /** Насколько элемент может залезть левее начала тоста (наклон курсива). */
    public static final int X_SLACK = 32;
    /** Первая полоса тостов; всё ниже это обычный текст. */
    public static final int BAND_BASE = 32;
    public static final int CODE_BITS = 14;
    public static final int CODES = 1 << CODE_BITS;

    // ---- полосы по y ----
    public static final int Y_STEP = 4096;
    /** Глиф это полоска таймера, его RGB это данные. */
    public static final int Y_FLAG_TIMER = 1;
    /** Глиф из второй строки двухстрочного тоста. */
    public static final int Y_FLAG_ALT = 2;

    // ---- битовые поля кода ----
    /** Угол, 2 бита. */
    public static final int SHIFT_CORNER = 0;
    /** Фаза шейдера, 2 бита. */
    public static final int SHIFT_PHASE = 2;
    /** Игровой тик начала движения (mod 16), 4 бита. */
    public static final int SHIFT_TICK = 4;
    /** Слот в стопке, 2 бита. */
    public static final int SHIFT_SLOT = 8;
    /** Сколько двухстрочных тостов между этим и углом, 2 бита. */
    public static final int SHIFT_TALL_BEFORE = 10;
    /** Направление сдвига: 0 к углу, 1 от угла. */
    public static final int SHIFT_SHIFT_AWAY = 12;
    /** 1, если сдвиг проходит ещё и через доп. высоту двухстрочного тоста. */
    public static final int SHIFT_SHIFT_TALL = 13;

    // ---- раскладка, px GUI ----
    public static final int WIDTH = 160;
    /** Высота однострочного тоста. Все вершины должны влезать в одну строку боссбара (19 px). */
    public static final int HEIGHT = 18;
    /** Добавка к высоте у двухстрочного тоста. */
    public static final int TALL_EXTRA = 10;
    /** От верха тоста до верха строки текста боссбара. */
    public static final int TOP = 5;
    /** Зазор между тостами в стопке. */
    public static final int GAP = 3;
    /** Отступ от краёв экрана. */
    public static final int MARGIN = 4;
    public static final int ICON_X = 5;
    public static final int ICON_Y = 4;
    public static final int TEXT_X = 16;
    public static final int TEXT_RIGHT_PAD = 6;
    public static final int TEXT_MAX_WIDTH = WIDTH - TEXT_X - TEXT_RIGHT_PAD;
    public static final int BAR_X = 4;
    public static final int BAR_Y = 16;
    public static final int BAR_WIDTH = 152;
    public static final int BAR_HEIGHT = 2;

    // ---- ванильный рендер текста ----
    /** Верх глифа это {@code lineTop + FONT_BASELINE - ascent}. */
    public static final int FONT_BASELINE = 7;
    /** i-е имя боссбара рисуется на {@code y = BOSSBAR_FIRST + BOSSBAR_STEP * i}. */
    public static final int BOSSBAR_FIRST = 3;
    public static final int BOSSBAR_STEP = 19;

    // ---- анимация ----
    /** Сколько тиков шейдер двигает тост. */
    public static final int ANIM_TICKS = 6;
    /** Через сколько тиков сервер переключает фазу (должно быть меньше {@link #ANIM_EARLY}). */
    public static final int PHASE_TICKS = 8;
    /** С этого числа тиков от старта (mod 16) метка считается "из будущего", то есть движение ещё не началось. */
    public static final int ANIM_EARLY = 13;
    /** Метки времени движения берутся по этому модулю. */
    public static final int TICK_MOD = 16;
    /** На сколько скрытый тост уезжает за экран. */
    public static final int HIDE = WIDTH + MARGIN + 8;

    // ---- таймер ----
    /** Длина игровых суток: здесь переполняется uniform {@code GameTime}. */
    public static final int DAY = 24000;
    /** Сколько бит цвета таймера занимает {@code duration / DURATION_UNIT}. */
    public static final int DURATION_BITS = 9;
    /** Шаг кодируемой длительности, тики. */
    public static final int DURATION_UNIT = 2;
    public static final int MAX_DURATION_TICKS = ((1 << DURATION_BITS) - 1) * DURATION_UNIT;
    /** Старт таймера "в будущем" не дальше этого считается только что начатым (часы клиента отстают). */
    public static final int FUTURE_GUARD = 200;

    // ---- прочее ----
    /** Набор пробелов: ±2^0 .. ±2^(SPACE_BITS-1). */
    public static final int SPACE_BITS = 24;
    /** Advance панелей в паке: картинка 160 px + 1 px зазора. */
    public static final int PANEL_ADVANCE = WIDTH + 1;
    /** Максимум тостов на экране у игрока (слот 2 бита). */
    public static final int MAX_SLOTS = 4;

    private Protocol() {
    }

    /** Смещение строк тоста от угла без учёта анимации, px. */
    public static int stackOffset(int slot, int tallBefore) {
        return slot * (HEIGHT + GAP) + tallBefore * TALL_EXTRA;
    }

    /**
     * Ascent, при котором верх глифа окажется на {@code localTop} внутри тоста.
     *
     * @param localTop px от верха тоста (до сдвига второй строки)
     * @param yFlags   комбинация {@link #Y_FLAG_TIMER} и {@link #Y_FLAG_ALT}
     */
    public static int ascentFor(int localTop, int yFlags) {
        return FONT_BASELINE + TOP - localTop - yFlags * Y_STEP;
    }
}
