package com.jumper.registry;

import com.jumper.Jumper;
import com.jumper.block.JumperAnchorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Jumper.MOD_ID);

    /**
     * The Jumper Anchor. As blast resistant as obsidian so a creeper can't take out a base's anchor, and pistons can't
     * move it (its position is what jumps are measured from).
     */
    public static final RegistryObject<JumperAnchorBlock> JUMPER_ANCHOR = BLOCKS.register("jumper_anchor",
            () -> new JumperAnchorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> 7)));

    private ModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
