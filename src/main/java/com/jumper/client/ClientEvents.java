package com.jumper.client;

import com.jumper.Jumper;
import com.jumper.JumperClientConfig;
import com.jumper.net.JumperNetwork;
import com.jumper.net.WheelActionPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Set;

/**
 * Client game events: the wheel, jump-back and jump-home keys, hiding the HUD under Jumper's screens, drawing the range
 * dome, and forgetting the last server's spots on leaving.
 */
@EventBusSubscriber(modid = Jumper.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    /**
     * The HUD parts hidden while a Jumper screen is open (they showed through under the wheel's bottom row): hotbar,
     * hearts, armor, food, air, mount health, xp, jump bar, held item name, crosshair. Chat, the action bar (where
     * Jumper's messages appear), titles and the rest stay.
     */
    private static final Set<ResourceLocation> HIDDEN_LAYERS = Set.of(VanillaGuiLayers.HOTBAR,
            VanillaGuiLayers.PLAYER_HEALTH, VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.VEHICLE_HEALTH, VanillaGuiLayers.EXPERIENCE_BAR,
            VanillaGuiLayers.EXPERIENCE_LEVEL, VanillaGuiLayers.JUMP_METER, VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.SPECTATOR_TOOLTIP, VanillaGuiLayers.CROSSHAIR);

    /**
     * Mods whose HUD parts only decorate the bars hidden above, hidden with them so they don't float on their own
     * (AppleSkin's saturation outlines showed over the empty hunger bar's place).
     */
    private static final Set<String> HIDDEN_NAMESPACES = Set.of("appleskin");

    /** The range dome's colours: teal while the anchor works at its tier, amber while it works as tier 1. */
    private static final int DOME_TEAL = 0x8C2FA89A;
    private static final int DOME_TEAL_BRIGHT = 0xD02FA89A;
    private static final int DOME_AMBER = 0x8CE8AA3C;
    private static final int DOME_AMBER_BRIGHT = 0xD0E8AA3C;
    /** While the dome shows, the client asks for a fresh status this often (ticks), so its size and colour stay true. */
    private static final int DOME_STATUS_TICKS = 100;
    private static final DomeLines DOME_LINES = new DomeLines();
    private static int domeTicks;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        openWheelIfPressed();
        sendIfPressed(JumperKeys.BACK, WheelActionPayload.back());
        sendIfPressed(JumperKeys.HOME, WheelActionPayload.home());
        refreshDomeStatus();
    }

    /**
     * The anchor's power and loaded state can change while you walk around (its chunk stops running when you go far
     * away), and the server only sends updates when something happens. So while the dome is on and you're in its
     * dimension, ask now and then; the server answers at most every 10 ticks anyway.
     */
    private static void refreshDomeStatus() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!JumperClientConfig.rangeDome() || minecraft.player == null || minecraft.level == null
                || !ClientState.linked() || !minecraft.level.dimension().location().equals(ClientState.anchorDimension())) {
            domeTicks = 0;
            return;
        }
        if (++domeTicks >= DOME_STATUS_TICKS) {
            domeTicks = 0;
            if (minecraft.screen == null) {
                JumperNetwork.sendToServer(WheelActionPayload.status());
            }
        }
    }

    /**
     * Draws the range dome around the linked anchor (when switched on in the anchor's menu, in the anchor's dimension,
     * and not with the HUD hidden by F1). After particles: drawn on top of the world like other see-through effects,
     * and hidden behind blocks like a block outline. The size is the reach that works right now, from the server.
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !JumperClientConfig.rangeDome()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int reach = ClientState.reachNow();
        if (minecraft.level == null || minecraft.options.hideGui || !ClientState.linked() || reach <= 0
                || !minecraft.level.dimension().location().equals(ClientState.anchorDimension())) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        long anchor = ClientState.anchorPos();
        boolean active = ClientState.active();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        DOME_LINES.consumer = buffers.getBuffer(RenderType.lines());
        DOME_LINES.pose = event.getPoseStack().last();
        RangeDome.draw(BlockPos.getX(anchor) + 0.5 - camera.x, BlockPos.getY(anchor) + 0.5 - camera.y,
                BlockPos.getZ(anchor) + 0.5 - camera.z, reach, minecraft.options.getEffectiveRenderDistance() * 16.0,
                active ? DOME_TEAL : DOME_AMBER, active ? DOME_TEAL_BRIGHT : DOME_AMBER_BRIGHT, DOME_LINES);
        buffers.endBatch(RenderType.lines());
        DOME_LINES.consumer = null;
        DOME_LINES.pose = null;
    }

    /** Puts the dome's lines into the game's line buffer (1.21.1 vertex calls). Reused every frame. */
    private static final class DomeLines implements RangeDome.LineSink {
        private VertexConsumer consumer;
        private PoseStack.Pose pose;

        @Override
        public void line(float x1, float y1, float z1, float x2, float y2, float z2, float nx, float ny, float nz,
                int argb) {
            this.consumer.addVertex(this.pose, x1, y1, z1).setColor(argb).setNormal(this.pose, nx, ny, nz);
            this.consumer.addVertex(this.pose, x2, y2, z2).setColor(argb).setNormal(this.pose, nx, ny, nz);
        }
    }

    /**
     * Runs first, so other mods that draw on these bars from their own listeners (AppleSkin on 1.20.1) see the bar
     * already cancelled and skip it too.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (isJumperScreen(Minecraft.getInstance().screen) && (HIDDEN_LAYERS.contains(event.getName())
                || HIDDEN_NAMESPACES.contains(event.getName().getNamespace()))) {
            event.setCanceled(true);
        }
    }

    /** The wheel, the spot list, the name box and the anchor's menu. */
    static boolean isJumperScreen(Screen screen) {
        return screen instanceof WheelScreen || screen instanceof SpotListScreen || screen instanceof NameScreen
                || screen instanceof AnchorScreen;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.clear();
    }

    /** Opens the wheel when its key was pressed in the world (no other screen open). */
    static void openWheelIfPressed() {
        Minecraft minecraft = Minecraft.getInstance();
        boolean pressed = false;
        while (JumperKeys.WHEEL.consumeClick()) {
            pressed = true;
        }
        if (!pressed || minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (!ClientState.linked()) {
            minecraft.player.displayClientMessage(Component.translatable("message.jumper.not_linked"), true);
            return;
        }
        // Ask for fresh power numbers; the wheel opens right away and shows them when they arrive.
        JumperNetwork.sendToServer(WheelActionPayload.status());
        minecraft.setScreen(new WheelScreen());
    }

    /** Sends a request to the server when one of the no-wheel keys ("Jump back", "Jump home") was pressed in the world. */
    static void sendIfPressed(KeyMapping key, WheelActionPayload request) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean pressed = false;
        while (key.consumeClick()) {
            pressed = true;
        }
        if (!pressed || minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (!ClientState.linked()) {
            minecraft.player.displayClientMessage(Component.translatable("message.jumper.not_linked"), true);
            return;
        }
        JumperNetwork.sendToServer(request);
    }
}
