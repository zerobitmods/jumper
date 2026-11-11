package com.jumper;

import java.util.List;

/**
 * Every number behind Jumper's rules. Fixed limits are constants; everything a server owner can change is listed in
 * {@link #SETTINGS} and lives in the server config (world/serverconfig/jumper-server.toml), read in by
 * {@code JumperConfig} through {@link #apply}. Until a config is loaded (and on a client connected to someone else's
 * server) the defaults below are used. Tiers are numbered 1 to 4.
 */
public final class JumperRules {
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 4;
    /** Range value that means "no limit". */
    public static final int UNLIMITED = -1;
    /** Most slots a tier can have (the most spots one sync message carries). */
    public static final int MAX_SLOTS = 256;

    /** How far (in blocks) a player can look at a block to use it as a new spot's picture and name. */
    public static final double ICON_REACH = 5.0;
    /** Longest spot name, in characters. */
    public static final int MAX_NAME_LENGTH = 24;

    /**
     * One line of the server config. Numbers are passed around as long but every limit fits in an int (the config
     * stores ints); a true/false setting uses 1 and 0.
     */
    public record Setting(String key, String comment, long defaultValue, long min, long max, boolean isBoolean) {
        static Setting number(String key, long defaultValue, long min, long max, String comment) {
            return new Setting(key, comment, defaultValue, min, max, false);
        }

        static Setting flag(String key, boolean defaultValue, String comment) {
            return new Setting(key, comment, defaultValue ? 1 : 0, 0, 1, true);
        }
    }

    private static final int MAX_RANGE = 30_000_000;
    private static final long MAX_UPKEEP = 1_000_000_000L;

    /** Everything in the server config, in the order the file lists it. The keys' sections become [tier1] etc. */
    public static final List<Setting> SETTINGS = List.of(
            Setting.number("tier1.range", 64, UNLIMITED, MAX_RANGE,
                    "How far from the anchor (in blocks) jumps work at tier 1. -1 = no limit. Every anchor works like "
                            + "tier 1 while it has no power or its chunk isn't loaded."),
            Setting.number("tier1.slots", 8, 1, MAX_SLOTS, "Spots each linked player can save at tier 1."),
            Setting.flag("tier1.crossDimension", false, "Whether tier 1 can jump between dimensions."),
            Setting.number("tier2.range", 256, UNLIMITED, MAX_RANGE, "Range at tier 2 in blocks. -1 = no limit."),
            Setting.number("tier2.slots", 16, 1, MAX_SLOTS, "Spots each linked player can save at tier 2."),
            Setting.number("tier2.upkeep", 50_000, 0, MAX_UPKEEP,
                    "Power a tier 2 anchor uses while its chunk is loaded, in FE per tick. 0 = needs no power."),
            Setting.flag("tier2.crossDimension", false, "Whether tier 2 can jump between dimensions."),
            Setting.number("tier3.range", 2048, UNLIMITED, MAX_RANGE, "Range at tier 3 in blocks. -1 = no limit."),
            Setting.number("tier3.slots", 32, 1, MAX_SLOTS, "Spots each linked player can save at tier 3."),
            Setting.number("tier3.upkeep", 500_000, 0, MAX_UPKEEP, "Power a tier 3 anchor uses, in FE per tick."),
            Setting.flag("tier3.crossDimension", false, "Whether tier 3 can jump between dimensions."),
            Setting.number("tier4.range", UNLIMITED, UNLIMITED, MAX_RANGE, "Range at tier 4 in blocks. -1 = no limit."),
            Setting.number("tier4.slots", 64, 1, MAX_SLOTS, "Spots each linked player can save at tier 4."),
            Setting.number("tier4.upkeep", 5_000_000, 0, MAX_UPKEEP, "Power a tier 4 anchor uses, in FE per tick."),
            Setting.flag("tier4.crossDimension", true, "Whether tier 4 can jump between dimensions."),
            Setting.number("power.upkeepPerSpot", 2, 0, 1_000_000,
                    "Extra FE per tick for every spot saved on a tier 2+ anchor (all its linked players together)."),
            Setting.number("power.bufferSeconds", 3600, 1, 604_800,
                    "How many seconds of its current upkeep an anchor can store."),
            Setting.number("jumps.cooldownTicks", 40, 10, 72_000,
                    "Ticks a player must wait between jumps (20 ticks = 1 second). At least 10, because a jump can "
                    + "load the chunk it lands in."),
            Setting.flag("jumps.blockAfterHit", true,
                    "Refuse jumps for 5 seconds after a mob or player hits you, and after you hit another player."));

    private static final int[] range = {64, 256, 2048, UNLIMITED};
    private static final int[] slots = {8, 16, 32, 64};
    /** FE per tick for each tier; tier 1 (index 0) is always free. */
    private static final long[] upkeep = {0, 50_000, 500_000, 5_000_000};
    private static final boolean[] crossDimension = {false, false, false, true};
    private static long upkeepPerSpot = 2;
    private static int bufferTicks = 3600 * 20;
    private static int cooldownTicks = 40;
    private static boolean blockAfterHit = true;

    private JumperRules() {
    }

    /**
     * Takes one setting's value (already checked against its limits by the config). Unknown keys are ignored. Only
     * called on the server thread.
     */
    public static void apply(String key, long value) {
        String[] parts = key.split("\\.", 2);
        if (parts.length == 2 && parts[0].startsWith("tier")) {
            int tier;
            try {
                tier = Integer.parseInt(parts[0].substring(4));
            } catch (NumberFormatException e) {
                return;
            }
            if (tier < MIN_TIER || tier > MAX_TIER) {
                return;
            }
            int i = tier - 1;
            switch (parts[1]) {
                case "range" -> range[i] = (int) Math.max(UNLIMITED, Math.min(MAX_RANGE, value));
                case "slots" -> slots[i] = (int) Math.max(1, Math.min(MAX_SLOTS, value));
                case "upkeep" -> upkeep[i] = i == 0 ? 0 : Math.max(0, Math.min(MAX_UPKEEP, value));
                case "crossDimension" -> crossDimension[i] = value != 0;
                default -> {
                }
            }
            return;
        }
        switch (key) {
            case "power.upkeepPerSpot" -> upkeepPerSpot = Math.max(0, value);
            case "power.bufferSeconds" -> bufferTicks = (int) Math.max(20, Math.min(604_800L * 20, value * 20));
            case "jumps.cooldownTicks" -> cooldownTicks = (int) Math.max(0, Math.min(72_000, value));
            case "jumps.blockAfterHit" -> blockAfterHit = value != 0;
            default -> {
            }
        }
    }

    /** The setting with this key, or null. */
    public static Setting setting(String key) {
        for (Setting setting : SETTINGS) {
            if (setting.key().equals(key)) {
                return setting;
            }
        }
        return null;
    }

    public static int clampTier(int tier) {
        return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
    }

    /** How far from the anchor (in blocks) jumps work, or {@link #UNLIMITED}. */
    public static int range(int tier) {
        return range[clampTier(tier) - 1];
    }

    /** How many spots a player linked to an anchor of this tier can save. */
    public static int slots(int tier) {
        return slots[clampTier(tier) - 1];
    }

    /**
     * FE per tick an anchor of this tier uses, with this many saved spots on it. 0 means it needs no power (tier 1, or
     * upkeep set to 0).
     */
    public static long upkeep(int tier, int spots) {
        int t = clampTier(tier);
        if (t == MIN_TIER || upkeep[t - 1] == 0) {
            return 0;
        }
        return upkeep[t - 1] + upkeepPerSpot * Math.max(0, spots);
    }

    /** True if this tier can jump between dimensions. */
    public static boolean crossDimension(int tier) {
        return crossDimension[clampTier(tier) - 1];
    }

    /**
     * How far an anchor working as this tier reaches, in blocks, or {@link #UNLIMITED}: a tier that reaches other
     * dimensions reaches everywhere (see {@code JumpService.reach}). This is the range dome's size.
     */
    public static int reach(int tier) {
        return crossDimension(tier) ? UNLIMITED : range(tier);
    }

    /** An anchor holds this many ticks of its current upkeep. */
    public static int bufferTicks() {
        return bufferTicks;
    }

    /** Ticks between jumps (20 ticks = 1 second). */
    public static int cooldownTicks() {
        return cooldownTicks;
    }

    /** True if being hit in the last 5 seconds blocks a jump. */
    public static boolean blockAfterHit() {
        return blockAfterHit;
    }
}
