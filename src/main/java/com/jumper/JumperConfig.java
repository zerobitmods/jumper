package com.jumper;

import com.jumper.data.JumperData;
import com.jumper.jump.JumperSync;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The server config (world/serverconfig/jumper-server.toml; a copy in the pack's defaultconfigs/ folder is used for new
 * worlds). Built from {@link JumperRules#SETTINGS}; every value is copied into JumperRules when the file loads or
 * changes, and by {@code /jumper config}. Per version: the config classes' names and registration differ between 1.20.1 and 1.21.1.
 */
public final class JumperConfig {
    public static final ForgeConfigSpec SPEC;
    private static final Map<String, ForgeConfigSpec.ConfigValue<?>> VALUES = new LinkedHashMap<>();

    // Every number fits in an int (see the limits in JumperRules), so they're plain int values in the file.
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        for (JumperRules.Setting setting : JumperRules.SETTINGS) {
            builder.comment(setting.comment());
            VALUES.put(setting.key(), setting.isBoolean()
                    ? builder.define(setting.key(), setting.defaultValue() != 0)
                    : builder.defineInRange(setting.key(), (int) setting.defaultValue(), (int) setting.min(),
                            (int) setting.max()));
        }
        SPEC = builder.build();
    }

    private JumperConfig() {
    }

    public static void register(IEventBus modEventBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SPEC);
        modEventBus.addListener(JumperConfig::onLoading);
        modEventBus.addListener(JumperConfig::onReloading);
    }

    private static void onLoading(ModConfigEvent.Loading event) {
        onFileChanged(event.getConfig(), false);
    }

    private static void onReloading(ModConfigEvent.Reloading event) {
        onFileChanged(event.getConfig(), true);
    }

    /** Loading: the server is starting (no world data yet). Reloading: edited while running, maybe on another thread. */
    private static void onFileChanged(ModConfig config, boolean whileRunning) {
        if (config.getSpec() != SPEC) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (!whileRunning || server == null) {
            applyAll();
        } else {
            server.execute(() -> {
                applyAll();
                changed(server);
            });
        }
    }

    private static void applyAll() {
        if (!SPEC.isLoaded()) {
            return;
        }
        for (Map.Entry<String, ForgeConfigSpec.ConfigValue<?>> entry : VALUES.entrySet()) {
            JumperRules.apply(entry.getKey(), asLong(entry.getValue().get()));
        }
    }

    /** After a change on a running server: recount anchors and update every player's wheel. Server thread only. */
    private static void changed(MinecraftServer server) {
        JumperData.get(server).configChanged();
        JumperSync.sendToAll(server);
    }

    private static long asLong(Object value) {
        if (value instanceof Boolean flag) {
            return flag ? 1 : 0;
        }
        return value instanceof Number number ? number.longValue() : 0;
    }

    public static boolean loaded() {
        return SPEC.isLoaded();
    }

    /** The current value as text, or null if there's no such setting. */
    public static String get(String key) {
        ForgeConfigSpec.ConfigValue<?> value = VALUES.get(key);
        return value == null || !SPEC.isLoaded() ? null : String.valueOf(value.get());
    }

    /**
     * Sets a value from {@code /jumper config}, saves the file and applies it. Server thread only. Returns null if it
     * worked, else the key of the message saying why not (its arguments: key, min, max).
     */
    @SuppressWarnings("unchecked")
    public static String set(MinecraftServer server, String key, String text) {
        JumperRules.Setting setting = JumperRules.setting(key);
        ForgeConfigSpec.ConfigValue<?> value = VALUES.get(key);
        if (setting == null || value == null) {
            return "config_unknown";
        }
        if (!SPEC.isLoaded()) {
            return "config_not_loaded";
        }
        if (setting.isBoolean()) {
            String lower = text.toLowerCase(Locale.ROOT);
            if (!lower.equals("true") && !lower.equals("false")) {
                return "config_bad_flag";
            }
            ((ForgeConfigSpec.ConfigValue<Boolean>) value).set(lower.equals("true"));
        } else {
            long number;
            try {
                number = Long.parseLong(text);
            } catch (NumberFormatException e) {
                return "config_bad_number";
            }
            if (number < setting.min() || number > setting.max()) {
                return "config_bad_number";
            }
            ((ForgeConfigSpec.ConfigValue<Integer>) value).set((int) number);
        }
        SPEC.save();
        applyAll();
        changed(server);
        return null;
    }
}
