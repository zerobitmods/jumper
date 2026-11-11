package com.jumper.client;

import com.jumper.JumperRules;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * A small box for typing a spot's name: used when saving a new spot (the suggested name is filled in and selected, so
 * typing replaces it) and when renaming one from the list. Enter saves, Esc cancels. The same file on 1.20.1 and 1.21.1.
 */
public class NameScreen extends Screen {
    private static final int PANEL_WIDTH = 192;
    private static final int PANEL_HEIGHT = 84;
    private static final int BUTTON_WIDTH = 58;
    private static final int BUTTON_HEIGHT = 16;
    private static final int COLOR_BACK = 0x90000000;
    private static final int COLOR_PANEL = 0xF4101014;
    private static final int COLOR_EDGE = 0xFF2FA89A;
    private static final int COLOR_BUTTON = 0xFF26262C;
    private static final int COLOR_BUTTON_HOVER = 0xD8207068;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SOFT = 0xFFB8C0C8;
    private static final int COLOR_TEXT_LOCKED = 0xFF707478;
    private static final int COLOR_PROBLEM = 0xFFE24B4A;

    private final ItemStack picture;
    private final String initial;
    private final List<ClientState.Entry> spots;
    /** Index of the spot being renamed (its own name doesn't count as taken), or -1 for a new spot. */
    private final int self;
    private final Consumer<String> onSave;
    /** Where Esc, Cancel and Save go back to; null closes to the game. */
    private final Screen parent;
    private EditBox box;
    private int left;
    private int top;
    private int saveLeft;
    private int cancelLeft;
    private int buttonTop;

    public NameScreen(Component title, ItemStack picture, String initial, List<ClientState.Entry> spots, int self,
            Consumer<String> onSave, Screen parent) {
        super(title);
        this.picture = picture;
        this.initial = initial;
        this.spots = spots;
        this.self = self;
        this.onSave = onSave;
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_WIDTH) / 2;
        this.top = (this.height - PANEL_HEIGHT) / 2;
        String text = this.box == null ? this.initial : this.box.getValue();
        this.box = new EditBox(this.font, this.left + 32, this.top + 24, PANEL_WIDTH - 42, 16,
                Component.translatable("screen.jumper.name.box"));
        this.box.setMaxLength(JumperRules.MAX_NAME_LENGTH);
        this.box.setValue(text);
        // Everything selected, so typing replaces the suggestion; Enter keeps it.
        this.box.setCursorPosition(text.length());
        this.box.setHighlightPos(0);
        addWidget(this.box);
        setInitialFocus(this.box);
        this.buttonTop = this.top + 48;
        this.saveLeft = this.width / 2 - BUTTON_WIDTH - 4;
        this.cancelLeft = this.width / 2 + 4;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Why the typed name can't be used, or null if it can. */
    private Component problem() {
        String name = this.box.getValue().trim();
        if (name.isEmpty()) {
            return Component.translatable("screen.jumper.name.empty");
        }
        if (ClientEdits.taken(name, this.spots, this.self)) {
            return Component.translatable("screen.jumper.name.taken", name);
        }
        return null;
    }

    private void save() {
        if (problem() != null) {
            return;
        }
        click();
        String name = this.box.getValue().trim();
        Minecraft.getInstance().setScreen(this.parent);
        this.onSave.accept(name);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= this.buttonTop && mouseY < this.buttonTop + BUTTON_HEIGHT) {
            if (mouseX >= this.saveLeft && mouseX < this.saveLeft + BUTTON_WIDTH) {
                save();
                return true;
            }
            if (mouseX >= this.cancelLeft && mouseX < this.cancelLeft + BUTTON_WIDTH) {
                click();
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No super.render: no blur, the world stays visible behind the box.
        graphics.fill(0, 0, this.width, this.height, COLOR_BACK);
        int right = this.left + PANEL_WIDTH;
        int bottom = this.top + PANEL_HEIGHT;
        graphics.fill(this.left, this.top, right, bottom, COLOR_EDGE);
        graphics.fill(this.left + 1, this.top + 1, right - 1, bottom - 1, COLOR_PANEL);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.top + 8, COLOR_TEXT);
        graphics.renderItem(this.picture, this.left + 10, this.top + 24);
        this.box.render(graphics, mouseX, mouseY, partialTick);
        Component problem = problem();
        drawButton(graphics, this.saveLeft, Component.translatable("screen.jumper.name.save"), mouseX, mouseY,
                problem != null);
        drawButton(graphics, this.cancelLeft, Component.translatable("screen.jumper.name.cancel"), mouseX, mouseY,
                false);
        Component note = problem != null ? problem : Component.translatable("screen.jumper.name.keys");
        drawShrunk(graphics, note, this.width / 2, this.top + 70, PANEL_WIDTH - 12,
                problem != null ? COLOR_PROBLEM : COLOR_TEXT_LOCKED);
    }

    private void drawButton(GuiGraphics graphics, int left, Component label, int mouseX, int mouseY, boolean dim) {
        boolean hovered = !dim && mouseX >= left && mouseX < left + BUTTON_WIDTH && mouseY >= this.buttonTop
                && mouseY < this.buttonTop + BUTTON_HEIGHT;
        graphics.fill(left, this.buttonTop, left + BUTTON_WIDTH, this.buttonTop + BUTTON_HEIGHT,
                hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        graphics.drawCenteredString(this.font, label, left + BUTTON_WIDTH / 2, this.buttonTop + 4,
                dim ? COLOR_TEXT_LOCKED : hovered ? COLOR_TEXT : COLOR_TEXT_SOFT);
    }

    /** One line of text, made smaller if it is wider than the room (never larger). */
    private void drawShrunk(GuiGraphics graphics, Component text, int centerX, int y, int room, int color) {
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
