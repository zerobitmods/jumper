package com.jumper.jump;

import com.jumper.JumperRules;
import com.jumper.block.JumperAnchorBlock;
import com.jumper.data.AnchorKey;
import com.jumper.data.AnchorRecord;
import com.jumper.data.JumperData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Putting an upgrade into an anchor. Server only. Upgrades go one tier at a time, in order: a Tier 3 upgrade only works
 * on a tier 2 anchor. The item is used up only when the tier really changes (never in creative).
 */
public final class Upgrades {

    private Upgrades() {
    }

    /**
     * Tries to raise the anchor at {@code pos} to {@code target} with the upgrade in {@code stack}, and tells the player
     * how it went on the action bar.
     */
    public static void apply(Player player, Level level, BlockPos pos, ItemStack stack, int target) {
        MinecraftServer server = level.getServer();
        if (server == null || stack.isEmpty()) {
            return;
        }
        JumperData data = JumperData.get(server);
        // ensureAnchor also repairs the list if this anchor is somehow missing from it.
        AnchorRecord anchor = data.ensureAnchor(level, pos);
        int tier = anchor.tier();
        if (tier >= target) {
            tell(player, Component.translatable("message.jumper.upgrade_already", tier, target), ChatFormatting.RED);
            return;
        }
        if (tier != target - 1) {
            tell(player, Component.translatable("message.jumper.upgrade_order", tier, tier + 1), ChatFormatting.RED);
            return;
        }
        AnchorKey key = JumperData.key(level, pos);
        if (!data.setTier(key, target)) {
            return;
        }
        JumperAnchorBlock.showTier(level, pos, target);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.0F, 1.0F);
        int range = JumperRules.range(target);
        int slots = JumperRules.slots(target);
        tell(player, range == JumperRules.UNLIMITED
                ? Component.translatable("message.jumper.upgraded_unlimited", target, slots)
                : Component.translatable("message.jumper.upgraded", target, range, slots), ChatFormatting.GREEN);
        // Everyone linked here sees the new slots and range on their wheel right away.
        JumperSync.sendToLinked(server, key);
    }

    private static void tell(Player player, MutableComponent message, ChatFormatting color) {
        player.displayClientMessage(message.withStyle(color), true);
    }
}
