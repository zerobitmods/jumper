package com.jumper.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> server: something done in an anchor's menu. pos is the anchor's block position (packed), in the dimension
 * the player is in. The server checks the block is an anchor and the player is close to it before doing anything.
 */
public record AnchorPayload(byte action, long pos) {
    /** Link me to this anchor (the menu's "Link to this anchor" button). */
    public static final byte LINK = 0;
    /** Send this anchor's menu numbers again (the open menu asks once a second). */
    public static final byte INFO = 1;

    public static AnchorPayload link(BlockPos pos) {
        return new AnchorPayload(LINK, pos.asLong());
    }

    public static AnchorPayload info(BlockPos pos) {
        return new AnchorPayload(INFO, pos.asLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeLong(pos);
    }

    public static AnchorPayload decode(FriendlyByteBuf buf) {
        return new AnchorPayload(buf.readByte(), buf.readLong());
    }
}
