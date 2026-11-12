package ru.fell.notifylib.plugin.paper;

import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;
import ru.fell.notifylib.core.type.CallerResolver;

import java.util.Optional;

/**
 * Определяет, какой плагин вызвал API: идёт по стеку до первого фрейма вне NotifyLib, чей класс загружен
 * загрузчиком плагина. Результат кэшируется на класс.
 */
public final class PaperCallerResolver implements CallerResolver {

    private static final int MAX_FRAMES = 24;
    private static final String[] OWN_PACKAGES = {
            "ru.fell.notifylib.api.", "ru.fell.notifylib.core.", "ru.fell.notifylib.plugin."};

    private final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private final ClassValue<Optional<String>> owners = new ClassValue<>() {
        @Override
        protected Optional<String> computeValue(Class<?> type) {
            try {
                return Optional.of(JavaPlugin.getProvidingPlugin(type).getName());
            } catch (IllegalArgumentException | IllegalStateException e) {
                return Optional.empty();
            }
        }
    };

    @Override
    public @Nullable String callingPlugin() {
        return walker.walk(frames -> frames.limit(MAX_FRAMES)
                .map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> !isOwn(type.getName()))
                .map(owners::get)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(null));
    }

    private static boolean isOwn(String className) {
        for (String own : OWN_PACKAGES) {
            if (className.startsWith(own)) {
                return true;
            }
        }
        return false;
    }
}
