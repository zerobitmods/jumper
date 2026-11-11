package com.jumper.client;

import com.jumper.Jumper;
import com.jumper.item.AnchorItem;
import com.jumper.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Lets the anchor item show its tier (step F16). The item model {@code models/item/jumper_anchor.json} picks the
 * tier's block model through the item property {@code jumper:tier}, which is the tier written on the stack (1 to 4).
 */
@Mod.EventBusSubscriber(modid = Jumper.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class JumperItemModels {

    public static final ResourceLocation TIER = new ResourceLocation(Jumper.MOD_ID, "tier");

    /** Read every frame for every anchor item drawn: only an NBT lookup, no allocations. */
    private static final ItemPropertyFunction ANCHOR_TIER = (stack, level, entity, seed) -> AnchorItem.tierOf(stack);

    private JumperItemModels() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // ItemProperties is a plain map, not thread-safe: register on the main thread.
        event.enqueueWork(() -> ItemProperties.register(ModItems.JUMPER_ANCHOR.get(), TIER, ANCHOR_TIER));
    }
}
