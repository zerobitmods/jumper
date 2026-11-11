package com.jumper.jump;

import com.jumper.JumperRules;
import com.jumper.block.JumperAnchorBlock;
import com.jumper.data.AnchorKey;
import com.jumper.data.AnchorRecord;
import com.jumper.data.JumperData;
import com.jumper.data.PlayerRecord;
import com.jumper.net.AnchorInfoPayload;
import com.jumper.net.AnchorPayload;
import com.jumper.net.JumperNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The anchor's right-click menu, server side: opening it (with the numbers it shows), linking from its button, and the
 * once-a-second refresh while it is open. Everything a client asks is checked here: the block must be an anchor, in a
 * loaded chunk, close to the player. Server thread only.
 */
public final class AnchorMenu {
    /** Farthest a player may link from, in blocks from the middle of the anchor (as far as vanilla keeps a chest open). */
    private static final double LINK_REACH = 8.0;
    /** Farthest an open menu keeps getting numbers; further away it is told to close. */
    private static final double INFO_REACH = 16.0;
    /** Fewest ticks between two refreshes a player's client may get answered. */
    private static final int INFO_MIN_TICKS = 10;
    /** Game time of each player's last answered refresh. Weak keys: an entry goes when its player object does. */
    private static final Map<ServerPlayer, long[]> LAST_INFO = new WeakHashMap<>();

    private AnchorMenu() {
    }

    /** The player right-clicked this anchor: open its menu on their screen. */
    public static void open(ServerPlayer player, Level level, BlockPos pos) {
        JumperData data = JumperData.get(player.server);
        // ensureAnchor also repairs the list if this anchor is somehow missing from it.
        AnchorRecord anchor = data.ensureAnchor(level, pos);
        JumperNetwork.sendToPlayer(player, info(player, data, JumperData.key(level, pos), anchor, true));
    }

    /** What the server does with a click or refresh from the menu. The anchor is in the player's own dimension. */
    public static void handle(AnchorPayload payload, ServerPlayer player) {
        Level level = player.level();
        BlockPos pos = BlockPos.of(payload.pos());
        if (payload.action() == AnchorPayload.LINK) {
            if (!anchorNear(player, level, pos, LINK_REACH)) {
                JumperNetwork.sendToPlayer(player, AnchorInfoPayload.gone(payload.pos()));
                return;
            }
            link(player, level, pos);
        } else if (payload.action() == AnchorPayload.INFO) {
            if (!allowRefresh(player)) {
                return;
            }
            if (!anchorNear(player, level, pos, INFO_REACH)) {
                JumperNetwork.sendToPlayer(player, AnchorInfoPayload.gone(payload.pos()));
                return;
            }
            JumperData data = JumperData.get(player.server);
            AnchorRecord anchor = data.ensureAnchor(level, pos);
            JumperNetwork.sendToPlayer(player, info(player, data, JumperData.key(level, pos), anchor, false));
        }
    }

    /** Links the player to this anchor (one link per player): message, fresh wheel, and the menu updated in place. */
    private static void link(ServerPlayer player, Level level, BlockPos pos) {
        JumperData data = JumperData.get(player.server);
        AnchorRecord anchor = data.ensureAnchor(level, pos);
        boolean changed = data.link(player.getUUID(), level, pos);
        player.displayClientMessage(linkMessage(changed, anchor.tier()), true);
        JumperSync.send(player);
        JumperNetwork.sendToPlayer(player, info(player, data, JumperData.key(level, pos), anchor, false));
    }

    private static Component linkMessage(boolean changed, int tier) {
        int range = JumperRules.range(tier);
        int slots = JumperRules.slots(tier);
        if (range == JumperRules.UNLIMITED) {
            return Component.translatable(changed ? "message.jumper.linked_unlimited"
                    : "message.jumper.already_linked_unlimited", slots);
        }
        return Component.translatable(changed ? "message.jumper.linked" : "message.jumper.already_linked", range, slots);
    }

    /** The menu's numbers. Power and spots only for the player's own anchor; anyone may see an anchor's tier. */
    private static AnchorInfoPayload info(ServerPlayer player, JumperData data, AnchorKey key, AnchorRecord anchor,
            boolean open) {
        PlayerRecord record = data.player(player.getUUID());
        AnchorKey linked = record == null ? null : record.link();
        int tier = anchor.tier();
        int baseRange = JumperRules.range(JumperRules.MIN_TIER);
        if (record == null || !key.equals(linked)) {
            byte link = linked != null && data.anchor(linked) != null
                    ? AnchorInfoPayload.LINK_ELSEWHERE : AnchorInfoPayload.LINK_NONE;
            return new AnchorInfoPayload(key.pos(), open, (byte) tier, JumperRules.range(tier),
                    JumperRules.crossDimension(tier), link, (byte) 0, 0, 0, 0, 0, 0, 0, baseRange);
        }
        JumpService.Power power = JumpService.power(player.server, key, anchor);
        int slots = JumperRules.slots(tier);
        int usable = power.active() ? slots : Math.min(slots, JumperRules.slots(JumperRules.MIN_TIER));
        return new AnchorInfoPayload(key.pos(), open, (byte) tier, JumperRules.range(tier),
                JumperRules.crossDimension(tier), AnchorInfoPayload.LINK_HERE, (byte) power.ordinal(),
                anchor.energy(), anchor.capacity(), anchor.rate(), Math.min(record.spots().size(), slots), slots,
                usable, baseRange);
    }

    /** True if there is an anchor at pos, its chunk is loaded (never loads one), and the player is within reach. */
    private static boolean anchorNear(ServerPlayer player, Level level, BlockPos pos, double reach) {
        if (!player.isAlive() || player.isSpectator() || !level.isLoaded(pos)
                || !(level.getBlockState(pos).getBlock() instanceof JumperAnchorBlock)) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= reach * reach;
    }

    /** At most one answered refresh every {@link #INFO_MIN_TICKS} ticks per player. */
    private static boolean allowRefresh(ServerPlayer player) {
        long now = player.server.overworld().getGameTime();
        long[] last = LAST_INFO.computeIfAbsent(player, ignored -> new long[] {Long.MIN_VALUE / 2});
        long since = now - last[0];
        if (since >= 0 && since < INFO_MIN_TICKS) {
            return false;
        }
        last[0] = now;
        return true;
    }
}
