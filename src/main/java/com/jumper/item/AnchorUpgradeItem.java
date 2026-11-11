package com.jumper.item;

import com.jumper.jump.Upgrades;
import com.jumper.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An anchor upgrade: right-click a Jumper Anchor that is exactly one tier below to raise it to {@link #tier()}. The
 * anchor block passes the click on to this item (sneaking or not), and {@link Upgrades} does all the checks on the
 * server.
 */
public class AnchorUpgradeItem extends Item {
    private final int tier;

    public AnchorUpgradeItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    /** The tier this upgrade raises an anchor to. */
    public int tier() {
        return tier;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (context.getPlayer() == null || !level.getBlockState(context.getClickedPos()).is(ModBlocks.JUMPER_ANCHOR.get())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Upgrades.apply(context.getPlayer(), level, context.getClickedPos(), context.getItemInHand(), tier);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.jumper.upgrade.from", tier - 1, tier).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.jumper.upgrade.how").withStyle(ChatFormatting.GRAY));
    }
}
