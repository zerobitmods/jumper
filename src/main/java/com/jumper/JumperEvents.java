package com.jumper;

import com.jumper.command.JumperCommands;
import com.jumper.jump.JumperSync;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Game-event listeners (not mod-loading events). */
public final class JumperEvents {

    private JumperEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(JumperEvents::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(JumperEvents::onPlayerLoggedIn);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        JumperCommands.register(event.getDispatcher());
    }

    /** A player who joins is told their link and spots, so their wheel is ready. */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JumperSync.send(player);
        }
    }
}
