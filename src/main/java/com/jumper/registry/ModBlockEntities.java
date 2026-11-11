package com.jumper.registry;

import com.jumper.Jumper;
import com.jumper.block.AnchorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Block entity types. On Forge 1.20.1 the anchor hands out its energy capability itself (AnchorBlockEntity). */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Jumper.MOD_ID);

    public static final RegistryObject<BlockEntityType<AnchorBlockEntity>> JUMPER_ANCHOR =
            BLOCK_ENTITIES.register("jumper_anchor", () -> BlockEntityType.Builder
                    .of(AnchorBlockEntity::new, ModBlocks.JUMPER_ANCHOR.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
