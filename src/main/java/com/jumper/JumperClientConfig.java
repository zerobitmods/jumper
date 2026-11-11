package com.jumper;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * The client config (config/jumper-client.toml): this player's own display choices, kept between sessions. Only the
 * range dome switch so far, set from the anchor's menu. Loaded only on a game client; a server never reads it.
 * Per version: only the config classes' names and how it registers differ on 1.21.1.
 */
public final class JumperClientConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue RANGE_DOME;
    /** The switch as last loaded or set, read every frame without asking the config library. */
    private static volatile boolean rangeDome;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Show the range dome around your linked anchor. Turned on and off from the anchor's menu.");
        RANGE_DOME = builder.define("rangeDome", false);
        SPEC = builder.build();
    }

    private JumperClientConfig() {
    }

    public static void register(IEventBus modEventBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC);
        modEventBus.addListener(JumperClientConfig::onLoading);
        modEventBus.addListener(JumperClientConfig::onReloading);
    }

    private static void onLoading(ModConfigEvent.Loading event) {
        onFileChanged(event.getConfig());
    }

    private static void onReloading(ModConfigEvent.Reloading event) {
        onFileChanged(event.getConfig());
    }

    private static void onFileChanged(ModConfig config) {
        if (config.getSpec() == SPEC && SPEC.isLoaded()) {
            rangeDome = RANGE_DOME.get();
        }
    }

    public static boolean rangeDome() {
        return rangeDome;
    }

    /** Turns the dome on or off and saves the file. */
    public static void setRangeDome(boolean on) {
        rangeDome = on;
        if (SPEC.isLoaded()) {
            RANGE_DOME.set(on);
            SPEC.save();
        }
    }
}
