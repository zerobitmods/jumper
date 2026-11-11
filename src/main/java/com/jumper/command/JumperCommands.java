package com.jumper.command;

import com.jumper.Jumper;
import com.jumper.JumperConfig;
import com.jumper.JumperRules;
import com.jumper.block.JumperAnchorBlock;
import com.jumper.data.AnchorKey;
import com.jumper.data.AnchorRecord;
import com.jumper.data.JumperData;
import com.jumper.data.PlayerRecord;
import com.jumper.data.Spot;
import com.jumper.jump.JumpService;
import com.jumper.jump.JumperSync;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /jumper commands for every player: save, go, back, home, list, remove. They go through exactly the same server checks as
 * the wheel. "tier" is for operators: it sets the tier of the anchor you're linked to (for testing and for admins,
 * until upgrade items exist). "power" shows your anchor's power; "power add" and "power set" are for operators (testing).
 */
public final class JumperCommands {

    /** Offers the player's own spot names while they type. */
    private static final SuggestionProvider<CommandSourceStack> SPOT_NAMES = (context, builder) -> {
        List<String> names = new ArrayList<>();
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            PlayerRecord record = JumperData.get(player.server).player(player.getUUID());
            if (record != null) {
                for (Spot spot : record.spots()) {
                    names.add(spot.name());
                }
            }
        }
        return SharedSuggestionProvider.suggest(names, builder);
    };

    /** Suggests the server config's setting names. */
    private static final SuggestionProvider<CommandSourceStack> SETTING_NAMES = (context, builder) -> {
        List<String> keys = new ArrayList<>();
        for (JumperRules.Setting setting : JumperRules.SETTINGS) {
            keys.add(setting.key());
        }
        return SharedSuggestionProvider.suggest(keys, builder);
    };

    private JumperCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("jumper");
        root.then(Commands.literal("save")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> show(context.getSource(), JumpService.save(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("go")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SPOT_NAMES)
                                .executes(context -> show(context.getSource(), JumpService.jumpTo(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("back")
                        .executes(context -> show(context.getSource(),
                                JumpService.back(context.getSource().getPlayerOrException()))))
                .then(Commands.literal("home")
                        .executes(context -> show(context.getSource(),
                                JumpService.home(context.getSource().getPlayerOrException()))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SPOT_NAMES)
                                .executes(context -> show(context.getSource(), JumpService.remove(
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("tier")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("tier", IntegerArgumentType.integer(JumperRules.MIN_TIER, JumperRules.MAX_TIER))
                                .executes(context -> setTier(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "tier")))))
                .then(Commands.literal("power")
                        .executes(context -> power(context.getSource()))
                        .then(Commands.literal("add")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("fe", LongArgumentType.longArg(0))
                                        .executes(context -> changePower(context.getSource(),
                                                LongArgumentType.getLong(context, "fe"), true))))
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("fe", LongArgumentType.longArg(0))
                                        .executes(context -> changePower(context.getSource(),
                                                LongArgumentType.getLong(context, "fe"), false)))))
                .then(Commands.literal("config")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> listConfig(context.getSource()))
                        .then(Commands.argument("setting", StringArgumentType.word())
                                .suggests(SETTING_NAMES)
                                .executes(context -> showConfig(context.getSource(),
                                        StringArgumentType.getString(context, "setting")))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .executes(context -> setConfig(context.getSource(),
                                                StringArgumentType.getString(context, "setting"),
                                                StringArgumentType.getString(context, "value"))))))
                .then(Commands.literal("version")
                        .executes(context -> {
                            context.getSource().sendSuccess(
                                    () -> Component.literal("Jumper " + Jumper.version() + " (build " + Jumper.DEV_STEP + ")"), false);
                            return 1;
                        }));
        dispatcher.register(root);
    }

    private static int show(CommandSourceStack source, JumpService.Result result) {
        if (result.ok()) {
            source.sendSuccess(result::message, false);
            return 1;
        }
        source.sendFailure(result.message());
        return 0;
    }

    /** Operators: every server config setting and its value. */
    private static int listConfig(CommandSourceStack source) {
        if (!JumperConfig.loaded()) {
            source.sendFailure(Component.translatable("message.jumper.config_not_loaded"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.jumper.config_header"), false);
        for (JumperRules.Setting setting : JumperRules.SETTINGS) {
            String value = JumperConfig.get(setting.key());
            source.sendSuccess(() -> Component.translatable("message.jumper.config_line", setting.key(), value), false);
        }
        return 1;
    }

    /** Operators: one setting's value and what it does. */
    private static int showConfig(CommandSourceStack source, String key) {
        JumperRules.Setting setting = JumperRules.setting(key);
        String value = JumperConfig.get(key);
        if (setting == null || value == null) {
            source.sendFailure(Component.translatable(setting == null ? "message.jumper.config_unknown"
                    : "message.jumper.config_not_loaded", key));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("message.jumper.config_line", key, value), false);
        source.sendSuccess(() -> Component.literal(setting.comment()), false);
        return 1;
    }

    /** Operators: changes a setting, saves it to the server config and applies it straight away. */
    private static int setConfig(CommandSourceStack source, String key, String text) {
        String problem = JumperConfig.set(source.getServer(), key, text);
        if (problem != null) {
            JumperRules.Setting setting = JumperRules.setting(key);
            source.sendFailure(Component.translatable("message.jumper." + problem, key,
                    setting == null ? 0 : setting.min(), setting == null ? 0 : setting.max()));
            return 0;
        }
        String value = JumperConfig.get(key);
        // Logged for every operator, like other admin commands.
        source.sendSuccess(() -> Component.translatable("message.jumper.config_set", key, value), true);
        return 1;
    }

    /** Operators: sets the tier of the anchor the player is linked to. */
    private static int setTier(CommandSourceStack source, int tier) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        JumperData data = JumperData.get(player.server);
        PlayerRecord record = data.player(player.getUUID());
        AnchorKey link = record == null ? null : record.link();
        if (link == null || !data.setTier(link, tier)) {
            source.sendFailure(Component.translatable("message.jumper.not_linked"));
            return 0;
        }
        JumperAnchorBlock.showTier(player.server, link, tier);
        JumperSync.sendToLinked(player.server, link);
        int range = JumperRules.range(tier);
        source.sendSuccess(() -> range == JumperRules.UNLIMITED
                ? Component.translatable("message.jumper.tier_set_unlimited", tier, JumperRules.slots(tier))
                : Component.translatable("message.jumper.tier_set", tier, range, JumperRules.slots(tier)), true);
        return 1;
    }

    /** The anchor the player is linked to, or null after telling them why not. */
    private static AnchorRecord linkedAnchor(CommandSourceStack source, ServerPlayer player, JumperData data) {
        PlayerRecord record = data.player(player.getUUID());
        AnchorKey link = record == null ? null : record.link();
        if (link == null) {
            source.sendFailure(Component.translatable("message.jumper.not_linked"));
            return null;
        }
        AnchorRecord anchor = JumpService.liveAnchor(player.server, data, link);
        if (anchor == null) {
            source.sendFailure(Component.translatable("message.jumper.anchor_gone"));
        }
        return anchor;
    }

    /** Shows how much power the player's anchor has, what it uses, and how long that lasts. */
    private static int power(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        JumperData data = JumperData.get(player.server);
        AnchorRecord anchor = linkedAnchor(source, player, data);
        if (anchor == null) {
            return 0;
        }
        long rate = anchor.rate();
        if (rate == 0) {
            source.sendSuccess(() -> Component.translatable("message.jumper.power_free", anchor.tier()), false);
            return 1;
        }
        String stored = fe(anchor.energy());
        String capacity = fe(anchor.capacity());
        JumpService.Power power = JumpService.power(player.server, data.player(player.getUUID()).link(), anchor);
        if (power.empty()) {
            source.sendSuccess(() -> Component.translatable("message.jumper.power_out", anchor.tier(), fe(rate),
                    capacity), false);
        } else {
            String left = duration(anchor.energy() / rate);
            source.sendSuccess(() -> Component.translatable("message.jumper.power_status", anchor.tier(), stored,
                    capacity, fe(rate), anchor.spots(), left), false);
        }
        if (power.unloaded()) {
            // Said with or without power: a base that isn't loaded is its own problem, with its own fix.
            source.sendSuccess(() -> Component.translatable(power.empty() ? "message.jumper.power_not_loaded_out"
                    : "message.jumper.power_not_loaded"), false);
        }
        return power.empty() ? 0 : 1;
    }

    /** Operators: adds to (or sets) the stored power of the anchor they're linked to. */
    private static int changePower(CommandSourceStack source, long amount, boolean add) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        JumperData data = JumperData.get(player.server);
        AnchorRecord anchor = linkedAnchor(source, player, data);
        if (anchor == null) {
            return 0;
        }
        if (anchor.rate() == 0) {
            source.sendFailure(Component.translatable("message.jumper.power_free", anchor.tier()));
            return 0;
        }
        long target = add ? anchor.energy() + Math.min(amount, Long.MAX_VALUE / 2) : amount;
        data.setEnergy(anchor, target);
        JumperSync.send(player); // the wheel's slot count changes when power comes back or runs out
        String stored = fe(anchor.energy());
        String capacity = fe(anchor.capacity());
        source.sendSuccess(() -> Component.translatable("message.jumper.power_set", stored, capacity), true);
        return 1;
    }

    /** 1234567 -> "1,234,567". */
    private static String fe(long amount) {
        return String.format(Locale.ROOT, "%,d", amount);
    }

    /** Ticks as "1 h 5 min", "12 min 30 s" or "45 s". */
    private static String duration(long ticks) {
        long seconds = ticks / 20;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return hours + " h " + minutes + " min";
        }
        if (minutes > 0) {
            return minutes + " min " + secs + " s";
        }
        return secs + " s";
    }

    /** Shows the player's link and spots. */
    private static int list(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        JumperData data = JumperData.get(player.server);
        PlayerRecord record = data.player(player.getUUID());
        AnchorKey link = record == null ? null : record.link();
        AnchorRecord anchor = link == null ? null : JumpService.liveAnchor(player.server, data, link);
        if (link == null) {
            source.sendFailure(Component.translatable("message.jumper.not_linked"));
            return 0;
        }
        if (anchor == null) {
            source.sendFailure(Component.translatable("message.jumper.anchor_gone"));
            return 0;
        }
        BlockPos pos = link.blockPos();
        int tier = anchor.tier();
        int range = JumperRules.range(tier);
        String where = pos.getX() + " " + pos.getY() + " " + pos.getZ() + " (" + link.dimension() + ")";
        Component anchorLine = range == JumperRules.UNLIMITED
                ? Component.translatable("message.jumper.list_anchor_unlimited", where, tier)
                : Component.translatable("message.jumper.list_anchor", where, tier, range);
        source.sendSuccess(() -> anchorLine, false);

        List<Spot> spots = record.spots();
        if (spots.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.jumper.list_empty", JumperRules.slots(tier)), false);
            return 1;
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < spots.size(); i++) {
            if (i > 0) {
                names.append(", ");
            }
            names.append(spots.get(i).name());
        }
        String joined = names.toString();
        source.sendSuccess(() -> Component.translatable("message.jumper.list_spots", spots.size(),
                JumperRules.slots(tier), joined), false);
        return spots.size();
    }
}
