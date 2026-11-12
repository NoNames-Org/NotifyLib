package ru.fell.notifylib.plugin.command;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.key.Key;
import ru.fell.notifylib.api.ToastTypes;
import ru.fell.notifylib.core.type.RegisteredType;
import ru.fell.notifylib.core.type.ToastTypeRegistryImpl;

import java.util.Locale;
import java.util.Optional;

/** Разбор и автодополнение ключей типов. {@code info} это сокращение для {@code notifylib:info}. */
final class TypeArgument {

    private static final String MINECRAFT = "minecraft";

    private TypeArgument() {
    }

    static Optional<RegisteredType> resolve(ToastTypeRegistryImpl types, Key raw) {
        Optional<RegisteredType> exact = types.lookup(raw);
        if (exact.isPresent() || !MINECRAFT.equals(raw.namespace())) {
            return exact;
        }
        return types.lookup(Key.key(ToastTypes.NAMESPACE, raw.value()));
    }

    static SuggestionProvider<CommandSourceStack> suggestions(ToastTypeRegistryImpl types) {
        return (context, builder) -> {
            String typed = builder.getRemainingLowerCase();
            for (RegisteredType type : types.registrations()) {
                Key key = type.type().key();
                String suggestion = ToastTypes.NAMESPACE.equals(key.namespace()) ? key.value() : key.asString();
                if (suggestion.toLowerCase(Locale.ROOT).startsWith(typed)) {
                    builder.suggest(suggestion);
                }
            }
            return builder.buildFuture();
        };
    }
}
