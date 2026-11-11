package com.jumper.item;

import com.jumper.JumperRules;
import com.jumper.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The Jumper Anchor as an item. A broken anchor keeps its tier on the item (stored power is lost), and placing it
 * gives the new anchor that tier again ({@code JumperAnchorBlock.setPlacedBy}).
 */
public class AnchorItem extends BlockItem {

    public AnchorItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** The tier written on this anchor item: 1 if none. */
    public static int tierOf(ItemStack stack) {
        Integer tier = stack.get(ModDataComponents.ANCHOR_TIER.get());
        return tier == null ? JumperRules.MIN_TIER : JumperRules.clampTier(tier);
    }

    /** Writes the tier onto an anchor item. Tier 1 writes nothing, so plain anchors still stack with crafted ones. */
    public static void setTier(ItemStack stack, int tier) {
        int clamped = JumperRules.clampTier(tier);
        if (clamped <= JumperRules.MIN_TIER) {
            stack.remove(ModDataComponents.ANCHOR_TIER.get());
        } else {
            stack.set(ModDataComponents.ANCHOR_TIER.get(), clamped);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int tier = tierOf(stack);
        if (tier > JumperRules.MIN_TIER) {
            tooltip.add(Component.translatable("tooltip.jumper.anchor_tier", tier).withStyle(ChatFormatting.GREEN));
        }
    }
}
