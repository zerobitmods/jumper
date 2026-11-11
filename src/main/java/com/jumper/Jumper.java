package com.jumper;

import com.jumper.net.JumperNetwork;
import com.jumper.registry.ModBlockEntities;
import com.jumper.registry.ModBlocks;
import com.jumper.registry.ModCreativeTabs;
import com.jumper.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Jumper: fast travel inside your own base. Main class for Minecraft 1.20.1 + Forge.
 */
@Mod(Jumper.MOD_ID)
public final class Jumper {
    public static final String MOD_ID = "jumper";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Development step this build is at; bumped with every step. */
    public static final String DEV_STEP = "F19a";

    public Jumper() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        JumperEvents.register();
        JumperConfig.register(modEventBus);
        JumperClientConfig.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    /** The mod's version from its metadata (gradle.properties mod_version), e.g. "1.0.0". */
    public static String version() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("?");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(JumperNetwork::register);
        LOGGER.info("Jumper loaded (step {})", DEV_STEP);
    }
}
