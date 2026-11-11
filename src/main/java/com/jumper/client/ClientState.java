package com.jumper.client;

import com.jumper.net.SyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** What this client knows about its own player's link and spots, as last told by the server. Display only. */
public final class ClientState {

    /**
     * One spot as the wheel shows it. iconId is the picture's item id exactly as the server sent it (the list's Undo
     * sends it back to put an old picture back).
     */
    public record Entry(String name, ItemStack icon, String iconId) {
    }

    /** Power states, matching {@code JumpService.Power} on the server. */
    public static final int POWER_FREE = 0;
    public static final int POWER_ON = 1;
    public static final int POWER_NONE = 2;
    public static final int POWER_NOT_LOADED = 3;
    /** Out of power and the base isn't loaded. */
    public static final int POWER_BOTH = 4;
    /** Where the player stood when the server last answered, matching {@code JumpService.HERE_} on the server. */
    public static final int HERE_OK = 0;
    public static final int HERE_LIMITED = 1;
    public static final int HERE_TOO_FAR = 2;
    public static final int HERE_OTHER_DIMENSION = 3;

    private static boolean linked;
    private static boolean hasBack;
    private static int slots;
    private static int usable;
    private static int power;
    private static long energy;
    private static long capacity;
    private static long rate;
    private static int here;
    private static int range;
    private static boolean[] far = new boolean[0];
    /** Client game time when the power numbers arrived, to count them down between updates. */
    private static long syncedAt;
    private static List<Entry> spots = List.of();
    /** Number of the last edit the server says it handled (see {@code ClientEdits}). */
    private static int editSeq;
    private static int baseSlots = 8;
    private static int baseRange = 64;
    /** The linked anchor's dimension (null: unknown) and block position, for the range dome. */
    private static ResourceLocation anchorDimension;
    private static long anchorPos;
    /** How far the anchor reaches right now, in blocks (-1: no limit). */
    private static int reachNow = -1;

    private ClientState() {
    }

    static void apply(SyncPayload payload) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < payload.names().size(); i++) {
            String icon = payload.icons().get(i);
            entries.add(new Entry(payload.names().get(i), iconStack(icon), icon));
        }
        linked = payload.linked();
        hasBack = payload.hasBack();
        slots = payload.slots();
        usable = Math.max(0, Math.min(payload.usable(), payload.slots()));
        power = payload.power();
        energy = payload.energy();
        capacity = payload.capacity();
        rate = payload.rate();
        here = payload.here();
        range = payload.range();
        far = payload.far();
        syncedAt = gameTime();
        spots = entries;
        editSeq = payload.editSeq();
        if (payload.linked()) {
            baseSlots = payload.baseSlots();
            baseRange = payload.baseRange();
        }
        anchorDimension = payload.linked() ? ResourceLocation.tryParse(payload.anchorDimension()) : null;
        anchorPos = payload.anchorPos();
        reachNow = payload.linked() ? payload.reachNow() : -1;
    }

    private static long gameTime() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }

    static void clear() {
        linked = false;
        hasBack = false;
        slots = 0;
        usable = 0;
        power = POWER_FREE;
        energy = 0;
        capacity = 0;
        rate = 0;
        here = HERE_OK;
        range = 0;
        far = new boolean[0];
        spots = List.of();
        anchorDimension = null;
        anchorPos = 0;
        reachNow = -1;
        // The spot list's Undo steps last until you leave the world.
        SpotListScreen.forgetUndo();
    }

    /** The linked anchor's dimension id, or null if not linked (or the id didn't parse). */
    public static ResourceLocation anchorDimension() {
        return anchorDimension;
    }

    /** The linked anchor's block position, packed (see {@code BlockPos.asLong}). Only meaningful while linked. */
    public static long anchorPos() {
        return anchorPos;
    }

    /** How far the anchor reaches right now in blocks: tier 1's range while it isn't working above tier 1; -1: no limit. */
    public static int reachNow() {
        return reachNow;
    }

    /** True if the anchor works at its real tier right now (needs no power, or has power and its chunk is running). */
    public static boolean active() {
        return power == POWER_FREE || power == POWER_ON;
    }

    /** Tier 1's slots on this server: what still works without power or with the base unloaded. */
    public static int baseSlots() {
        return baseSlots;
    }

    /** Tier 1's range on this server, in blocks (-1: no limit). */
    public static int baseRange() {
        return baseRange;
    }

    /** Number of the last edit the server handled from this client. */
    public static int editSeq() {
        return editSeq;
    }

    /** The item for an icon id; an ender pearl if the id is empty or the item doesn't exist on this client. */
    private static ItemStack iconStack(String id) {
        ResourceLocation key = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
        return new ItemStack(item == Items.AIR ? Items.ENDER_PEARL : item);
    }

    public static boolean linked() {
        return linked;
    }

    /** True if the player has a place to jump back to. */
    public static boolean hasBack() {
        return hasBack;
    }

    public static int slots() {
        return slots;
    }

    public static List<Entry> spots() {
        return spots;
    }

    /** How many of the slots work right now (fewer while the anchor has no power or its base isn't loaded). */
    public static int usable() {
        return usable;
    }

    /** One of the HERE_ constants. */
    public static int here() {
        return here;
    }

    /** The anchor's real range in blocks (-1: no limit). */
    public static int range() {
        return range;
    }

    /** True if the spot at this index is outside what the anchor reaches right now. */
    public static boolean far(int index) {
        return index >= 0 && index < far.length && far[index];
    }

    /** One of the POWER_ constants. */
    public static int power() {
        return power;
    }

    public static long capacity() {
        return capacity;
    }

    public static long rate() {
        return rate;
    }

    /** Stored FE now, estimated: what the server last said, minus the upkeep since (while the anchor is running). */
    public static long energyNow() {
        if (power != POWER_ON || rate <= 0) {
            return energy;
        }
        long elapsed = Math.max(0, gameTime() - syncedAt);
        return elapsed > energy / rate ? 0 : Math.max(0, energy - elapsed * rate);
    }
}
