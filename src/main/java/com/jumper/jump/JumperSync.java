package com.jumper.jump;

import com.jumper.JumperRules;
import com.jumper.data.AnchorKey;
import com.jumper.data.AnchorRecord;
import com.jumper.data.JumperData;
import com.jumper.data.PlayerRecord;
import com.jumper.data.Spot;
import com.jumper.net.JumperNetwork;
import com.jumper.net.SyncPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells a player's client what its wheel should show: whether they're linked, how many slots they have (and how many
 * work right now), each spot's name and picture, whether there's a place to jump back to, and the anchor's power. Sent
 * when something changes (join, link, save, remove, jump, anchor broken) and when the wheel opens, never on a timer.
 */
public final class JumperSync {

    private JumperSync() {
    }

    public static void send(ServerPlayer player) {
        JumperData data = JumperData.get(player.server);
        PlayerRecord record = data.player(player.getUUID());
        AnchorRecord anchor = record == null ? null : data.anchor(record.link());
        if (anchor == null) {
            JumperNetwork.sendToPlayer(player, SyncPayload.notLinked(record == null ? 0 : record.lastEditSeq()));
            return;
        }
        JumpService.Power power = JumpService.power(player.server, record.link(), anchor);
        int slots = JumperRules.slots(anchor.tier());
        // Without power, or with the base unloaded, only tier 1's slots work; the wheel shows the rest dimmed.
        int usable = power.active() ? slots : Math.min(slots, JumperRules.slots(JumperRules.MIN_TIER));
        int workingTier = power.active() ? anchor.tier() : JumperRules.MIN_TIER;
        List<String> names = new ArrayList<>();
        List<String> icons = new ArrayList<>();
        List<Spot> spots = record.spots();
        // Spots past the slot count are locked (saved on a bigger anchor), so the wheel doesn't show them.
        int count = Math.min(spots.size(), Math.min(slots, SyncPayload.MAX_SPOTS));
        boolean[] far = new boolean[count];
        for (int i = 0; i < count; i++) {
            names.add(spots.get(i).name());
            icons.add(spots.get(i).icon());
            far[i] = !JumpService.inReach(record.link(), workingTier, spots.get(i));
        }
        JumperNetwork.sendToPlayer(player, new SyncPayload(true, record.back() != null, slots, usable,
                (byte) power.ordinal(), anchor.energy(), anchor.capacity(), anchor.rate(),
                JumpService.here(player, record.link(), anchor, power), JumperRules.range(anchor.tier()), names, icons,
                far, record.lastEditSeq(), JumperRules.slots(JumperRules.MIN_TIER),
                JumperRules.range(JumperRules.MIN_TIER), record.link().dimension(), record.link().pos(),
                JumperRules.reach(workingTier)));
    }

    /** Fewest ticks between two status requests a player's client may get answered (the wheel asks on opening). */
    private static final int STATUS_MIN_TICKS = 10;

    /** The wheel just opened: send a fresh list and power, at most once every {@link #STATUS_MIN_TICKS} ticks. */
    public static void sendStatus(ServerPlayer player) {
        PlayerRecord record = JumperData.get(player.server).player(player.getUUID());
        long now = player.server.overworld().getGameTime();
        if (record != null) {
            long since = now - record.lastStatusTick();
            if (since >= 0 && since < STATUS_MIN_TICKS) {
                return;
            }
            record.setLastStatusTick(now);
        }
        send(player);
    }

    /** Updates every online player (after a config change). */
    public static void sendToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player);
        }
    }

    /** Updates every online player linked to this anchor (it was just removed or changed). */
    public static void sendToLinked(MinecraftServer server, AnchorKey key) {
        JumperData data = JumperData.get(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerRecord record = data.player(player.getUUID());
            if (record != null && key.equals(record.link())) {
                send(player);
            }
        }
    }
}
