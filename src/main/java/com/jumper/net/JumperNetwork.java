package com.jumper.net;

import com.jumper.jump.AnchorMenu;
import com.jumper.jump.EditActions;
import com.jumper.jump.JumpService;
import com.jumper.jump.JumperSync;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers Jumper's packets. Everything a client sends is checked on the server before it does anything. */
public final class JumperNetwork {

    private JumperNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // Bumped whenever a packet changes: a client and server with different packet sets refuse to connect.
        PayloadRegistrar registrar = event.registrar("10");
        registrar.playToServer(WheelActionPayload.TYPE, WheelActionPayload.STREAM_CODEC, JumperNetwork::onWheelAction);
        registrar.playToServer(EditPayload.TYPE, EditPayload.STREAM_CODEC, JumperNetwork::onEdit);
        registrar.playToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, JumperNetwork::onSync);
        registrar.playToServer(AnchorPayload.TYPE, AnchorPayload.STREAM_CODEC, JumperNetwork::onAnchor);
        registrar.playToClient(AnchorInfoPayload.TYPE, AnchorInfoPayload.STREAM_CODEC, JumperNetwork::onAnchorInfo);
    }

    public static void sendToServer(WheelActionPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToServer(EditPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToServer(AnchorPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, SyncPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    public static void sendToPlayer(ServerPlayer player, AnchorInfoPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    // Runs on the server thread (NeoForge's default for payload handlers).
    private static void onWheelAction(WheelActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            handleWheelAction(payload, player);
        }
    }

    private static void onEdit(EditPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            EditActions.handle(payload, player);
        }
    }

    private static void onAnchor(AnchorPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            AnchorMenu.handle(payload, player);
        }
    }

    /** Client: only runs on the client, so the client class is never loaded on a server. */
    private static void onSync(SyncPayload payload, IPayloadContext context) {
        com.jumper.client.ClientAccess.applySync(payload);
    }

    /** Client: opens or updates the anchor's menu. */
    private static void onAnchorInfo(AnchorInfoPayload payload, IPayloadContext context) {
        com.jumper.client.ClientAccess.applyAnchorInfo(payload);
    }

    /** What the server does with a wheel pick. JumpService re-checks everything; the answer goes to the action bar. */
    static void handleWheelAction(WheelActionPayload payload, ServerPlayer player) {
        if (payload.action() == WheelActionPayload.STATUS) {
            JumperSync.sendStatus(player);
            return;
        }
        JumpService.Result result;
        if (payload.action() == WheelActionPayload.JUMP) {
            result = JumpService.jumpToIndex(player, payload.index());
        } else if (payload.action() == WheelActionPayload.SAVE) {
            result = JumpService.saveAuto(player);
        } else if (payload.action() == WheelActionPayload.BACK) {
            result = JumpService.back(player);
        } else if (payload.action() == WheelActionPayload.HOME) {
            result = JumpService.home(player);
        } else {
            return;
        }
        player.displayClientMessage(result.message(), true);
        if (!result.ok()) {
            // The client's list may be out of date (that's often why a pick fails); send a fresh one.
            JumperSync.send(player);
        }
    }
}
