package com.jumper.client;

import com.jumper.Jumper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Jumper's keys, changeable in Controls under "Jumper". */
@Mod.EventBusSubscriber(modid = Jumper.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class JumperKeys {

    /**
     * Hold to open the jump wheel. Default Shift + R (zerobit's pick): plain R is used by other mods. The id changed
     * from "key.jumper.wheel" in step 5 so the new default applies even where the old plain-R choice was saved.
     */
    public static final KeyMapping WHEEL = new KeyMapping("key.jumper.open_wheel", KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT, InputConstants.Type.KEYSYM, InputConstants.KEY_R, "key.categories.jumper");

    /** Jump back to where you last jumped from, without opening the wheel. Not bound to a key by default. */
    public static final KeyMapping BACK = new KeyMapping("key.jumper.back", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, "key.categories.jumper");

    /** Jump to your anchor, without opening the wheel. Not bound to a key by default. */
    public static final KeyMapping HOME = new KeyMapping("key.jumper.home", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, "key.categories.jumper");

    private JumperKeys() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(WHEEL);
        event.register(BACK);
        event.register(HOME);
    }
}
