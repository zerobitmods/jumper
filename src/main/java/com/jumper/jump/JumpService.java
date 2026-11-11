package com.jumper.jump;

import com.jumper.JumperRules;
import com.jumper.block.JumperAnchorBlock;
import com.jumper.data.AnchorKey;
import com.jumper.data.AnchorRecord;
import com.jumper.data.JumperData;
import com.jumper.data.PlayerRecord;
import com.jumper.data.Spot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;

/**
 * The rules of jumping, all checked on the server: saving and removing spots, jumping to one, and jumping back.
 * Nothing here runs unless a player asks for it, and nothing is trusted from the client.
 */
public final class JumpService {

    /** How something a player asked for turned out: whether it worked, and the line to show them. */
    public record Result(boolean ok, Component message) {
    }

    private enum Reach {
        OK, WRONG_DIMENSION, TOO_FAR
    }

    /**
     * Whether an anchor's upgrades are working right now. Anything but {@link #FREE} and {@link #ON} means it works as a
     * tier 1 anchor for now (Home still uses its real tier).
     */
    public enum Power {
        /** Needs no power (tier 1, or upkeep set to 0). */
        FREE,
        /** Powered, and its chunk is running. */
        ON,
        /** Out of power (its chunk is running). */
        NO_POWER,
        /** Has power, but its chunk isn't running (unloaded, or only held at the edge of a loaded area). */
        NOT_LOADED,
        /** Out of power, and its chunk isn't running either. Kept last: the client reads these by position. */
        NOT_LOADED_NO_POWER;

        public boolean active() {
            return this == FREE || this == ON;
        }

        /** True if the anchor's chunk isn't running. */
        public boolean unloaded() {
            return this == NOT_LOADED || this == NOT_LOADED_NO_POWER;
        }

        /** True if the anchor needs power and has none. */
        public boolean empty() {
            return this == NO_POWER || this == NOT_LOADED_NO_POWER;
        }

        /** First part of the message key for a refusal caused by this state. */
        String reason() {
            return switch (this) {
                case NOT_LOADED -> "not_loaded";
                case NOT_LOADED_NO_POWER -> "unloaded_no_power";
                default -> "no_power";
            };
        }
    }

    /** Where a player stands compared with their anchor's reach (sent to the wheel). */
    public static final byte HERE_OK = 0;
    /** Outside what works right now (no power, or the base isn't loaded), but inside the real range: Home works. */
    public static final byte HERE_LIMITED = 1;
    /** Farther than the anchor's upgrades reach: nothing works from here, Home included. */
    public static final byte HERE_TOO_FAR = 2;
    /** In a dimension the anchor can't reach. */
    public static final byte HERE_OTHER_DIMENSION = 3;

    /** What kind of jump is being made. */
    private enum Kind {
        SPOT, BACK, HOME
    }

    /** Places tried, in order, when jumping home: on top of the anchor first, then around it (x, y, z from the anchor). */
    /** How long hitting another player blocks your own jumps: 5 seconds, as long as vanilla remembers being hit. */
    private static final int ATTACKER_BLOCK_TICKS = 100;

    private static final int[][] HOME_OFFSETS = {
            {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
            {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1}, {0, 2, 0}};

    private JumpService() {
    }

    // ---- what players can ask for ----

    /** Saves the place the player is standing as a new spot with this name. */
    public static Result save(ServerPlayer player, String rawName) {
        return save(player, rawName, false);
    }

    /** Saves the place the player is standing, named after the block they're looking at (the wheel's "+"). */
    public static Result saveAuto(ServerPlayer player) {
        return save(player, "", true);
    }

    private static Result save(ServerPlayer player, String rawName, boolean autoName) {
        MinecraftServer server = player.server;
        JumperData data = JumperData.get(server);
        PlayerRecord record = data.player(player.getUUID());
        Result problem = linkProblem(server, data, record);
        if (problem != null) {
            return problem;
        }
        AnchorKey link = record.link();
        AnchorRecord anchor = data.anchor(link);
        int realTier = anchor.tier();
        Power power = power(server, link, anchor);
        int tier = power.active() ? realTier : JumperRules.MIN_TIER;
        Item iconItem = iconFor(player);
        String name = autoName ? autoName(record, iconItem) : cleanName(rawName);
        if (name.isEmpty() || name.length() > JumperRules.MAX_NAME_LENGTH) {
            return fail("name_bad", JumperRules.MAX_NAME_LENGTH);
        }
        if (record.indexOf(name) >= 0) {
            return fail("name_taken", name);
        }
        int slots = JumperRules.slots(tier);
        if (record.spots().size() >= slots) {
            return record.spots().size() < JumperRules.slots(realTier)
                    ? fail(power.reason() + "_full", slots) : fail("spots_full", slots);
        }
        if (!player.onGround()) {
            return fail("not_on_ground");
        }
        Spot spot = Spot.at(name, BuiltInRegistries.ITEM.getKey(iconItem).toString(), player);
        Reach reach = reach(link, tier, spot.dimension(), spot.x(), spot.y(), spot.z());
        if (reach != Reach.OK && reach(link, realTier, spot.dimension(), spot.x(), spot.y(), spot.z()) == Reach.OK) {
            return fail(power.reason() + "_save", JumperRules.range(JumperRules.MIN_TIER));
        }
        if (reach == Reach.WRONG_DIMENSION) {
            return fail("you_wrong_dimension");
        }
        if (reach == Reach.TOO_FAR) {
            return fail("you_out_of_range", JumperRules.range(realTier));
        }
        data.addSpot(record, spot);
        JumperSync.send(player);
        return ok("saved", name, record.spots().size(), slots);
    }

    /** Removes the spot with this name. */
    public static Result remove(ServerPlayer player, String name) {
        JumperData data = JumperData.get(player.server);
        PlayerRecord record = data.player(player.getUUID());
        int index = record == null ? -1 : record.indexOf(name.trim());
        if (index < 0) {
            return fail("no_such_spot", name.trim());
        }
        Spot removed = data.removeSpot(record, index);
        JumperSync.send(player);
        return ok("removed", removed.name());
    }

    // ---- editing the list (the list screen; each edit names the spot it expects, so a stale screen can't hit another) ----

    /** Gives the spot at this index a new name. */
    public static Result rename(ServerPlayer player, int index, String expected, String rawName) {
        JumperData data = JumperData.get(player.server);
        PlayerRecord record = data.player(player.getUUID());
        if (!editable(player.server, data, record, index, expected)) {
            return fail("spot_missing");
        }
        String name = cleanName(rawName);
        if (name.isEmpty() || name.length() > JumperRules.MAX_NAME_LENGTH) {
            return fail("name_bad", JumperRules.MAX_NAME_LENGTH);
        }
        int other = record.indexOf(name);
        if (other >= 0 && other != index) {
            return fail("name_taken", name);
        }
        Spot old = record.spots().get(index);
        data.replaceSpot(record, index, old.withName(name));
        JumperSync.send(player);
        return ok("renamed", old.name(), name);
    }

    /** Uses the item in the player's main hand as the picture of the spot at this index. */
    public static Result setPicture(ServerPlayer player, int index, String expected) {
        JumperData data = JumperData.get(player.server);
        PlayerRecord record = data.player(player.getUUID());
        if (!editable(player.server, data, record, index, expected)) {
            return fail("spot_missing");
        }
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            return fail("picture_empty_hand");
        }
        Spot old = record.spots().get(index);
        data.replaceSpot(record, index, old.withIcon(BuiltInRegistries.ITEM.getKey(held.getItem()).toString()));
        JumperSync.send(player);
        return ok("picture_set", old.name());
    }

    /** Moves the spot at this index one place earlier (-1) or later (+1). Says nothing when it works (it's quick). */
    public static Result move(ServerPlayer player, int index, String expected, int direction) {
        JumperData data = JumperData.get(player.server);
        PlayerRecord record = data.player(player.getUUID());
        if (!editable(player.server, data, record, index, expected)) {
            return fail("spot_missing");
        }
        int target = index + (direction < 0 ? -1 : 1);
        if (target < 0 || target >= visibleCount(player.server, data, record)) {
            return fail("cant_move");
        }
        data.swapSpots(record, index, target);
        JumperSync.send(player);
        return new Result(true, Component.empty());
    }

    /** Deletes the spot at this index (the list screen has already asked the player to confirm). */
    public static Result removeAt(ServerPlayer player, int index, String expected) {
        JumperData data = JumperData.get(player.server);
        PlayerRecord record = data.player(player.getUUID());
        if (!editable(player.server, data, record, index, expected)) {
            return fail("spot_missing");
        }
        Spot removed = data.removeSpot(record, index);
        record.rememberDeleted(removed);
        JumperSync.send(player);
        return ok("removed", removed.name());
    }

    /**
     * Brings back a spot this player deleted from the list (the list's Undo), at this place in the list. Only spots the
     * server itself kept can come back, so the client never says where a spot is.
     */
    public static Result restore(ServerPlayer player, int index, String name) {
        MinecraftServer server = player.server;
        JumperData data = JumperData.get(server);
        PlayerRecord record = data.player(player.getUUID());
        Result problem = linkProblem(server, data, record);
        if (problem != null) {
            return problem;
        }
        Spot spot = record.findDeleted(name);
        if (spot == null) {
            return fail("restore_gone", name);
        }
        if (record.indexOf(spot.name()) >= 0) {
            return fail("name_taken", spot.name());
        }
        // A full list can't take it back, unless spots are hidden past the slots: then the delete pulled one of them
        // into view, and putting this one back just hides it again (the count goes back to what it was).
        int realSlots = JumperRules.slots(data.anchor(record.link()).tier());
        if (record.spots().size() == realSlots) {
            return fail("spots_full", realSlots);
        }
        int at = Math.max(0, Math.min(index, visibleCount(server, data, record)));
        data.insertSpot(record, at, spot);
        record.forgetDeleted(spot);
        JumperSync.send(player);
        return ok("restored", spot.name());
    }

    /** Sets the picture of the spot at this index to an item by id (the list's Undo putting an old picture back). */
    public static Result setPictureId(ServerPlayer player, int index, String expected, String itemId) {
        JumperData data = JumperData.get(player.server);
        PlayerRecord record = data.player(player.getUUID());
        if (!editable(player.server, data, record, index, expected)) {
            return fail("spot_missing");
        }
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key) || BuiltInRegistries.ITEM.get(key) == Items.AIR) {
            return fail("picture_bad");
        }
        Spot old = record.spots().get(index);
        data.replaceSpot(record, index, old.withIcon(key.toString()));
        JumperSync.send(player);
        return ok("picture_set", old.name());
    }

    /** True if the index is one the player can see in their list and the spot there has the expected name. */
    private static boolean editable(MinecraftServer server, JumperData data, PlayerRecord record, int index,
            String expected) {
        return record != null && index >= 0 && index < visibleCount(server, data, record)
                && record.spots().get(index).name().equals(expected);
    }

    /**
     * How many of the player's spots their list shows: all of them up to the linked anchor's real slot count (spots
     * past it were saved on a bigger anchor and stay hidden and untouched). Same rule as {@link JumperSync#send}.
     */
    static int visibleCount(MinecraftServer server, JumperData data, PlayerRecord record) {
        AnchorRecord anchor = record.link() == null ? null : data.anchor(record.link());
        int size = record.spots().size();
        return anchor == null ? 0 : Math.min(size, JumperRules.slots(anchor.tier()));
    }

    /** A name as typed, with spaces trimmed and characters that could change how text draws (like §) taken out. */
    public static String cleanName(String raw) {
        StringBuilder out = new StringBuilder(Math.min(raw.length(), 64));
        for (int i = 0; i < raw.length() && out.length() <= JumperRules.MAX_NAME_LENGTH * 2; i++) {
            char ch = raw.charAt(i);
            if (ch >= ' ' && ch != 127 && ch != '\u00a7') {
                out.append(ch);
            }
        }
        return out.toString().trim();
    }

    /** Jumps to the spot with this name. */
    public static Result jumpTo(ServerPlayer player, String name) {
        PlayerRecord record = JumperData.get(player.server).player(player.getUUID());
        int index = record == null ? -1 : record.indexOf(name.trim());
        if (record != null && record.link() != null && index < 0) {
            return fail("no_such_spot", name.trim());
        }
        return jumpToIndex(player, index);
    }

    /** Jumps to the spot at this place in the player's list (what the wheel sends). */
    public static Result jumpToIndex(ServerPlayer player, int index) {
        MinecraftServer server = player.server;
        JumperData data = JumperData.get(server);
        PlayerRecord record = data.player(player.getUUID());
        Result problem = linkProblem(server, data, record);
        if (problem != null) {
            return problem;
        }
        if (index < 0 || index >= record.spots().size()) {
            return fail("spot_missing");
        }
        Spot spot = record.spots().get(index);
        AnchorRecord anchor = data.anchor(record.link());
        Power power = power(server, record.link(), anchor);
        int slots = JumperRules.slots(power.active() ? anchor.tier() : JumperRules.MIN_TIER);
        if (index >= slots) {
            if (index < JumperRules.slots(anchor.tier())) {
                // The anchor has the slot, but isn't powered or loaded right now.
                return fail(power.reason() + "_slot", spot.name());
            }
            // Saved while linked to a bigger anchor; the current one doesn't have this many slots.
            return fail("slot_locked", spot.name(), slots);
        }
        return jump(player, data, record, spot, Kind.SPOT);
    }

    /** Jumps back to where the player last jumped from. */
    public static Result back(ServerPlayer player) {
        MinecraftServer server = player.server;
        JumperData data = JumperData.get(server);
        PlayerRecord record = data.player(player.getUUID());
        Result problem = linkProblem(server, data, record);
        if (problem != null) {
            return problem;
        }
        if (record.back() == null) {
            return fail("no_back");
        }
        return jump(player, data, record, record.back(), Kind.BACK);
    }

    /** Jumps to the player's anchor: onto it, or right beside it if something is on top. */
    public static Result home(ServerPlayer player) {
        MinecraftServer server = player.server;
        JumperData data = JumperData.get(server);
        PlayerRecord record = data.player(player.getUUID());
        Result problem = linkProblem(server, data, record);
        if (problem != null) {
            return problem;
        }
        return jump(player, data, record, null, Kind.HOME);
    }

    // ---- the jump itself ----

    /** target is the spot to land on; for {@link Kind#HOME} it's null and a place at the anchor is found here. */
    private static Result jump(ServerPlayer player, JumperData data, PlayerRecord record, Spot target, Kind kind) {
        boolean back = kind == Kind.BACK;
        MinecraftServer server = player.server;
        AnchorKey link = record.link();
        AnchorRecord anchor = data.anchor(link);
        int realTier = anchor.tier();
        // Home always works as far as the anchor's upgrades reach, powered or not: the way back from anywhere it covers.
        // Every other jump works as tier 1 while the anchor is out of power.
        Power power = power(server, link, anchor);
        int tier = kind == Kind.HOME || power.active() ? realTier : JumperRules.MIN_TIER;
        if (!player.isAlive()) {
            return fail("not_now");
        }

        // Both ends of the jump must be inside the anchor's range.
        String here = JumperData.dimensionId(player.level());
        Reach from = reach(link, tier, here, player.getX(), player.getY(), player.getZ());
        if (from != Reach.OK && tier != realTier
                && reach(link, realTier, here, player.getX(), player.getY(), player.getZ()) == Reach.OK) {
            return fail(power.reason() + "_range", JumperRules.range(JumperRules.MIN_TIER));
        }
        if (from == Reach.WRONG_DIMENSION) {
            return fail("you_wrong_dimension");
        }
        if (from == Reach.TOO_FAR) {
            // Still too far with the anchor's real tier (the limited case was answered above), so name the real range.
            return fail("you_out_of_range", JumperRules.range(realTier));
        }
        if (kind != Kind.HOME) { // the anchor itself is always in its own range
            Reach to = reach(link, tier, target.dimension(), target.x(), target.y(), target.z());
            if (to != Reach.OK && tier != realTier
                    && reach(link, realTier, target.dimension(), target.x(), target.y(), target.z()) == Reach.OK) {
                return fail(power.reason() + "_range", JumperRules.range(JumperRules.MIN_TIER));
            }
            if (to != Reach.OK) {
                return back ? fail("back_out_of_range") : fail("spot_out_of_range", target.name());
            }
        }

        long now = server.overworld().getGameTime();
        long wait = record.lastJumpTick() + JumperRules.cooldownTicks() - now;
        // A wait longer than the cooldown means the clock went backwards; ignore it.
        if (wait > 0 && wait <= JumperRules.cooldownTicks()) {
            return fail("cooldown", String.format(java.util.Locale.ROOT, "%.1f", wait / 20.0));
        }
        // Set while a mob or player has hit this player in the last 5 seconds (vanilla clears it after that).
        if (JumperRules.blockAfterHit() && player.getLastHurtByMob() != null) {
            return fail("in_combat");
        }
        // Hit another player in the last 5 seconds (step 18): no hitting someone and jumping away. Hitting mobs doesn't
        // count. Vanilla notes the last mob or player you hit (cleared once it dies), stamped with your own tick count.
        if (JumperRules.blockAfterHit() && player.getLastHurtMob() instanceof Player
                && player.tickCount - player.getLastHurtMobTimestamp() <= ATTACKER_BLOCK_TICKS) {
            return fail("hit_player");
        }

        ServerLevel destination;
        if (kind == Kind.HOME) {
            // Looked for only now, after the cheap checks, because it reads blocks at the anchor.
            destination = levelOf(server, link.dimension());
            target = destination == null ? null : homeSpot(destination, player, link);
            if (target == null) {
                return fail("home_blocked");
            }
        } else {
            destination = levelOf(server, target.dimension());
            // Someone who can fly may go back to a place they left in mid-air; everyone else needs a floor.
            boolean needFloor = !(back && player.getAbilities().mayfly);
            if (destination == null || !isSafe(destination, player, target, needFloor)) {
                return back ? fail("back_not_safe") : fail("not_safe", target.name());
            }
        }

        // Let claim and protection mods say no. Only "cancel" is honored: a changed target would skip the safety check.
        EntityTeleportEvent.TeleportCommand event =
                new EntityTeleportEvent.TeleportCommand(player, target.x(), target.y(), target.z());
        MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return fail("blocked");
        }

        Spot cameFrom = Spot.at("", "", player);
        ServerLevel origin = player.serverLevel();
        // Jumped from mid-air (jumping, falling, flying): "Back" should land on the floor below, not in the air.
        Spot backTo = player.onGround() ? cameFrom : groundBelow(origin, player, cameFrom);
        if (player.isSleeping()) {
            player.stopSleepInBed(true, true);
        }
        // Same as the vanilla /tp command: keep the arrival chunk loaded for the moment the player lands.
        destination.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT,
                new ChunkPos(BlockPos.containing(target.x(), target.y(), target.z())), 1, player.getId());
        player.teleportTo(destination, target.x(), target.y(), target.z(), target.yaw(), target.pitch());
        if (player.level() != destination) {
            // Another mod stopped the change of dimension.
            return fail("blocked");
        }
        player.resetFallDistance();
        // Leaving: a puff and a sound for anyone nearby (the jumper only hears the arrival).
        effects(origin, player, cameFrom.x(), cameFrom.y(), cameFrom.z(), false);
        effects(destination, player, target.x(), target.y(), target.z(), true);
        record.setLastJumpTick(now);
        data.setBack(record, backTo);
        JumperSync.send(player); // the wheel's "Back" appears after the first jump
        if (kind == Kind.HOME) {
            return ok("jumped_home");
        }
        return back ? ok("jumped_back") : ok("jumped", target.name());
    }

    /** A safe place to stand at the anchor (facing the way the player faces now), or null if it's walled in. */
    private static Spot homeSpot(ServerLevel level, ServerPlayer player, AnchorKey anchor) {
        BlockPos pos = anchor.blockPos();
        for (int[] offset : HOME_OFFSETS) {
            Spot candidate = new Spot("", "", anchor.dimension(), pos.getX() + offset[0] + 0.5, pos.getY() + offset[1],
                    pos.getZ() + offset[2] + 0.5, player.getYRot(), player.getXRot());
            if (isSafe(level, player, candidate, true)) {
                return candidate;
            }
        }
        return null;
    }

    /** How far down (in blocks) to look for a floor under a place the player left in mid-air. */
    private static final int BACK_FLOOR_SEARCH = 64;

    /**
     * The first safe place to stand straight below a mid-air place, checked every half block (so slabs count), or the
     * mid-air place itself if there's none within {@link #BACK_FLOOR_SEARCH} blocks. Only reads the column the player
     * is in, whose chunk is loaded.
     */
    private static Spot groundBelow(ServerLevel level, ServerPlayer player, Spot from) {
        double start = Math.floor(from.y() * 2.0) / 2.0;
        for (int step = 0; step <= BACK_FLOOR_SEARCH * 2; step++) {
            double y = start - step * 0.5;
            if (y < level.getMinBuildHeight()) {
                break;
            }
            Spot candidate = new Spot("", "", from.dimension(), from.x(), y, from.z(), from.yaw(), from.pitch());
            if (isSafe(level, player, candidate, true)) {
                return candidate;
            }
        }
        return from;
    }

    /**
     * True if a player can stand at the spot: inside the world, nothing solid in the way, something solid to stand on
     * (unless needFloor is false), and no lava or fire. Reading the blocks loads the arrival chunk if it isn't loaded,
     * which the jump would do anyway.
     */
    private static boolean isSafe(ServerLevel level, ServerPlayer player, Spot spot, boolean needFloor) {
        double x = spot.x();
        double y = spot.y();
        double z = spot.z();
        AABB body = player.getDimensions(Pose.STANDING).makeBoundingBox(x, y, z);
        if (body.minY < level.getMinBuildHeight() || body.maxY > level.getMaxBuildHeight()) {
            return false;
        }
        BlockPos feet = BlockPos.containing(x, y + 0.01, z);
        if (!level.getWorldBorder().isWithinBounds(feet)) {
            return false;
        }
        // Collision checks skip unloaded chunks (no blocks seen, so "nothing to stand on"). Load the arrival chunks
        // first, as any teleport does: usually one chunk, at most four. Spots and the anchor are in chunks that exist.
        for (int cx = Mth.floor(body.minX) >> 4; cx <= Mth.floor(body.maxX) >> 4; cx++) {
            for (int cz = Mth.floor(body.minZ) >> 4; cz <= Mth.floor(body.maxZ) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        if (!level.noCollision(body.deflate(1.0E-5))) {
            return false; // a block (or a boat, shulker...) is in the way
        }
        AABB floor = new AABB(body.minX, y - 0.5, body.minZ, body.maxX, y, body.maxZ);
        if (needFloor && level.noCollision(floor)) {
            return false; // nothing to stand on
        }
        if (level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(feet.above()).is(FluidTags.LAVA)) {
            return false;
        }
        return !level.getBlockState(feet).is(BlockTags.FIRE);
    }

    // ---- helpers ----

    /** The sound and particles at one end of a jump. Placeholder vanilla effects for now. */
    private static void effects(ServerLevel level, ServerPlayer player, double x, double y, double z, boolean arrival) {
        if (player.isSpectator()) {
            return; // spectators stay silent and unseen
        }
        level.sendParticles(ParticleTypes.PORTAL, x, y + 1.0, z, 24, 0.3, 0.5, 0.3, 0.2);
        // Passing the player leaves them out; null plays it for everyone nearby, the jumper included.
        level.playSound(arrival ? null : player, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                arrival ? 0.6F : 0.4F, arrival ? 1.2F : 0.9F);
    }

    /**
     * The item that stands for a new spot: the block the player is looking at (within a few blocks), else the block
     * under their feet, else an ender pearl.
     */
    private static Item iconFor(ServerPlayer player) {
        HitResult hit = player.pick(JumperRules.ICON_REACH, 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            Item looked = player.level().getBlockState(blockHit.getBlockPos()).getBlock().asItem();
            if (looked != Items.AIR) {
                return looked;
            }
        }
        Item below = player.level().getBlockState(player.getOnPos()).getBlock().asItem();
        return below != Items.AIR ? below : Items.ENDER_PEARL;
    }

    /** The item's name, with a number added if the player already has a spot called that. */
    private static String autoName(PlayerRecord record, Item iconItem) {
        String base = new ItemStack(iconItem).getHoverName().getString().trim();
        if (base.isEmpty()) {
            base = "Spot";
        }
        int room = JumperRules.MAX_NAME_LENGTH - 3; // leaves space for " 99"
        if (base.length() > room) {
            base = base.substring(0, room).trim();
        }
        if (record.indexOf(base) < 0) {
            return base;
        }
        for (int number = 2; number < 100; number++) {
            String candidate = base + " " + number;
            if (record.indexOf(candidate) < 0) {
                return candidate;
            }
        }
        return base;
    }

    /** A failure if the player has no usable link, else null. */
    private static Result linkProblem(MinecraftServer server, JumperData data, PlayerRecord record) {
        if (record == null || record.link() == null) {
            return fail("not_linked");
        }
        if (liveAnchor(server, data, record.link()) == null) {
            return fail("anchor_gone");
        }
        return null;
    }

    /**
     * The listed anchor, or null if it's gone. Never loads a chunk: if the anchor's chunk happens to be loaded, the
     * block is double-checked (and a stale entry dropped); if not, the list is trusted.
     */
    public static AnchorRecord liveAnchor(MinecraftServer server, JumperData data, AnchorKey key) {
        AnchorRecord anchor = data.anchor(key);
        if (anchor == null) {
            return null;
        }
        ServerLevel level = levelOf(server, key.dimension());
        if (level == null) {
            return null;
        }
        BlockPos pos = key.blockPos();
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null && !(chunk.getBlockState(pos).getBlock() instanceof JumperAnchorBlock)) {
            data.removeAnchor(key);
            return null;
        }
        return anchor;
    }

    /** Whether the anchor's upgrades are working right now: see {@link Power}. Never loads a chunk. */
    public static Power power(MinecraftServer server, AnchorKey key, AnchorRecord anchor) {
        if (!anchor.needsPower()) {
            return Power.FREE;
        }
        // Both are reported: "not loaded" is a separate problem from "no power", and each has its own fix.
        boolean running = running(server, key);
        if (anchor.energy() <= 0) {
            return running ? Power.NO_POWER : Power.NOT_LOADED_NO_POWER;
        }
        return running ? Power.ON : Power.NOT_LOADED;
    }

    /** Where the player stands compared with the anchor's reach: one of the HERE_ values. */
    public static byte here(ServerPlayer player, AnchorKey link, AnchorRecord anchor, Power power) {
        String dimension = JumperData.dimensionId(player.level());
        Reach real = reach(link, anchor.tier(), dimension, player.getX(), player.getY(), player.getZ());
        if (real == Reach.WRONG_DIMENSION) {
            return HERE_OTHER_DIMENSION;
        }
        if (real == Reach.TOO_FAR) {
            return HERE_TOO_FAR;
        }
        if (power.active()) {
            return HERE_OK;
        }
        return reach(link, JumperRules.MIN_TIER, dimension, player.getX(), player.getY(), player.getZ()) == Reach.OK
                ? HERE_OK : HERE_LIMITED;
    }

    /** True if the spot is inside the reach of an anchor working as this tier. */
    public static boolean inReach(AnchorKey link, int tier, Spot spot) {
        return reach(link, tier, spot.dimension(), spot.x(), spot.y(), spot.z()) == Reach.OK;
    }

    /** The anchor's tier as it works right now: its real tier when {@link Power#active()}, else tier 1. */
    public static int effectiveTier(MinecraftServer server, AnchorKey key, AnchorRecord anchor) {
        return power(server, key, anchor).active() ? anchor.tier() : JumperRules.MIN_TIER;
    }

    /**
     * True if the anchor's chunk is running: it is loaded and blocks there tick, so its generators and cables work. A
     * chunk only held in memory at the edge of a loaded area doesn't count. Neither does one that is inside a player's
     * simulation distance but not loaded (simulation distance can be larger than view distance), which is why this asks
     * the chunk source and not only the simulation-distance tracker. Never loads a chunk.
     */
    private static boolean running(MinecraftServer server, AnchorKey key) {
        ServerLevel level = levelOf(server, key.dimension());
        if (level == null) {
            return false;
        }
        BlockPos pos = key.blockPos();
        if (!level.getChunkSource().isPositionTicking(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4))) {
            return false;
        }
        // Anchors placed before block entities existed get theirs now, so their upkeep starts running.
        level.getBlockEntity(pos);
        return true;
    }

    /** The loaded dimension with this id, or null. */
    public static ServerLevel levelOf(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /** Whether a place is inside the anchor's reach: straight-line distance from the middle of the anchor block. */
    private static Reach reach(AnchorKey anchor, int tier, String dimension, double x, double y, double z) {
        if (JumperRules.crossDimension(tier)) {
            return Reach.OK;
        }
        if (!anchor.dimension().equals(dimension)) {
            return Reach.WRONG_DIMENSION;
        }
        int range = JumperRules.range(tier);
        if (range == JumperRules.UNLIMITED) {
            return Reach.OK;
        }
        BlockPos pos = anchor.blockPos();
        double dx = x - (pos.getX() + 0.5);
        double dy = y - (pos.getY() + 0.5);
        double dz = z - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= (double) range * range ? Reach.OK : Reach.TOO_FAR;
    }

    private static Result ok(String key, Object... args) {
        return new Result(true, Component.translatable("message.jumper." + key, args));
    }

    private static Result fail(String key, Object... args) {
        return new Result(false, Component.translatable("message.jumper." + key, args));
    }
}
