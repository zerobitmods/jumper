package com.jumper.registry;

import com.jumper.Jumper;
import com.jumper.block.AnchorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Jumper.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnchorBlockEntity>> JUMPER_ANCHOR =
            BLOCK_ENTITIES.register("jumper_anchor", () -> BlockEntityType.Builder
                    .of(AnchorBlockEntity::new, ModBlocks.JUMPER_ANCHOR.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(ModBlockEntities::registerCapabilities);
    }

    /** The anchor takes FE from any side through NeoForge's standard energy capability. */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, JUMPER_ANCHOR.get(),
                (anchor, side) -> anchor.energyPort());
    }
}
