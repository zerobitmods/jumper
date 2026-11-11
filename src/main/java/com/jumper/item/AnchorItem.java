package com.jumper.item;

import com.jumper.JumperRules;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Jumper Anchor as an item. A broken anchor keeps its tier on the item (stored power is lost), and placing it
 * gives the new anchor that tier again ({@code JumperAnchorBlock.setPlacedBy}).
 */
public class AnchorItem extends BlockItem {
    /** Item NBT key for the tier (1.20.1 has no data components). */
    private static final String TIER_KEY = "JumperTier";

    public AnchorItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** The tier written on this anchor item: 1 if none. */
    public static int tierOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(TIER_KEY, Tag.TAG_INT)
                ? JumperRules.clampTier(tag.getInt(TIER_KEY)) : JumperRules.MIN_TIER;
    }

    /** Writes the tier onto an anchor item. Tier 1 writes nothing, so plain anchors still stack with crafted ones. */
    public static void setTier(ItemStack stack, int tier) {
        int clamped = JumperRules.clampTier(tier);
        if (clamped <= JumperRules.MIN_TIER) {
            stack.removeTagKey(TIER_KEY);
        } else {
            stack.getOrCreateTag().putInt(TIER_KEY, clamped);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int tier = tierOf(stack);
        if (tier > JumperRules.MIN_TIER) {
            tooltip.add(Component.translatable("tooltip.jumper.anchor_tier", tier).withStyle(ChatFormatting.GREEN));
        }
    }
}
