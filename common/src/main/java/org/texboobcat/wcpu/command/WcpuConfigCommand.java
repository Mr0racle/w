package org.texboobcat.wcpu.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import org.texboobcat.wcpu.ConfigBridge;
import org.texboobcat.wcpu.Lang;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

// /wcpu config: every setting that used to live only in the config file, with its description.
//   /wcpu config                    list all settings (hover for details, click to edit)
//   /wcpu config <setting>          describe one setting
//   /wcpu config <setting> <value>  change and save it
//   /wcpu config reset <setting>    back to the default
final class WcpuConfigCommand {

    private static final String SETTING = "setting";
    private static final String VALUE = "value";

    private WcpuConfigCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("config")
            .executes(ctx -> list(ctx.getSource()))
            .then(Commands.literal("reset")
                .then(Commands.argument(SETTING, StringArgumentType.word())
                    .suggests(WcpuConfigCommand::suggestSettings)
                    .executes(ctx -> reset(ctx, StringArgumentType.getString(ctx, SETTING)))))
            .then(Commands.argument(SETTING, StringArgumentType.word())
                .suggests(WcpuConfigCommand::suggestSettings)
                .executes(ctx -> show(ctx.getSource(), StringArgumentType.getString(ctx, SETTING)))
                .then(Commands.argument(VALUE, StringArgumentType.greedyString())
                    .suggests(WcpuConfigCommand::suggestValues)
                    .executes(ctx -> set(ctx, StringArgumentType.getString(ctx, SETTING),
                        StringArgumentType.getString(ctx, VALUE)))));
    }

    // /wcpu language [en|ru]: the language of this command's output, stored as general.language.
    static LiteralArgumentBuilder<CommandSourceStack> languageNode() {
        return Commands.literal("language")
            .executes(ctx -> showLanguage(ctx.getSource()))
            .then(Commands.argument("code", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    builder.suggest(Lang.ENGLISH, Component.literal("English"));
                    builder.suggest(Lang.RUSSIAN, Component.literal("Русский"));
                    return builder.buildFuture();
                })
                .executes(ctx -> setLanguage(ctx.getSource(),
                    StringArgumentType.getString(ctx, "code"))));
    }

    private static int showLanguage(CommandSourceStack source) {
        source.sendSuccess(() -> text(Lang.t("Language") + ": ", ChatFormatting.DARK_AQUA)
            .append(text(Lang.code(), ChatFormatting.GREEN)), false);
        source.sendSuccess(() -> text(Lang.t("Change: /wcpu language <en|ru>"),
            ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    private static int setLanguage(CommandSourceStack source, String code) {
        if (!Lang.supported(code)) {
            source.sendFailure(Component.literal(Lang.f("Unknown language: %s (use en or ru)", code)));
            return 0;
        }
        ConfigBridge bridge = ConfigBridge.installed();
        String error = bridge == null ? null : bridge.set("general.language", code.toLowerCase(java.util.Locale.ROOT));
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        if (bridge == null) {
            Lang.set(code);
        }
        source.sendSuccess(() -> text(Lang.f("Language set to %s.", Lang.code()), ChatFormatting.GREEN), true);
        return 1;
    }

    // ---- reading ----------------------------------------------------------------------------

    private static Optional<ConfigBridge.Entry> find(String path) {
        ConfigBridge bridge = ConfigBridge.installed();
        return bridge == null ? Optional.empty()
            : bridge.entries().stream().filter(entry -> entry.path().equals(path)).findFirst();
    }

    private static int list(CommandSourceStack source) {
        ConfigBridge bridge = ConfigBridge.installed();
        if (bridge == null) {
            source.sendFailure(Component.literal(Lang.t("Settings are not available on this loader.")));
            return 0;
        }
        source.sendSuccess(() -> text("wcpu ", ChatFormatting.GOLD, ChatFormatting.BOLD)
            .append(text(Lang.t("» settings (hover for details, click to change)"), ChatFormatting.GRAY)), false);
        List<ConfigBridge.Entry> entries = bridge.entries().stream()
            .sorted(Comparator.comparing(ConfigBridge.Entry::path)).toList();
        String section = "";
        for (ConfigBridge.Entry entry : entries) {
            String head = entry.path().contains(".")
                ? entry.path().substring(0, entry.path().indexOf('.')) : "";
            if (!head.equals(section)) {
                section = head;
                MutableComponent title = text("[" + head + "]", ChatFormatting.DARK_AQUA);
                source.sendSuccess(() -> title, false);
            }
            source.sendSuccess(() -> row(entry), false);
        }
        return entries.size();
    }

    private static MutableComponent row(ConfigBridge.Entry entry) {
        MutableComponent hover = text(entry.path() + "\n", ChatFormatting.GOLD)
            .append(text(entry.description() + "\n", ChatFormatting.GRAY))
            .append(text(Lang.t("default: ") + entry.defaultValue(), ChatFormatting.DARK_GRAY));
        if (!entry.range().isEmpty()) {
            hover.append(text(Lang.t("  range: ") + entry.range(), ChatFormatting.DARK_GRAY));
        }
        String command = "/wcpu config " + entry.path() + " ";
        return text("  " + shortName(entry.path()), ChatFormatting.AQUA)
            .append(text(" = ", ChatFormatting.DARK_GRAY))
            .append(text(entry.value(), valueColor(entry)))
            .withStyle(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)));
    }

    private static int show(CommandSourceStack source, String path) {
        Optional<ConfigBridge.Entry> found = find(path);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal(Lang.f("Unknown setting: %s (see /wcpu config)", path)));
            return 0;
        }
        ConfigBridge.Entry entry = found.get();
        source.sendSuccess(() -> text(entry.path(), ChatFormatting.GOLD, ChatFormatting.BOLD), false);
        for (String line : entry.description().split("\n")) {
            source.sendSuccess(() -> text(line, ChatFormatting.GRAY), false);
        }
        source.sendSuccess(() -> text(Lang.t("Current: "), ChatFormatting.DARK_AQUA)
            .append(text(entry.value(), valueColor(entry)))
            .append(text(Lang.t("   Default: ") + entry.defaultValue(), ChatFormatting.DARK_GRAY))
            .append(text(entry.range().isEmpty() ? "" : Lang.t("   Range: ") + entry.range(),
                ChatFormatting.DARK_GRAY)), false);
        if (entry.needsRestart()) {
            source.sendSuccess(() -> text(Lang.t("Saved, but takes effect only after the server is restarted."),
                ChatFormatting.YELLOW), false);
        }
        source.sendSuccess(() -> text(Lang.f("Change: /wcpu config %s <value>", entry.path()),
            ChatFormatting.DARK_GRAY), false);
        return 1;
    }

    // ---- writing ----------------------------------------------------------------------------

    private static int set(CommandContext<CommandSourceStack> ctx, String path, String raw) {
        return apply(ctx.getSource(), path, ConfigBridge.installed() == null ? "no bridge"
            : ConfigBridge.installed().set(path, raw));
    }

    private static int reset(CommandContext<CommandSourceStack> ctx, String path) {
        return apply(ctx.getSource(), path, ConfigBridge.installed() == null ? "no bridge"
            : ConfigBridge.installed().reset(path));
    }

    private static int apply(CommandSourceStack source, String path, String error) {
        if (error != null) {
            source.sendFailure(Component.literal(error));
            return 0;
        }
        Optional<ConfigBridge.Entry> now = find(path);
        source.sendSuccess(() -> text(path, ChatFormatting.AQUA)
            .append(text(" = ", ChatFormatting.DARK_GRAY))
            .append(text(now.map(ConfigBridge.Entry::value).orElse("?"), ChatFormatting.GREEN))
            .append(text(Lang.t("  (saved)"), ChatFormatting.DARK_GRAY)), true);
        if (now.isPresent() && now.get().needsRestart()) {
            source.sendSuccess(() -> text(Lang.t("Saved, but takes effect only after the server is restarted."),
                ChatFormatting.YELLOW), false);
        }
        return 1;
    }

    // ---- suggestions (with the description as tooltip, like vanilla) -------------------------

    private static CompletableFuture<Suggestions> suggestSettings(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        ConfigBridge bridge = ConfigBridge.installed();
        if (bridge != null) {
            String typed = builder.getRemainingLowerCase();
            for (ConfigBridge.Entry entry : bridge.entries()) {
                if (entry.path().toLowerCase(java.util.Locale.ROOT).contains(typed)) {
                    builder.suggest(entry.path(), Component.literal(firstLine(entry.description())));
                }
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestValues(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Optional<ConfigBridge.Entry> found = find(StringArgumentType.getString(ctx, SETTING));
        if (found.isPresent()) {
            ConfigBridge.Entry entry = found.get();
            if (entry.defaultValue().equals("true") || entry.defaultValue().equals("false")) {
                builder.suggest("true", Component.literal(firstLine(entry.description())));
                builder.suggest("false", Component.literal(firstLine(entry.description())));
            } else {
                builder.suggest(entry.value(), Component.literal(Lang.t("current value")));
                builder.suggest(entry.defaultValue(), Component.literal(Lang.t("default value")));
            }
        }
        return builder.buildFuture();
    }

    // ---- small helpers ----------------------------------------------------------------------

    private static String firstLine(String description) {
        int end = description.indexOf('\n');
        return end < 0 ? description : description.substring(0, end);
    }

    private static String shortName(String path) {
        return path.contains(".") ? path.substring(path.indexOf('.') + 1) : path;
    }

    private static ChatFormatting valueColor(ConfigBridge.Entry entry) {
        if (entry.value().equals("true")) {
            return ChatFormatting.GREEN;
        }
        if (entry.value().equals("false")) {
            return ChatFormatting.RED;
        }
        return entry.value().equals(entry.defaultValue()) ? ChatFormatting.WHITE : ChatFormatting.YELLOW;
    }

    private static MutableComponent text(String value, ChatFormatting... formats) {
        return Component.literal(value).withStyle(formats);
    }
}
