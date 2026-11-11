package com.jumper.registry;

import com.jumper.Jumper;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Jumper.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.jumper"))
                    .icon(() -> new ItemStack(ModItems.JUMPER_ANCHOR.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.JUMPER_ANCHOR.get());
                        output.accept(ModItems.ANCHOR_UPGRADE_2.get());
                        output.accept(ModItems.ANCHOR_UPGRADE_3.get());
                        output.accept(ModItems.ANCHOR_UPGRADE_4.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
