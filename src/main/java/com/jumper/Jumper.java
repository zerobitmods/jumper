package com.jumper;

import com.jumper.net.JumperNetwork;
import com.jumper.registry.ModBlockEntities;
import com.jumper.registry.ModBlocks;
import com.jumper.registry.ModCreativeTabs;
import com.jumper.registry.ModDataComponents;
import com.jumper.registry.ModItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Jumper: fast travel inside your own base. Main class for Minecraft 1.21.1 + NeoForge.
 */
@Mod(Jumper.MOD_ID)
public final class Jumper {
    public static final String MOD_ID = "jumper";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Development step this build is at; bumped with every step. */
    public static final String DEV_STEP = "19a";

    public Jumper(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        JumperEvents.register();
        JumperConfig.register(modEventBus, modContainer);
        JumperClientConfig.register(modEventBus, modContainer);
        modEventBus.addListener(JumperNetwork::register);
        modEventBus.addListener(this::commonSetup);
    }

    /** The mod's version from its metadata (gradle.properties mod_version), e.g. "1.0.0". */
    public static String version() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("?");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Jumper loaded (step {})", DEV_STEP);
    }
}
