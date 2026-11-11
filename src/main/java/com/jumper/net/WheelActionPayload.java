package com.jumper.net;

import com.jumper.Jumper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: the player picked something on the wheel. The server checks everything again. */
public record WheelActionPayload(byte action, int index) implements CustomPacketPayload {
    /** Jump to the spot at {@link #index} of the player's list. */
    public static final byte JUMP = 0;
    /** Save the place the player is standing as a new spot (index unused). */
    public static final byte SAVE = 1;
    /** Jump back to where the player last jumped from (index unused). Also sent by the "Jump back" key. */
    public static final byte BACK = 2;
    /** Jump to the player's anchor (index unused). Also sent by the "Jump home" key. */
    public static final byte HOME = 3;
    /** Send me my wheel's contents and the anchor's power again (index unused). Sent when the wheel opens. */
    public static final byte STATUS = 4;

    public static final Type<WheelActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Jumper.MOD_ID, "wheel_action"));

    public static final StreamCodec<FriendlyByteBuf, WheelActionPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.encode(buf), WheelActionPayload::decode);

    public static WheelActionPayload jump(int index) {
        return new WheelActionPayload(JUMP, index);
    }

    public static WheelActionPayload save() {
        return new WheelActionPayload(SAVE, 0);
    }

    public static WheelActionPayload back() {
        return new WheelActionPayload(BACK, 0);
    }

    public static WheelActionPayload home() {
        return new WheelActionPayload(HOME, 0);
    }

    public static WheelActionPayload status() {
        return new WheelActionPayload(STATUS, 0);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeVarInt(index);
    }

    public static WheelActionPayload decode(FriendlyByteBuf buf) {
        return new WheelActionPayload(buf.readByte(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
