package ru.fell.notifylib.plugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.fell.notifylib.api.Toast;
import ru.fell.notifylib.core.service.OnlinePlayers;
import ru.fell.notifylib.core.service.ToastApiImpl;
import ru.fell.notifylib.core.type.RegisteredType;
import ru.fell.notifylib.core.type.ToastTypeRegistryImpl;
import ru.fell.notifylib.plugin.config.Messages;

import java.time.Duration;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/** {@code /notify send|types|reload}. Личные настройки игроков меняются только через API. */
public final class ToastCommand {

    static final String PERM_SEND = "notifylib.send";
    static final String PERM_ADMIN = "notifylib.admin";
    private static final String COMMAND_OWNER = "NotifyLib";
    private static final double MIN_SECONDS = 1;
    private static final double MAX_SECONDS = 51;
    /** Пишется в чате буквально ("\n") и делит текст на заголовок и вторую строку. */
    private static final String LINE_BREAK = "\\n";

    private final ToastApiImpl api;
    private final ToastTypeRegistryImpl types;
    private final OnlinePlayers online;
    private final Supplier<Messages> messages;
    private final Runnable reload;

    public ToastCommand(ToastApiImpl api, ToastTypeRegistryImpl types, OnlinePlayers online,
                        Supplier<Messages> messages, Runnable reload) {
        this.api = api;
        this.types = types;
        this.online = online;
        this.messages = messages;
        this.reload = reload;
    }

    public LiteralCommandNode<CommandSourceStack> node() {
        return Commands.literal("notify")
                .requires(source -> has(source, PERM_SEND) || has(source, PERM_ADMIN))
                .then(Commands.literal("send")
                        .requires(source -> has(source, PERM_SEND))
                        .then(sendChain(Commands.literal("*"), context -> online.snapshot()))
                        .then(sendChain(Commands.argument("targets", ArgumentTypes.players()), this::targets)))
                .then(Commands.literal("types")
                        .requires(source -> has(source, PERM_ADMIN))
                        .executes(this::listTypes))
                .then(Commands.literal("reload")
                        .requires(source -> has(source, PERM_ADMIN))
                        .executes(this::reload))
                .build();
    }

    private <B extends ArgumentBuilder<CommandSourceStack, B>> B sendChain(B target, Targets targets) {
        return target.then(Commands.argument("type", ArgumentTypes.key())
                .suggests(TypeArgument.suggestions(types))
                .then(Commands.argument("seconds", DoubleArgumentType.doubleArg(MIN_SECONDS, MAX_SECONDS))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(context -> send(context, targets.resolve(context))))));
    }

    private int send(CommandContext<CommandSourceStack> context, Collection<? extends Player> players) {
        CommandSender sender = context.getSource().getSender();
        Key raw = context.getArgument("type", Key.class);
        Optional<RegisteredType> type = TypeArgument.resolve(types, raw);
        if (type.isEmpty()) {
            sender.sendMessage(messages.get().prefixed("unknown-type", Placeholder.unparsed("type", raw.asString())));
            return 0;
        }
        if (players.isEmpty()) {
            sender.sendMessage(messages.get().prefixed("send-no-targets"));
            return 0;
        }
        Toast toast = buildToast(type.get(), DoubleArgumentType.getDouble(context, "seconds"),
                StringArgumentType.getString(context, "text"));
        for (Player player : players) {
            api.show(player, toast, COMMAND_OWNER);
        }
        sender.sendMessage(messages.get().prefixed("send-done", Placeholder.unparsed("count", Integer.toString(players.size()))));
        return Command.SINGLE_SUCCESS;
    }

    private static Toast buildToast(RegisteredType type, double seconds, String text) {
        Toast.Builder builder = Toast.builder()
                .type(type.type())
                .duration(Duration.ofMillis(Math.round(seconds * 1000)));
        int split = text.indexOf(LINE_BREAK);
        String title = split < 0 ? "" : text.substring(0, split).strip();
        String body = split < 0 ? text : text.substring(split + LINE_BREAK.length()).strip();
        if (title.isEmpty() || body.isEmpty()) {
            builder.text(title.isEmpty() ? body : title);
        } else {
            // "верх\nниз": первая строка становится заголовком двухстрочного уведомления
            builder.title(title).text(body);
        }
        return builder.build();
    }

    private Collection<? extends Player> targets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getArgument("targets", PlayerSelectorArgumentResolver.class).resolve(context.getSource());
    }

    private int listTypes(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Messages current = messages.get();
        Collection<RegisteredType> all = types.registrations();
        sender.sendMessage(current.prefixed("types-header", Placeholder.unparsed("count", Integer.toString(all.size()))));
        for (RegisteredType type : all) {
            sender.sendMessage(current.raw("types-line",
                    Placeholder.unparsed("key", type.type().key().asString()),
                    Placeholder.component("bar", Component.text("■", type.type().barColor())),
                    Placeholder.unparsed("glyph", "U+" + Integer.toHexString(type.glyphBase()).toUpperCase(Locale.ROOT))));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        reload.run();
        context.getSource().getSender().sendMessage(messages.get().prefixed("reload-done"));
        return Command.SINGLE_SUCCESS;
    }

    private static boolean has(CommandSourceStack source, String permission) {
        return source.getSender().hasPermission(permission);
    }

    @FunctionalInterface
    private interface Targets {
        Collection<? extends Player> resolve(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }
}
