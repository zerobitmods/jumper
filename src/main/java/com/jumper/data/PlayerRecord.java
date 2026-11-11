package com.jumper.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What Jumper remembers about one player. */
public final class PlayerRecord {
    /** The anchor this player is linked to, or null. */
    private AnchorKey link;
    /** Saved spots, in the order they were saved. */
    private final List<Spot> spots = new ArrayList<>();
    /** Where the player last jumped from, or null. */
    private Spot back;
    /** Game time of the last jump. Not saved: after a restart the cooldown starts fresh. */
    private long lastJumpTick = Long.MIN_VALUE / 2;
    /** Game time this player's client last got an answer to a status request. Not saved. */
    private long lastStatusTick = Long.MIN_VALUE / 2;
    /** Number the client gave its last edit; sent back with every sync so its list screen knows what it has seen. */
    private int lastEditSeq;
    /** Game time the current one-second edit window started, and edits counted in it. Not saved. */
    private long editWindowTick = Long.MIN_VALUE / 2;
    private int editsInWindow;
    /**
     * Spots this player deleted from the list screen, newest last, so the list's Undo can bring them back. The server
     * keeps these (the client is never trusted with a spot's place). Not saved: a restart forgets them.
     */
    private final List<Spot> deleted = new ArrayList<>();

    /** Most deleted spots kept for Undo per player; the oldest is forgotten first. */
    public static final int MAX_DELETED_KEPT = 20;

    public AnchorKey link() {
        return link;
    }

    void setLink(AnchorKey link) {
        this.link = link;
    }

    /** The saved spots (read-only). */
    public List<Spot> spots() {
        return Collections.unmodifiableList(spots);
    }

    /** Index of the spot with this name (upper/lower case ignored), or -1. */
    public int indexOf(String name) {
        for (int i = 0; i < spots.size(); i++) {
            if (spots.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    List<Spot> spotList() {
        return spots;
    }

    public Spot back() {
        return back;
    }

    void setBack(Spot back) {
        this.back = back;
    }

    public long lastJumpTick() {
        return lastJumpTick;
    }

    public void setLastJumpTick(long tick) {
        this.lastJumpTick = tick;
    }

    public long lastStatusTick() {
        return lastStatusTick;
    }

    public void setLastStatusTick(long tick) {
        this.lastStatusTick = tick;
    }

    public int lastEditSeq() {
        return lastEditSeq;
    }

    public void setLastEditSeq(int seq) {
        this.lastEditSeq = seq;
    }

    /** Keeps a spot deleted from the list, for Undo. */
    public void rememberDeleted(Spot spot) {
        if (deleted.size() >= MAX_DELETED_KEPT) {
            deleted.remove(0);
        }
        deleted.add(spot);
    }

    /** The most recently deleted spot with exactly this name, or null. */
    public Spot findDeleted(String name) {
        for (int i = deleted.size() - 1; i >= 0; i--) {
            if (deleted.get(i).name().equals(name)) {
                return deleted.get(i);
            }
        }
        return null;
    }

    /** Forgets a deleted spot (it was brought back). */
    public void forgetDeleted(Spot spot) {
        for (int i = deleted.size() - 1; i >= 0; i--) {
            if (deleted.get(i) == spot) {
                deleted.remove(i);
                return;
            }
        }
    }

    /** Counts one edit; false once more than {@code max} edits arrive within 20 ticks (a misbehaving client). */
    public boolean allowEdit(long now, int max) {
        if (now - editWindowTick >= 20 || now < editWindowTick) {
            editWindowTick = now;
            editsInWindow = 0;
        }
        return ++editsInWindow <= max;
    }
}
