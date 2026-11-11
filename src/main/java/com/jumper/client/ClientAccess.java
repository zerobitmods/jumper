package com.jumper.client;

import com.jumper.net.AnchorInfoPayload;
import com.jumper.net.SyncPayload;
import net.minecraft.client.Minecraft;

/**
 * The only place common code calls into client-only code. Only ever called on the client, so this class (and the
 * client classes behind it) is never loaded on a dedicated server.
 */
public final class ClientAccess {
    private ClientAccess() {
    }

    public static void applySync(SyncPayload payload) {
        ClientState.apply(payload);
    }

    /**
     * The server's answer about an anchor: opens its menu (after a right-click, only if no other screen is open), or
     * updates the menu already open on that anchor, or closes it if the anchor is gone or out of reach.
     */
    public static void applyAnchorInfo(AnchorInfoPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.screen instanceof AnchorScreen menu && menu.pos() == payload.pos()) {
            if (payload.tier() <= 0) {
                menu.onClose();
            } else {
                menu.update(payload);
            }
        } else if (payload.open() && payload.tier() > 0 && minecraft.screen == null) {
            minecraft.setScreen(new AnchorScreen(payload));
        }
    }
}
