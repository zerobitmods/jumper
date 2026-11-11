package com.jumper.net;

import com.jumper.Jumper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: what an anchor's menu shows. open: the player just right-clicked the anchor, so open the menu (else
 * only update a menu already open on this anchor). tier 0 means the anchor is gone or out of reach: close the menu.
 * range: the tier's real range in blocks (-1: no limit); anyDimension: the tier reaches other dimensions. link: one of
 * the LINK_ values. The power and spot numbers are only filled in for the player's own anchor (LINK_HERE): power is a
 * {@code JumpService.Power} ordinal, energy and capacity in FE, rate in FE per tick, spots = the player's spots that
 * fit, slots = the real slot count, usable = how many work right now. baseRange: tier 1's range (-1: no limit).
 */
public record AnchorInfoPayload(long pos, boolean open, byte tier, int range, boolean anyDimension, byte link,
        byte power, long energy, long capacity, long rate, int spots, int slots, int usable, int baseRange)
        implements CustomPacketPayload {
    /** The player isn't linked to any anchor. */
    public static final byte LINK_NONE = 0;
    /** The player is linked to this anchor. */
    public static final byte LINK_HERE = 1;
    /** The player is linked to a different anchor. */
    public static final byte LINK_ELSEWHERE = 2;

    public static final Type<AnchorInfoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jumper.MOD_ID, "anchor_info"));

    public static final StreamCodec<FriendlyByteBuf, AnchorInfoPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.encode(buf), AnchorInfoPayload::decode);

    /** Tells an open menu on this anchor to close. */
    public static AnchorInfoPayload gone(long pos) {
        return new AnchorInfoPayload(pos, false, (byte) 0, 0, false, LINK_NONE, (byte) 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(pos);
        buf.writeBoolean(open);
        buf.writeByte(tier);
        buf.writeInt(range);
        buf.writeBoolean(anyDimension);
        buf.writeByte(link);
        buf.writeByte(power);
        buf.writeVarLong(energy);
        buf.writeVarLong(capacity);
        buf.writeVarLong(rate);
        buf.writeVarInt(spots);
        buf.writeVarInt(slots);
        buf.writeVarInt(usable);
        buf.writeInt(baseRange);
    }

    public static AnchorInfoPayload decode(FriendlyByteBuf buf) {
        return new AnchorInfoPayload(buf.readLong(), buf.readBoolean(), buf.readByte(), buf.readInt(),
                buf.readBoolean(), buf.readByte(), buf.readByte(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
