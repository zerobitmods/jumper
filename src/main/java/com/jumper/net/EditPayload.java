package com.jumper.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> server: a change to the player's list of spots, from the name box or the list screen. index and expected
 * say which spot (the server refuses if the spot at index isn't named expected, so a stale screen can't change the
 * wrong one); text is the new name or an item id. seq is the client's number for this edit, sent back with the next sync. The server
 * checks everything again.
 */
public record EditPayload(byte action, int index, String expected, String text, int seq) {
    /** Save where the player stands as a new spot named {@link #text} (index and expected unused). */
    public static final byte SAVE = 0;
    /** Rename the spot to {@link #text}. */
    public static final byte RENAME = 1;
    /** Use the item in the player's main hand as the spot's picture. */
    public static final byte PICTURE = 2;
    /** Move the spot one place earlier. */
    public static final byte MOVE_UP = 3;
    /** Move the spot one place later. */
    public static final byte MOVE_DOWN = 4;
    /** Delete the spot. */
    public static final byte DELETE = 5;
    /**
     * Undo of a delete: bring back the spot named {@link #expected} at {@link #index}. Only works for a spot the server
     * kept when it was deleted; the client never sends a place.
     */
    public static final byte RESTORE = 6;
    /** Undo of a picture change: set the spot's picture to the item with id {@link #text}. */
    public static final byte PICTURE_SET = 7;

    /** Longest text either string may carry (names are shorter; item ids can be longer; the server checks both). */
    private static final int MAX_TEXT = 256;

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeVarInt(index);
        buf.writeUtf(clip(expected), MAX_TEXT);
        buf.writeUtf(clip(text), MAX_TEXT);
        buf.writeVarInt(seq);
    }

    private static String clip(String value) {
        return value.length() > MAX_TEXT ? value.substring(0, MAX_TEXT) : value;
    }

    public static EditPayload decode(FriendlyByteBuf buf) {
        return new EditPayload(buf.readByte(), buf.readVarInt(), buf.readUtf(MAX_TEXT), buf.readUtf(MAX_TEXT),
                buf.readVarInt());
    }
}
