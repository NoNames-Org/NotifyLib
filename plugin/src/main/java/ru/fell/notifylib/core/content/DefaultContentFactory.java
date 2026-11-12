package ru.fell.notifylib.core.content;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.api.ToastText;
import ru.fell.notifylib.core.engine.ContentFactory;
import ru.fell.notifylib.core.engine.EngineSettings;
import ru.fell.notifylib.core.engine.ToastContent;
import ru.fell.notifylib.core.protocol.Protocol;
import ru.fell.notifylib.core.text.FittedText;
import ru.fell.notifylib.core.text.TextFitter;
import ru.fell.notifylib.core.text.TextResolver;
import ru.fell.notifylib.core.type.RegisteredType;
import ru.fell.notifylib.core.type.ToastTypeRegistryImpl;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Готовит содержимое тоста: парсит и подгоняет тексты под строку, подставляет значения по умолчанию из типа
 * и конфига.
 *
 * <p>Рассылка отдаёт один и тот же тост сотням игроков, поэтому подогнанные тексты кэшируются по входу
 * (строка MiniMessage или экземпляр компонента): парсинг и замер идут один раз. Только главный поток.
 */
public final class DefaultContentFactory implements ContentFactory {

    private static final long MILLIS_PER_TICK = 50;
    private static final int CACHE_SIZE = 64;

    private final TextResolver resolver;
    private final TextFitter fitter;
    private final ToastTypeRegistryImpl types;
    private final Supplier<EngineSettings> settings;
    // Отдельный кэш на каждый вид входа и строки, чтобы не городить составной ключ: строка MiniMessage
    // сама себе ключ (String кэширует хэш), компонент сравнивается по ссылке.
    private final Map<String, FittedText> markupShort = lru();
    private final Map<String, FittedText> markupTall = lru();
    private final Map<Identity, FittedText> richShort = lru();
    private final Map<Identity, FittedText> richTall = lru();

    public DefaultContentFactory(TextResolver resolver, TextFitter fitter, ToastTypeRegistryImpl types,
                                 Supplier<EngineSettings> settings) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.fitter = Objects.requireNonNull(fitter, "fitter");
        this.types = Objects.requireNonNull(types, "types");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public @Nullable ToastContent create(UUID recipient, Toast toast) {
        Optional<RegisteredType> type = types.lookup(toast.type().key());
        if (type.isEmpty()) {
            return null;
        }
        ToastText titleText = toast.title();
        boolean tall = titleText != null;
        FittedText title = tall ? fit(titleText, true) : null;
        return new ToastContent(type.get(), fit(toast.text(), tall), title, durationTicks(toast),
                sound(toast, type.get()));
    }

    @Override
    public ToastContent withText(UUID recipient, ToastContent current, ToastText text) {
        return new ToastContent(current.type(), fit(text, current.tall()), current.title(),
                current.durationTicks(), current.sound());
    }

    /** Переводит длительность в тики и обрезает до того, что влезает в полоску таймера. */
    public static int toTicks(Duration duration) {
        long ticks = Math.round(duration.toMillis() / (double) MILLIS_PER_TICK);
        return (int) Math.max(1, Math.min(Protocol.MAX_DURATION_TICKS, ticks));
    }

    /**
     * Строки двухстрочного тоста может рисовать сдвинутая копия стандартного шрифта. В ней только ванильные
     * растровые глифы и нет декораций, которые переживут сдвиг по вертикали, поэтому такой текст чистится.
     */
    private FittedText fit(ToastText text, boolean tall) {
        if (text instanceof ToastText.Markup markup) {
            return (tall ? markupTall : markupShort)
                    .computeIfAbsent(markup.miniMessage(), input -> measure(resolver.parse(input), tall));
        }
        Component component = ((ToastText.Rich) text).component();
        return (tall ? richTall : richShort)
                .computeIfAbsent(new Identity(component), key -> measure(component, tall));
    }

    private FittedText measure(Component component, boolean tall) {
        return tall
                ? fitter.fitAltSafe(component, Protocol.TEXT_MAX_WIDTH)
                : fitter.fit(component, Protocol.TEXT_MAX_WIDTH);
    }

    private static <K> Map<K, FittedText> lru() {
        return new LinkedHashMap<>(CACHE_SIZE * 2, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, FittedText> eldest) {
                return size() > CACHE_SIZE;
            }
        };
    }

    private int durationTicks(Toast toast) {
        if (toast.persistent()) {
            return 0;
        }
        Duration duration = toast.duration();
        return duration == null ? settings.get().defaultDurationTicks() : toTicks(duration);
    }

    private static @Nullable Sound sound(Toast toast, RegisteredType type) {
        if (toast.silent()) {
            return null;
        }
        Sound explicit = toast.sound();
        return explicit != null ? explicit : type.type().sound();
    }

    /** Сравнение компонентов по ссылке: equals обходил бы всё дерево. */
    private static final class Identity {
        private final Component component;

        Identity(Component component) {
            this.component = component;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Identity identity && identity.component == component;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(component);
        }
    }
}
