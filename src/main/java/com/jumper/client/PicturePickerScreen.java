package com.jumper.client;

import com.jumper.Jumper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Picks a spot's picture from every item and fluid in the game, opened by clicking a picture in the list. A search box at the
 * top (words match the name; "@word" matches the mod id), the player's hotbar under it for quick picks, then a grid of
 * everything that matches. Clicking a picture (or Enter for the first match) hands it back and returns to the list;
 * Esc or Cancel goes back without a change.
 *
 * <p>The list of everything is built once, the first time the picker opens (and again if the game's language changes),
 * and only the rows on screen are drawn, so the picker costs about as much as an inventory screen. Items whose names
 * can't be read are left out rather than breaking the screen. The same file on 1.20.1 and 1.21.1.
 */
public class PicturePickerScreen extends WheelScreenBase {
    private static final int SLOT = 18;
    private static final int PAD = 8;
    private static final int MIN_COLUMNS = 9;
    private static final int MAX_COLUMNS = 14;
    private static final int MIN_ROWS = 3;
    private static final int MAX_ROWS = 10;
    private static final int HOTBAR = 9;
    private static final int BUTTON_WIDTH = 58;
    private static final int BUTTON_HEIGHT = 16;
    private static final int BAR_WIDTH = 3;

    private static final int COLOR_BACK = 0x90000000;
    private static final int COLOR_PANEL = 0xF4101014;
    private static final int COLOR_EDGE = 0xFF2FA89A;
    private static final int COLOR_SLOT = 0xFF1A1A1E;
    private static final int COLOR_SLOT_HOVER = 0xD8207068;
    private static final int COLOR_BUTTON = 0xFF26262C;
    private static final int COLOR_BUTTON_HOVER = 0xD8207068;
    private static final int COLOR_CURRENT = 0xFF2FA89A;
    private static final int COLOR_DIVIDER = 0xFF2A2A30;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SOFT = 0xFFB8C0C8;
    private static final int COLOR_TEXT_LOCKED = 0xFF707478;
    private static final int COLOR_TRACK = 0xFF1A1A1E;
    private static final int COLOR_THUMB = 0xFF707478;

    /** One picture the picker can offer. search is the name in lower case; mod is the id's namespace. */
    private record Choice(String id, Picture picture, String name, String search, String mod, boolean fluid) {
    }

    /** Everything, in registry order (the game's own items first), and the language its names are in. */
    private static List<Choice> everything;
    private static String everythingLanguage = "";

    private final String currentId;
    private final BiConsumer<String, Picture> onPick;
    private final Screen parent;
    private List<Choice> matches = List.of();
    private EditBox box;
    /** First grid row shown. */
    private int scroll;
    private boolean dragging;

    private int columns;
    private int gridRows;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    private int hotbarTop;
    private int gridLeft;
    private int gridTop;
    private int barLeft;
    private int hintY;
    private int buttonTop;
    private int cancelLeft;

    /**
     * @param spotName  the spot the picture is for (shown in the title)
     * @param currentId the spot's picture id now, outlined in the grid
     * @param onPick    gets the picked id and picture, after the screen has gone back to parent
     */
    public PicturePickerScreen(String spotName, String currentId, BiConsumer<String, Picture> onPick, Screen parent) {
        super(Component.translatable("screen.jumper.picker.title", spotName));
        this.currentId = currentId;
        this.onPick = onPick;
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        this.columns = Math.max(MIN_COLUMNS, Math.min(MAX_COLUMNS, (this.width - 40 - 2 * PAD - 6) / SLOT));
        // Fixed parts: title and search (40), hotbar and divider (26), hint (14), button (BUTTON_HEIGHT) and margins.
        int fixed = 40 + 26 + 14 + BUTTON_HEIGHT + PAD + 6;
        this.gridRows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, (this.height - 20 - fixed) / SLOT));
        int gridWidth = this.columns * SLOT;
        this.panelWidth = gridWidth + 2 * PAD + BAR_WIDTH + 3;
        this.panelHeight = fixed + this.gridRows * SLOT;
        this.left = (this.width - this.panelWidth) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        this.gridLeft = this.left + PAD;
        this.barLeft = this.gridLeft + gridWidth + 3;
        this.hotbarTop = this.top + 40;
        this.gridTop = this.hotbarTop + 26;
        this.hintY = this.gridTop + this.gridRows * SLOT + 5;
        this.buttonTop = this.hintY + 14;
        this.cancelLeft = (this.width - BUTTON_WIDTH) / 2;

        String text = this.box == null ? "" : this.box.getValue();
        this.box = new EditBox(this.font, this.gridLeft, this.top + 20, gridWidth, 14,
                Component.translatable("screen.jumper.picker.search"));
        this.box.setMaxLength(50);
        this.box.setHint(Component.translatable("screen.jumper.picker.search"));
        this.box.setValue(text);
        this.box.setResponder(value -> filter());
        addWidget(this.box);
        setInitialFocus(this.box);
        filter();
    }

    // ---- the list of everything ----

    private static List<Choice> everything() {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (everything == null || !language.equals(everythingLanguage)) {
            long start = System.nanoTime();
            everything = build();
            everythingLanguage = language;
            Jumper.LOGGER.debug("Jumper picture picker: {} pictures listed in {} ms", everything.size(),
                    (System.nanoTime() - start) / 1_000_000L);
        }
        return everything;
    }

    private static List<Choice> build() {
        List<Choice> list = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            try {
                ItemStack stack = new ItemStack(item);
                if (stack.isEmpty()) {
                    continue;
                }
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                String name = stack.getHoverName().getString();
                list.add(new Choice(key.toString(), Picture.of(stack), name, name.toLowerCase(Locale.ROOT),
                        key.getNamespace(), false));
            } catch (RuntimeException | LinkageError problem) {
                // An item that can't even say its name: leave it out.
            }
        }
        // Fluids: only the still kind (water, not flowing water), and never the empty fluid.
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            try {
                if (fluid == Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) {
                    continue;
                }
                Picture picture = Picture.of(fluid);
                ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid);
                String name = picture.name().getString();
                list.add(new Choice(picture.id(), picture, name, name.toLowerCase(Locale.ROOT), key.getNamespace(),
                        true));
            } catch (RuntimeException | LinkageError problem) {
                // Same as items: leave it out.
            }
        }
        // Each mod's fluids right after its items (a stable sort keeps everything else in registry order).
        Map<String, Integer> modOrder = new HashMap<>();
        for (Choice choice : list) {
            modOrder.putIfAbsent(choice.mod(), modOrder.size());
        }
        list.sort(Comparator.comparingInt(choice -> modOrder.get(choice.mod())));
        return List.copyOf(list);
    }

    /** Fills {@link #matches} from the search box: every word must match, "@word" against the mod id. */
    private void filter() {
        String query = this.box == null ? "" : this.box.getValue().trim().toLowerCase(Locale.ROOT);
        List<Choice> all = everything();
        if (query.isEmpty()) {
            this.matches = all;
        } else {
            String[] words = query.split("\\s+");
            List<Choice> found = new ArrayList<>();
            for (Choice choice : all) {
                if (matchesAll(choice, words)) {
                    found.add(choice);
                }
            }
            this.matches = found;
        }
        this.scroll = Math.max(0, Math.min(this.scroll, maxScroll()));
        if (!query.isEmpty()) {
            this.scroll = 0;
        }
    }

    private static boolean matchesAll(Choice choice, String[] words) {
        for (String word : words) {
            boolean ok = word.startsWith("@") ? choice.mod().startsWith(word.substring(1))
                    : choice.search().contains(word);
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    // ---- picking ----

    private void pick(String id, Picture picture) {
        click();
        Minecraft.getInstance().setScreen(this.parent);
        this.onPick.accept(id, picture);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    /** The hotbar item in slot i (0 to 8), or empty. */
    private static ItemStack hotbar(int i) {
        var player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getInventory().getItem(i);
    }

    private int hotbarAt(double mouseX, double mouseY) {
        if (mouseY < this.hotbarTop || mouseY >= this.hotbarTop + SLOT || mouseX < this.gridLeft) {
            return -1;
        }
        int i = (int) ((mouseX - this.gridLeft) / SLOT);
        return i < HOTBAR ? i : -1;
    }

    /** Index into {@link #matches} of the grid slot under the mouse, or -1. */
    private int gridAt(double mouseX, double mouseY) {
        if (mouseX < this.gridLeft || mouseX >= this.gridLeft + this.columns * SLOT || mouseY < this.gridTop
                || mouseY >= this.gridTop + this.gridRows * SLOT) {
            return -1;
        }
        int column = (int) ((mouseX - this.gridLeft) / SLOT);
        int row = (int) ((mouseY - this.gridTop) / SLOT);
        int index = (this.scroll + row) * this.columns + column;
        return index < this.matches.size() ? index : -1;
    }

    private boolean onBar(double mouseX, double mouseY) {
        return mouseX >= this.barLeft - 1 && mouseX < this.barLeft + BAR_WIDTH + 2 && mouseY >= this.gridTop
                && mouseY < this.gridTop + this.gridRows * SLOT && maxScroll() > 0;
    }

    private boolean onCancel(double mouseX, double mouseY) {
        return mouseX >= this.cancelLeft && mouseX < this.cancelLeft + BUTTON_WIDTH && mouseY >= this.buttonTop
                && mouseY < this.buttonTop + BUTTON_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int slot = hotbarAt(mouseX, mouseY);
            if (slot >= 0) {
                ItemStack stack = hotbar(slot);
                if (!stack.isEmpty()) {
                    Picture picture = Picture.of(stack);
                    pick(picture.id(), picture);
                }
                return true;
            }
            int index = gridAt(mouseX, mouseY);
            if (index >= 0) {
                Choice choice = this.matches.get(index);
                pick(choice.id(), choice.picture());
                return true;
            }
            if (onBar(mouseX, mouseY)) {
                this.dragging = true;
                scrollToMouse(mouseY);
                return true;
            }
            if (onCancel(mouseX, mouseY)) {
                click();
                onClose();
                return true;
            }
        }
        boolean used = super.mouseClicked(mouseX, mouseY, button);
        // Typing always goes to the search box, wherever the mouse clicked.
        setFocused(this.box);
        this.box.setFocused(true);
        return used;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragging) {
            scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
                && !this.matches.isEmpty()) {
            Choice first = this.matches.get(0);
            pick(first.id(), first.picture());
            return true;
        }
        if (keyCode == InputConstants.KEY_PAGEUP || keyCode == InputConstants.KEY_PAGEDOWN) {
            scrollBy(keyCode == InputConstants.KEY_PAGEUP ? -this.gridRows : this.gridRows);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- scrolling ----

    private int totalRows() {
        return (this.matches.size() + this.columns - 1) / this.columns;
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - this.gridRows);
    }

    private void scrollBy(int rows) {
        this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll + rows));
    }

    private void scrollToMouse(double mouseY) {
        double fraction = (mouseY - this.gridTop) / Math.max(1, this.gridRows * SLOT);
        this.scroll = Math.max(0, Math.min(maxScroll(), (int) Math.round(fraction * maxScroll())));
    }

    @Override
    protected boolean onScroll(double amount) {
        if (amount == 0.0) {
            return false;
        }
        scrollBy(amount > 0 ? -1 : 1);
        return true;
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No super.render: no blur, the world stays visible behind the picker.
        graphics.fill(0, 0, this.width, this.height, COLOR_BACK);
        int right = this.left + this.panelWidth;
        int bottom = this.top + this.panelHeight;
        graphics.fill(this.left, this.top, right, bottom, COLOR_EDGE);
        graphics.fill(this.left + 1, this.top + 1, right - 1, bottom - 1, COLOR_PANEL);
        graphics.drawCenteredString(this.font, fit(this.title.getString(), this.panelWidth - 2 * PAD),
                this.width / 2, this.top + 7, COLOR_TEXT);
        this.box.render(graphics, mouseX, mouseY, partialTick);

        Component hint = null;
        int hoveredHotbar = hotbarAt(mouseX, mouseY);
        for (int i = 0; i < HOTBAR; i++) {
            ItemStack stack = hotbar(i);
            int x = this.gridLeft + i * SLOT;
            boolean current = !stack.isEmpty() && Picture.of(stack).id().equals(this.currentId);
            drawSlot(graphics, x, this.hotbarTop, hoveredHotbar == i && !stack.isEmpty(), current);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 1, this.hotbarTop + 1);
                if (hoveredHotbar == i) {
                    hint = stack.getHoverName();
                }
            }
        }
        int dividerY = this.hotbarTop + SLOT + 3;
        graphics.fill(this.gridLeft, dividerY, this.gridLeft + this.columns * SLOT, dividerY + 1, COLOR_DIVIDER);

        int hoveredGrid = gridAt(mouseX, mouseY);
        int first = this.scroll * this.columns;
        int last = Math.min(this.matches.size(), first + this.gridRows * this.columns);
        for (int index = first; index < last; index++) {
            Choice choice = this.matches.get(index);
            int x = this.gridLeft + (index - first) % this.columns * SLOT;
            int y = this.gridTop + (index - first) / this.columns * SLOT;
            drawSlot(graphics, x, y, index == hoveredGrid, choice.id().equals(this.currentId));
            choice.picture().draw(graphics, x + 1, y + 1);
        }
        if (hoveredGrid >= 0) {
            Choice choice = this.matches.get(hoveredGrid);
            hint = Component.translatable(choice.fluid() ? "screen.jumper.picker.fluid" : "screen.jumper.picker.item",
                    choice.name(), choice.mod());
        }
        drawScrollbar(graphics);

        if (hint == null) {
            hint = this.matches.isEmpty() ? Component.translatable("screen.jumper.picker.none")
                    : Component.translatable("screen.jumper.picker.hint", this.matches.size());
        }
        drawShrunk(graphics, hint, this.width / 2, this.hintY, this.panelWidth - 2 * PAD,
                hoveredGrid >= 0 || hoveredHotbar >= 0 ? COLOR_TEXT : COLOR_TEXT_LOCKED);

        boolean cancelHovered = onCancel(mouseX, mouseY);
        graphics.fill(this.cancelLeft, this.buttonTop, this.cancelLeft + BUTTON_WIDTH,
                this.buttonTop + BUTTON_HEIGHT, cancelHovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        graphics.drawCenteredString(this.font, Component.translatable("screen.jumper.picker.cancel"),
                this.cancelLeft + BUTTON_WIDTH / 2, this.buttonTop + 4, cancelHovered ? COLOR_TEXT : COLOR_TEXT_SOFT);
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y, boolean hovered, boolean current) {
        if (current) {
            graphics.fill(x, y, x + SLOT, y + SLOT, COLOR_CURRENT);
            graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, hovered ? COLOR_SLOT_HOVER : COLOR_SLOT);
        } else {
            graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1, hovered ? COLOR_SLOT_HOVER : COLOR_SLOT);
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int trackTop = this.gridTop;
        int trackHeight = this.gridRows * SLOT - 1;
        graphics.fill(this.barLeft, trackTop, this.barLeft + BAR_WIDTH, trackTop + trackHeight, COLOR_TRACK);
        int thumb = Math.max(8, trackHeight * this.gridRows / Math.max(1, totalRows()));
        int thumbTop = trackTop + (trackHeight - thumb) * this.scroll / max;
        graphics.fill(this.barLeft, thumbTop, this.barLeft + BAR_WIDTH, thumbTop + thumb, COLOR_THUMB);
    }

    /** The text, cut with "..." if it's wider than the room. */
    private String fit(String text, int room) {
        if (this.font.width(text) <= room) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, room - this.font.width("..."))).trim() + "...";
    }

    /** One line of text, made smaller if it is wider than the room (never larger). */
    private void drawShrunk(GuiGraphics graphics, Component text, int centerX, int y, int room, int color) {
        int textWidth = this.font.width(text);
        if (textWidth <= room) {
            graphics.drawCenteredString(this.font, text, centerX, y, color);
            return;
        }
        float scale = Math.max(0.5F, (float) room / textWidth);
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
