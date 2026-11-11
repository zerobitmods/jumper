package com.jumper;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client config (config/jumper-client.toml): this player's own display choices, kept between sessions. Only the
 * range dome switch so far, set from the anchor's menu. Loaded only on a game client; a server never reads it.
 * Per version: only the config classes' names and how it registers differ on 1.20.1.
 */
public final class JumperClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue RANGE_DOME;
    /** The switch as last loaded or set, read every frame without asking the config library. */
    private static volatile boolean rangeDome;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Show the range dome around your linked anchor. Turned on and off from the anchor's menu.");
        RANGE_DOME = builder.define("rangeDome", false);
        SPEC = builder.build();
    }

    private JumperClientConfig() {
    }

    public static void register(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        modEventBus.addListener(ModConfigEvent.Loading.class, event -> onFileChanged(event.getConfig()));
        modEventBus.addListener(ModConfigEvent.Reloading.class, event -> onFileChanged(event.getConfig()));
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
