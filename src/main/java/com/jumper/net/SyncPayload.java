package com.jumper.net;

import com.jumper.JumperRules;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: what the player's wheel should show. names.get(i) and icons.get(i) describe the spot at index i of
 * the player's list; an icon is an item id like "minecraft:furnace" or "". hasBack: there is a place to jump back to.
 * slots: the anchor's real slot count; usable: how many of them work right now (fewer without power or with the base
 * unloaded). power: a {@code JumpService.Power} ordinal. energy, capacity (FE) and rate (FE per tick): the anchor's power.
 * here: where the player stood when this was sent (a {@code JumpService.HERE_} value). range: the anchor's real range in
 * blocks, or -1 for no limit. far[i]: spot i is outside what the anchor reaches right now. editSeq: the number of
 * the last edit the server handled from this client (see {@code EditPayload}). baseSlots and baseRange: tier 1's slots
 * and range on this server (what an anchor falls back to without power or with its base unloaded; -1 = no limit).
 * anchorDimension and anchorPos: where the linked anchor is (for the range dome). reachNow: how far it reaches right now
 * in blocks (tier 1's range while it isn't working above tier 1; -1: no limit, or it reaches other dimensions).
 */
public record SyncPayload(boolean linked, boolean hasBack, int slots, int usable, byte power, long energy,
        long capacity, long rate, byte here, int range, List<String> names, List<String> icons, boolean[] far,
        int editSeq, int baseSlots, int baseRange, String anchorDimension, long anchorPos, int reachNow) {
    /** Most spots one message may carry. */
    public static final int MAX_SPOTS = 256;
    private static final int MAX_ICON_LENGTH = 128;
    private static final int MAX_DIMENSION_LENGTH = 256;

    public static SyncPayload notLinked(int editSeq) {
        return new SyncPayload(false, false, 0, 0, (byte) 0, 0, 0, 0, (byte) 0, 0, List.of(), List.of(), new boolean[0],
                editSeq, 0, 0, "", 0, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(linked);
        buf.writeBoolean(hasBack);
        buf.writeVarInt(slots);
        buf.writeVarInt(usable);
        buf.writeByte(power);
        buf.writeVarLong(energy);
        buf.writeVarLong(capacity);
        buf.writeVarLong(rate);
        buf.writeByte(here);
        buf.writeInt(range);
        buf.writeVarInt(names.size());
        for (int i = 0; i < names.size(); i++) {
            buf.writeUtf(clip(names.get(i), JumperRules.MAX_NAME_LENGTH), JumperRules.MAX_NAME_LENGTH);
            String icon = icons.get(i);
            buf.writeUtf(icon.length() > MAX_ICON_LENGTH ? "" : icon, MAX_ICON_LENGTH);
            buf.writeBoolean(i < far.length && far[i]);
        }
        buf.writeVarInt(editSeq);
        buf.writeVarInt(baseSlots);
        buf.writeInt(baseRange);
        buf.writeUtf(anchorDimension.length() > MAX_DIMENSION_LENGTH ? "" : anchorDimension, MAX_DIMENSION_LENGTH);
        buf.writeLong(anchorPos);
        buf.writeInt(reachNow);
    }

    /** Never longer than the limit, so an odd saved name can't make the packet fail. */
    private static String clip(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }

    public static SyncPayload decode(FriendlyByteBuf buf) {
        boolean linked = buf.readBoolean();
        boolean hasBack = buf.readBoolean();
        int slots = buf.readVarInt();
        int usable = buf.readVarInt();
        byte power = buf.readByte();
        long energy = buf.readVarLong();
        long capacity = buf.readVarLong();
        long rate = buf.readVarLong();
        byte here = buf.readByte();
        int range = buf.readInt();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_SPOTS) {
            throw new DecoderException("Too many Jumper spots: " + count);
        }
        List<String> names = new ArrayList<>(count);
        List<String> icons = new ArrayList<>(count);
        boolean[] far = new boolean[count];
        for (int i = 0; i < count; i++) {
            names.add(buf.readUtf(JumperRules.MAX_NAME_LENGTH));
            icons.add(buf.readUtf(MAX_ICON_LENGTH));
            far[i] = buf.readBoolean();
        }
        int editSeq = buf.readVarInt();
        int baseSlots = buf.readVarInt();
        int baseRange = buf.readInt();
        String anchorDimension = buf.readUtf(MAX_DIMENSION_LENGTH);
        long anchorPos = buf.readLong();
        int reachNow = buf.readInt();
        return new SyncPayload(linked, hasBack, slots, usable, power, energy, capacity, rate, here, range, names,
                icons, far, editSeq, baseSlots, baseRange, anchorDimension, anchorPos, reachNow);
    }
}
