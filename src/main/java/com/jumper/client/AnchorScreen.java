package com.jumper.client;

import com.jumper.JumperClientConfig;
import com.jumper.net.AnchorInfoPayload;
import com.jumper.net.AnchorPayload;
import com.jumper.net.JumperNetwork;
import com.jumper.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The anchor's right-click menu: its tier and reach, its power, your spots, whether it's your anchor, the range dome
 * switch and Done. On an anchor you aren't linked to: "Not your anchor" and a Link button; the dome switch waits until
 * you link. The server sends the numbers when the menu opens and once a second while it's open (see
 * {@code AnchorMenu}). The same file on 1.20.1 and 1.21.1.
 */
public class AnchorScreen extends Screen {
    private static final int PANEL_WIDTH = 204;
    private static final int PANEL_HEIGHT = 124;
    private static final int BUTTON_HEIGHT = 18;
    private static final int LINE_HEIGHT = 11;
    private static final int COLOR_BACK = 0x60000000;
    private static final int COLOR_PANEL = 0xF4101014;
    private static final int COLOR_EDGE = 0xFF2FA89A;
    private static final int COLOR_BUTTON = 0xFF26262C;
    private static final int COLOR_BUTTON_ON = 0xFF1E6A62;
    private static final int COLOR_BUTTON_HOVER = 0xD8207068;
    private static final int COLOR_BUTTON_LINK = 0xFF207068;
    private static final int COLOR_BUTTON_LINK_HOVER = 0xFF2FA89A;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SOFT = 0xFFB8C0C8;
    private static final int COLOR_TEXT_LOCKED = 0xFF707478;
    private static final int COLOR_GOOD = 0xFF7FE07F;
    private static final int COLOR_WARN = 0xFFE8AA3C;
    private static final int COLOR_BAD = 0xFFE24B4A;
    /** Ticks between two refresh requests while the menu is open. */
    private static final int REFRESH_TICKS = 20;
    /** Ticks the Link button ignores clicks after one, until the server's answer arrives. */
    private static final int LINK_WAIT_TICKS = 10;

    /** One line of the menu's text. */
    private record Line(Component text, int color) {
    }

    private final long pos;
    private final ItemStack picture;
    private AnchorInfoPayload info;
    private final List<Line> lines = new ArrayList<>();
    private Component note = Component.empty();
    private int ticks;
    private int linkClickedAt = -LINK_WAIT_TICKS;
    private int left;
    private int top;

    public AnchorScreen(AnchorInfoPayload info) {
        super(Component.translatable("screen.jumper.anchor.title"));
        this.pos = info.pos();
        this.picture = new ItemStack(ModItems.JUMPER_ANCHOR.get());
        update(info);
    }

    /** The anchor's block position, packed. */
    public long pos() {
        return this.pos;
    }

    /** New numbers from the server. */
    public void update(AnchorInfoPayload newInfo) {
        this.info = newInfo;
        rebuild();
    }

    private boolean yours() {
        return this.info.link() == AnchorInfoPayload.LINK_HERE;
    }

    /** True if the anchor works at its real tier right now. Only known for your own anchor. */
    private boolean active() {
        return this.info.power() == ClientState.POWER_FREE || this.info.power() == ClientState.POWER_ON;
    }

    /** True if the tier reaches everywhere (no distance limit, or other dimensions too). */
    private boolean unlimited() {
        return this.info.anyDimension() || this.info.range() < 0;
    }

    private void rebuild() {
        this.lines.clear();
        AnchorInfoPayload in = this.info;
        int tier = in.tier();
        if (!yours()) {
            this.lines.add(new Line(tierLine(tier), COLOR_TEXT_SOFT));
            this.lines.add(new Line(Component.translatable("screen.jumper.anchor.not_yours"), COLOR_WARN));
            boolean elsewhere = in.link() == AnchorInfoPayload.LINK_ELSEWHERE;
            this.lines.add(new Line(Component.translatable(elsewhere ? "screen.jumper.anchor.linked_elsewhere"
                    : "screen.jumper.anchor.not_linked"), COLOR_TEXT_SOFT));
            this.note = Component.translatable(elsewhere ? "screen.jumper.anchor.note_link_move"
                    : "screen.jumper.anchor.note_link_new");
            return;
        }
        boolean active = active();
        if (active) {
            this.lines.add(new Line(tierLine(tier), COLOR_TEXT_SOFT));
        } else {
            this.lines.add(new Line(Component.translatable("screen.jumper.anchor.tier_for_now", tier,
                    in.baseRange() < 0 ? anyDistance() : Component.literal(String.valueOf(in.baseRange()))),
                    COLOR_WARN));
        }
        this.lines.add(powerLine());
        boolean limited = in.usable() < in.slots() && in.spots() > in.usable();
        this.lines.add(new Line(limited
                ? Component.translatable("screen.jumper.anchor.spots_working", in.spots(), in.slots(), in.usable())
                : Component.translatable("screen.jumper.anchor.spots", in.spots(), in.slots()), COLOR_TEXT_SOFT));
        this.lines.add(new Line(Component.translatable("screen.jumper.anchor.yours"), COLOR_GOOD));
        if (!active) {
            boolean unloaded = in.power() == ClientState.POWER_NOT_LOADED || in.power() == ClientState.POWER_BOTH;
            this.note = Component.translatable(unloaded ? "screen.jumper.anchor.note_unloaded"
                    : "screen.jumper.anchor.note_no_power");
        } else if (unlimited()) {
            this.note = Component.translatable("screen.jumper.anchor.note_no_dome");
        } else {
            this.note = Component.translatable(JumperClientConfig.rangeDome() ? "screen.jumper.anchor.note_dome"
                    : "screen.jumper.anchor.note_dome_off");
        }
    }

    private Component tierLine(int tier) {
        if (this.info.anyDimension()) {
            return Component.translatable("screen.jumper.anchor.tier_any_dimension", tier);
        }
        if (this.info.range() < 0) {
            return Component.translatable("screen.jumper.anchor.tier_any", tier);
        }
        return Component.translatable("screen.jumper.anchor.tier", tier, this.info.range());
    }

    private static Component anyDistance() {
        return Component.translatable("screen.jumper.anchor.any_distance");
    }

    private Line powerLine() {
        AnchorInfoPayload in = this.info;
        int percent = in.capacity() <= 0 ? 0
                : (int) Math.max(0L, Math.min(100L, (long) (in.energy() * 100.0 / in.capacity())));
        switch (in.power()) {
            case ClientState.POWER_FREE:
                return new Line(Component.translatable("screen.jumper.anchor.power_free"), COLOR_TEXT_SOFT);
            case ClientState.POWER_ON:
                if (in.rate() <= 0) {
                    return new Line(Component.translatable("screen.jumper.anchor.power_on_steady", percent),
                            COLOR_GOOD);
                }
                return new Line(Component.translatable("screen.jumper.anchor.power_on", percent,
                        WheelScreen.duration(in.energy() / in.rate())), COLOR_GOOD);
            case ClientState.POWER_NOT_LOADED:
                return new Line(Component.translatable("screen.jumper.anchor.power_unloaded", percent), COLOR_WARN);
            case ClientState.POWER_BOTH:
                return new Line(Component.translatable("screen.jumper.anchor.power_unloaded_none"), COLOR_BAD);
            default:
                Component reach = unlimited() ? Component.translatable("screen.jumper.anchor.no_limit")
                        : Component.literal(String.valueOf(in.range()));
                return new Line(Component.translatable("screen.jumper.anchor.power_none", reach), COLOR_BAD);
        }
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_WIDTH) / 2;
        this.top = Math.max(2, (this.height - PANEL_HEIGHT) / 2 - 6);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        this.ticks++;
        if (this.ticks % REFRESH_TICKS == 0) {
            JumperNetwork.sendToServer(AnchorPayload.info(BlockPos.of(this.pos)));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The inventory key (E unless rebound) closes the menu, the same as Esc and as on Jumper's other screens.
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- layout: the same numbers draw and react ----

    private int buttonTop() {
        return this.top + PANEL_HEIGHT - 26;
    }

    private int domeLeft() {
        return this.left + 8;
    }

    private int domeRight() {
        return this.left + 136;
    }

    private int doneLeft() {
        return this.left + 142;
    }

    private int doneRight() {
        return this.left + PANEL_WIDTH - 8;
    }

    private int linkTop() {
        return this.top + 28 + this.lines.size() * LINE_HEIGHT + 1;
    }

    private static boolean inside(double mouseX, double mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private boolean overDome(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, domeLeft(), buttonTop(), domeRight(), buttonTop() + BUTTON_HEIGHT);
    }

    private boolean overDone(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, doneLeft(), buttonTop(), doneRight(), buttonTop() + BUTTON_HEIGHT);
    }

    private boolean overLink(double mouseX, double mouseY) {
        return !yours() && inside(mouseX, mouseY, this.left + 10, linkTop(), this.left + PANEL_WIDTH - 10,
                linkTop() + BUTTON_HEIGHT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (overDone(mouseX, mouseY)) {
                click();
                onClose();
                return true;
            }
            if (overDome(mouseX, mouseY) && yours()) {
                click();
                JumperClientConfig.setRangeDome(!JumperClientConfig.rangeDome());
                rebuild();
                return true;
            }
            if (overLink(mouseX, mouseY) && this.ticks - this.linkClickedAt >= LINK_WAIT_TICKS) {
                click();
                this.linkClickedAt = this.ticks;
                JumperNetwork.sendToServer(AnchorPayload.link(BlockPos.of(this.pos)));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No super.render: no blur, the world (and the dome) stays visible behind the menu.
        graphics.fill(0, 0, this.width, this.height, COLOR_BACK);
        int right = this.left + PANEL_WIDTH;
        int bottom = this.top + PANEL_HEIGHT;
        graphics.fill(this.left, this.top, right, bottom, COLOR_EDGE);
        graphics.fill(this.left + 1, this.top + 1, right - 1, bottom - 1, COLOR_PANEL);
        graphics.renderItem(this.picture, this.left + 8, this.top + 6);
        graphics.drawString(this.font, this.title, this.left + 30, this.top + 10, COLOR_TEXT);
        int y = this.top + 28;
        for (Line line : this.lines) {
            drawShrunkLeft(graphics, line.text(), this.left + 10, y, PANEL_WIDTH - 20, line.color());
            y += LINE_HEIGHT;
        }
        if (!yours()) {
            boolean waiting = this.ticks - this.linkClickedAt < LINK_WAIT_TICKS;
            boolean hovered = !waiting && overLink(mouseX, mouseY);
            drawButton(graphics, this.left + 10, linkTop(), right - 10,
                    Component.translatable("screen.jumper.anchor.link"),
                    hovered ? COLOR_BUTTON_LINK_HOVER : COLOR_BUTTON_LINK, COLOR_TEXT);
        }
        drawDomeButton(graphics, mouseX, mouseY);
        boolean doneHovered = overDone(mouseX, mouseY);
        drawButton(graphics, doneLeft(), buttonTop(), doneRight(), Component.translatable("screen.jumper.anchor.done"),
                doneHovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON, doneHovered ? COLOR_TEXT : COLOR_TEXT_SOFT);
        drawShrunkCentered(graphics, this.note, this.width / 2, bottom + 6, this.width - 12, COLOR_TEXT_SOFT);
    }

    private void drawDomeButton(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!yours()) {
            drawButton(graphics, domeLeft(), buttonTop(), domeRight(),
                    Component.translatable("screen.jumper.anchor.dome_link_first"), COLOR_BUTTON, COLOR_TEXT_LOCKED);
            return;
        }
        boolean on = JumperClientConfig.rangeDome();
        boolean hovered = overDome(mouseX, mouseY);
        int fill = hovered ? COLOR_BUTTON_HOVER : on ? COLOR_BUTTON_ON : COLOR_BUTTON;
        drawButton(graphics, domeLeft(), buttonTop(), domeRight(),
                Component.translatable(on ? "screen.jumper.anchor.dome_on" : "screen.jumper.anchor.dome_off"), fill,
                on || hovered ? COLOR_TEXT : COLOR_TEXT_SOFT);
    }

    private void drawButton(GuiGraphics graphics, int left, int top, int right, Component label, int fill,
            int color) {
        graphics.fill(left, top, right, top + BUTTON_HEIGHT, fill);
        drawShrunkCentered(graphics, label, (left + right) / 2, top + 5, right - left - 6, color);
    }

    /** One line of text from x, made smaller if it is wider than the room (never larger). */
    private void drawShrunkLeft(GuiGraphics graphics, Component text, int x, int y, int room, int color) {
        int textWidth = this.font.width(text);
        if (textWidth <= room) {
            graphics.drawString(this.font, text, x, y, color);
            return;
        }
        float scale = (float) room / textWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + 4.0F * (1.0F - scale), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    /** One line of text centred on centerX, made smaller if it is wider than the room (never larger). */
    private void drawShrunkCentered(GuiGraphics graphics, Component text, int centerX, int y, int room, int color) {
        int textWidth = this.font.width(text);
        if (textWidth <= room) {
            graphics.drawCenteredString(this.font, text, centerX, y, color);
            return;
        }
        float scale = (float) room / textWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y + 4.0F * (1.0F - scale), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(this.font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }
}
