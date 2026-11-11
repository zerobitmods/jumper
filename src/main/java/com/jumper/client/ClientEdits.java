package com.jumper.client;

import com.jumper.JumperRules;
import com.jumper.net.EditPayload;
import com.jumper.net.JumperNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/** Sends list edits to the server, and works out the name and picture a new spot would get. Client only. */
public final class ClientEdits {
    /** Numbers each edit; the server sends the last one it handled back with every sync. Only ever goes up. */
    private static int lastSeq;

    private ClientEdits() {
    }

    /** Sends an edit and returns its number. */
    public static int send(byte action, int index, String expected, String text) {
        lastSeq = lastSeq == Integer.MAX_VALUE ? 1 : lastSeq + 1;
        JumperNetwork.sendToServer(new EditPayload(action, index, expected, text, lastSeq));
        return lastSeq;
    }

    /**
     * The item a spot saved here would show: the block looked at (within reach), else the block underfoot, else an
     * ender pearl. The server works it out the same way when it saves; this is only for the name box.
     */
    public static ItemStack suggestedPicture() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return new ItemStack(Items.ENDER_PEARL);
        }
        HitResult hit = player.pick(JumperRules.ICON_REACH, 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            Item looked = player.level().getBlockState(blockHit.getBlockPos()).getBlock().asItem();
            if (looked != Items.AIR) {
                return new ItemStack(looked);
            }
        }
        Item below = player.level().getBlockState(player.getOnPos()).getBlock().asItem();
        return new ItemStack(below != Items.AIR ? below : Items.ENDER_PEARL);
    }

    /** The picture's name, with a number added if a spot is already called that (same rule as the server). */
    public static String suggestedName(ItemStack picture, List<ClientState.Entry> spots) {
        String base = picture.getHoverName().getString().trim();
        if (base.isEmpty()) {
            base = "Spot";
        }
        int room = JumperRules.MAX_NAME_LENGTH - 3;
        if (base.length() > room) {
            base = base.substring(0, room).trim();
        }
        if (!taken(base, spots, -1)) {
            return base;
        }
        for (int number = 2; number < 100; number++) {
            String candidate = base + " " + number;
            if (!taken(candidate, spots, -1)) {
                return candidate;
            }
        }
        return base;
    }

    /** True if a spot other than the one at {@code except} has this name (upper/lower case ignored). */
    public static boolean taken(String name, List<ClientState.Entry> spots, int except) {
        String wanted = name.trim();
        for (int i = 0; i < spots.size(); i++) {
            if (i != except && spots.get(i).name().equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }
}
