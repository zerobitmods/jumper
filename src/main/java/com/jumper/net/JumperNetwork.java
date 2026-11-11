package com.jumper.net;

import com.jumper.Jumper;
import com.jumper.jump.AnchorMenu;
import com.jumper.jump.EditActions;
import com.jumper.jump.JumpService;
import com.jumper.jump.JumperSync;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Registers Jumper's packets. Everything a client sends is checked on the server before it does anything. */
public final class JumperNetwork {

    /** Bumped whenever a packet changes: a client and server with different packet sets refuse to connect. */
    private static final String PROTOCOL = "f10";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Jumper.MOD_ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private JumperNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(WheelActionPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WheelActionPayload::encode)
                .decoder(WheelActionPayload::decode)
                .consumerMainThread((payload, context) -> {
                    context.get().setPacketHandled(true);
                    ServerPlayer sender = context.get().getSender();
                    if (sender != null) {
                        handleWheelAction(payload, sender);
                    }
                })
                .add();
        CHANNEL.messageBuilder(EditPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(EditPayload::encode)
                .decoder(EditPayload::decode)
                .consumerMainThread((payload, context) -> {
                    context.get().setPacketHandled(true);
                    ServerPlayer sender = context.get().getSender();
                    if (sender != null) {
                        EditActions.handle(payload, sender);
                    }
                })
                .add();
        CHANNEL.messageBuilder(SyncPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPayload::encode)
                .decoder(SyncPayload::decode)
                .consumerMainThread((payload, context) -> {
                    context.get().setPacketHandled(true);
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> onSync(payload));
                })
                .add();
        CHANNEL.messageBuilder(AnchorPayload.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AnchorPayload::encode)
                .decoder(AnchorPayload::decode)
                .consumerMainThread((payload, context) -> {
                    context.get().setPacketHandled(true);
                    ServerPlayer sender = context.get().getSender();
                    if (sender != null) {
                        AnchorMenu.handle(payload, sender);
                    }
                })
                .add();
        CHANNEL.messageBuilder(AnchorInfoPayload.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AnchorInfoPayload::encode)
                .decoder(AnchorInfoPayload::decode)
                .consumerMainThread((payload, context) -> {
                    context.get().setPacketHandled(true);
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> onAnchorInfo(payload));
                })
                .add();
    }

    public static void sendToServer(WheelActionPayload payload) {
        CHANNEL.sendToServer(payload);
    }

    public static void sendToServer(EditPayload payload) {
        CHANNEL.sendToServer(payload);
    }

    public static void sendToServer(AnchorPayload payload) {
        CHANNEL.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, SyncPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToPlayer(ServerPlayer player, AnchorInfoPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    /** Client: only runs on the client, so the client class is never loaded on a server. */
    private static void onSync(SyncPayload payload) {
        com.jumper.client.ClientAccess.applySync(payload);
    }

    /** Client: opens or updates the anchor's menu. */
    private static void onAnchorInfo(AnchorInfoPayload payload) {
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
