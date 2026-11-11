package com.jumper.registry;

import com.jumper.Jumper;
import com.jumper.JumperRules;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Jumper.MOD_ID);

    /** On a Jumper Anchor item: the tier it had when it was broken. Missing means tier 1. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ANCHOR_TIER =
            COMPONENTS.registerComponentType("anchor_tier", builder -> builder
                    .persistent(Codec.intRange(JumperRules.MIN_TIER, JumperRules.MAX_TIER))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
