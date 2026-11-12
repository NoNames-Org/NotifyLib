package ru.fell.notifylib.api;

import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * Неизменяемое описание тоста. Создаётся через {@link #builder()}; один экземпляр можно показывать
 * сколько угодно игрокам.
 */
public final class Toast {

    public static final Duration MIN_DURATION = Duration.ofSeconds(1);
    /** Полоска таймера кодирует не больше 1022 тиков, отсюда предел. */
    public static final Duration MAX_DURATION = Duration.ofSeconds(51);
    /** Максимальная длина {@linkplain Builder#replaceKey(String) ключа замены}. */
    public static final int MAX_REPLACE_KEY_LENGTH = 64;

    private final ToastType type;
    private final ToastText text;
    private final @Nullable ToastText title;
    private final @Nullable Duration duration;
    private final boolean persistent;
    private final @Nullable String replaceKey;
    private final Priority priority;
    private final @Nullable Overflow overflow;
    private final @Nullable Sound sound;
    private final boolean silent;

    private Toast(Builder builder, ToastText text) {
        this.type = builder.type;
        this.text = text;
        this.title = builder.title;
        this.duration = builder.duration;
        this.persistent = builder.persistent;
        this.replaceKey = builder.replaceKey;
        this.priority = builder.priority;
        this.overflow = builder.overflow;
        this.sound = builder.sound;
        this.silent = builder.silent;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Билдер, заполненный значениями этого тоста. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.type = type;
        builder.text = text;
        builder.title = title;
        builder.duration = duration;
        builder.persistent = persistent;
        builder.replaceKey = replaceKey;
        builder.priority = priority;
        builder.overflow = overflow;
        builder.sound = sound;
        builder.silent = silent;
        return builder;
    }

    /** Тип: иконка, цвет полоски и звук по умолчанию. */
    public ToastType type() {
        return type;
    }

    /** Основная строка текста. */
    public ToastText text() {
        return text;
    }

    /** Заголовок двухстрочного тоста или {@code null} для однострочного. */
    public @Nullable ToastText title() {
        return title;
    }

    /**
     * Время показа или {@code null}: тогда берётся значение из конфига (у постоянного тоста тоже {@code null}).
     */
    public @Nullable Duration duration() {
        return duration;
    }

    /** {@code true}, если у тоста нет таймера и он висит до явного закрытия. */
    public boolean persistent() {
        return persistent;
    }

    /** Ключ замены: новый тост с тем же ключом обновляет видимый на месте. Может быть {@code null}. */
    public @Nullable String replaceKey() {
        return replaceKey;
    }

    /** Приоритет в очереди. */
    public Priority priority() {
        return priority;
    }

    /** Поведение при заполненном стеке или {@code null}, если берётся из конфига. */
    public @Nullable Overflow overflow() {
        return overflow;
    }

    /** Звук при показе или {@code null}, если играет звук типа. */
    public @Nullable Sound sound() {
        return sound;
    }

    /** {@code true}, если звук не играет вообще, даже звук типа. */
    public boolean silent() {
        return silent;
    }

    @Override
    public String toString() {
        return "Toast{type=" + type.key().asString() + ", text=" + text + ", title=" + title
                + ", duration=" + duration + ", persistent=" + persistent + ", replaceKey=" + replaceKey
                + ", priority=" + priority + ", overflow=" + overflow + ", silent=" + silent + '}';
    }

    /** Билдер {@link Toast}. Не потокобезопасен. */
    public static final class Builder {

        private ToastType type = ToastTypes.INFO;
        private @Nullable ToastText text;
        private @Nullable ToastText title;
        private @Nullable Duration duration;
        private boolean persistent;
        private @Nullable String replaceKey;
        private Priority priority = Priority.NORMAL;
        private @Nullable Overflow overflow;
        private @Nullable Sound sound;
        private boolean silent;

        private Builder() {
        }

        /** По умолчанию {@link ToastTypes#INFO}. */
        public Builder type(ToastType type) {
            this.type = Objects.requireNonNull(type, "type");
            return this;
        }

        public Builder text(Component text) {
            this.text = ToastText.of(text);
            return this;
        }

        /** Текст в формате MiniMessage. */
        public Builder text(String miniMessage) {
            this.text = ToastText.miniMessage(miniMessage);
            return this;
        }

        public Builder text(ToastText text) {
            this.text = Objects.requireNonNull(text, "text");
            return this;
        }

        /** Задаёт заголовок, тост становится двухстрочным (заголовок над текстом). */
        public Builder title(Component title) {
            this.title = ToastText.of(title);
            return this;
        }

        /** Заголовок в формате MiniMessage, тост становится двухстрочным. */
        public Builder title(String miniMessage) {
            this.title = ToastText.miniMessage(miniMessage);
            return this;
        }

        /** Задаёт заголовок; {@code null} делает тост однострочным. */
        public Builder title(@Nullable ToastText title) {
            this.title = title;
            return this;
        }

        /**
         * Задаёт время показа и снимает {@linkplain #persistent() постоянство}.
         *
         * @param duration от {@link #MIN_DURATION} до {@link #MAX_DURATION}
         * @throws IllegalArgumentException если значение вне диапазона
         */
        public Builder duration(Duration duration) {
            Objects.requireNonNull(duration, "duration");
            if (duration.compareTo(MIN_DURATION) < 0 || duration.compareTo(MAX_DURATION) > 0) {
                throw new IllegalArgumentException("длительность уведомления должна быть от " + MIN_DURATION.toSeconds() + " до "
                        + MAX_DURATION.toSeconds() + " секунд, получено " + duration.toMillis() + " мс");
            }
            this.duration = duration;
            this.persistent = false;
            return this;
        }

        /** Делает тост постоянным: без полоски таймера, висит до {@link ToastHandle#dismiss()}. */
        public Builder persistent() {
            this.persistent = true;
            this.duration = null;
            return this;
        }

        /**
         * Задаёт ключ замены. Если тост с таким ключом ещё виден, он получает новый текст и перезапущенный
         * таймер вместо появления второго тоста.
         *
         * @param replaceKey непустой ключ до {@link #MAX_REPLACE_KEY_LENGTH} символов или {@code null}
         * @throws IllegalArgumentException если ключ пустой или слишком длинный
         */
        public Builder replaceKey(@Nullable String replaceKey) {
            if (replaceKey != null && (replaceKey.isBlank() || replaceKey.length() > MAX_REPLACE_KEY_LENGTH)) {
                throw new IllegalArgumentException("ключ замены должен быть от 1 до " + MAX_REPLACE_KEY_LENGTH + " символов");
            }
            this.replaceKey = replaceKey;
            return this;
        }

        /** По умолчанию {@link Priority#NORMAL}. */
        public Builder priority(Priority priority) {
            this.priority = Objects.requireNonNull(priority, "priority");
            return this;
        }

        /** Что делать, если стек игрока заполнен; {@code null} означает значение из конфига. */
        public Builder overflow(@Nullable Overflow overflow) {
            this.overflow = overflow;
            return this;
        }

        /** Звук при показе вместо звука типа; {@code null} возвращает звук типа. */
        public Builder sound(@Nullable Sound sound) {
            this.sound = sound;
            this.silent = false;
            return this;
        }

        /** Отключает звук этого тоста. */
        public Builder silent() {
            this.silent = true;
            this.sound = null;
            return this;
        }

        /** @throws IllegalStateException если текст не задан */
        public Toast build() {
            ToastText current = text;
            if (current == null) {
                throw new IllegalStateException("текст уведомления не задан");
            }
            return new Toast(this, current);
        }
    }
}
