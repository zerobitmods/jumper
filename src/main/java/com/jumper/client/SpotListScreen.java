package com.jumper.client;

import com.jumper.net.EditPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The list of saved spots, opened from the wheel's "Edit spots" button: rename, change the picture (click it to use the
 * item in hand), move up or down, delete (asks once), and Undo. Moving a spot slides the whole list by one row instead,
 * so the spot (and its arrow) stays under the mouse and repeated clicks keep moving the same spot; near the ends of the
 * list that leaves an empty gap until the list is scrolled.
 *
 * <p>To make moves easy to follow, every row shows its number on the left, a moved row lights up and fades, and rows
 * glide to their new places instead of jumping. Rows are tracked by name for this (names are unique per player), so a
 * glide carries on smoothly when the server's copy of the list arrives.
 *
 * <p>Undo steps back through the changes made in this list (Ctrl+Z, or Cmd+Z on a Mac, does the same). The steps stay
 * after the list closes, until you leave the world; reopening keeps them as long as the list still starts with what it
 * showed when it closed (a spot saved from the wheel meanwhile only adds a row at the end). A deleted spot comes back
 * from the copy the server kept when it was deleted (its last 20, until the server stops); the client never says where
 * a spot is.
 *
 * <p>Edits show at once on this screen and are sent to the server, which checks and applies them. While an edit is on
 * its way, updates from the server are held back (they'd show the list from before the edit); the server's answer to
 * the last edit, or two seconds without one, ends the wait. If the server's list then isn't what this screen expected
 * (an edit was refused), the Undo steps are forgotten, since they no longer line up. The same file on 1.20.1 and 1.21.1.
 */
public class SpotListScreen extends WheelScreenBase {
    private static final int TOP = 20;
    private static final int ROW = 20;
    private static final int STEP = 22;
    private static final int ICON = 16;
    private static final int MAX_ROW_WIDTH = 360;
    /** Ticks to wait for the server to answer an edit before showing what it last sent anyway. */
    private static final int WAIT_TICKS = 40;
    /** How long rows take to glide to their new places, in milliseconds. */
    private static final long GLIDE_MS = 120;
    /** A moved row stays lit this long, then fades out over GLOW_FADE_MS (milliseconds). */
    private static final long GLOW_HOLD_MS = 350;
    private static final long GLOW_FADE_MS = 650;
    /** Most changes Undo can step back through. */
    private static final int MAX_UNDO = 64;

    private static final int NOTHING = 0;
    private static final int PICTURE = 1;
    private static final int RENAME = 2;
    private static final int UP = 3;
    private static final int DOWN = 4;
    private static final int DELETE = 5;
    private static final int ROW_ONLY = 6;
    private static final int DONE = 7;
    private static final int UNDO = 8;

    private static final int COLOR_BACK = 0xC0000000;
    private static final int COLOR_ROW = 0x60141418;
    private static final int COLOR_ROW_HOVER = 0xD8207068;
    private static final int COLOR_ROW_DELETE = 0xB0502828;
    private static final int COLOR_BUTTON = 0xFF26262C;
    private static final int COLOR_BUTTON_HOVER = 0xD8207068;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SOFT = 0xFFB8C0C8;
    private static final int COLOR_TEXT_LOCKED = 0xFF707478;
    private static final int COLOR_ARROW = 0xFFB8C0C8;
    private static final int COLOR_CROSS = 0xFFE24B4A;
    private static final int COLOR_DIVIDER = 0xFF2FA89A;
    private static final int COLOR_TRACK = 0xFF1A1A1E;
    private static final int COLOR_THUMB = 0xFF707478;
    /** The lit-up row: a teal wash (its alpha fades) and teal lines along its top and bottom. */
    private static final int GLOW_FILL_RGB = 0x207068;
    private static final int GLOW_LINE_RGB = 0x2FA89A;

    /** One change made in this list, for Undo. index is where the spot is after the change. */
    private record Change(int kind, int index, ClientState.Entry before, ClientState.Entry after) {
    }

    /** What this screen shows: the server's list, plus edits it hasn't confirmed yet. */
    private List<ClientState.Entry> rows;
    /** The server list {@link #rows} was last copied from (a new list arrives with every sync). */
    private List<ClientState.Entry> copiedFrom;
    /** Number of the last edit sent and not yet answered, or 0. */
    private int waitingFor;
    private int waitTicks;
    /** First row shown at the top; may run past either end while moving a spot (see the class note). */
    private int scroll;
    /** Row whose delete button was clicked once, or -1; and its name, in case the list changes under it. */
    private int confirmRow = -1;
    private String confirmName = "";
    /** Changes made in the list, newest last. Kept after the list closes, until you leave the world. */
    private static final List<Change> UNDO_STEPS = new ArrayList<>();
    /** The rows as the list showed them when it last closed, to check on reopening that the steps still line up. */
    private static List<ClientState.Entry> rowsWhenClosed = List.of();

    /** Where each row was drawn just before a change (by name), and how far each still has to glide. */
    private final Map<String, Float> placesBefore = new HashMap<>();
    private final Map<String, Float> glideOffsets = new HashMap<>();
    private long glideStart;
    /** The row that lights up (by name), or null; and when it lit up. */
    private String glowName;
    private long glowStart;

    private int visible;
    private int rowLeft;
    private int rowRight;
    private int numberRight;
    private int renameLeft;
    private int renameRight;
    private int doneLeft;
    private int doneRight;
    private int undoLeft;
    private int undoRight;
    private int buttonTop;
    private Component renameLabel;

    // What the mouse was over when last drawn.
    private int hoveredRow = -1;
    private int hoveredPart = NOTHING;

    public SpotListScreen() {
        super(Component.translatable("screen.jumper.list.title"));
        this.copiedFrom = ClientState.spots();
        this.rows = new ArrayList<>(this.copiedFrom);
        if (!startsWith(this.rows, rowsWhenClosed)) {
            // Changed from outside since the list closed (a spot removed by command, an edit refused after closing):
            // the old steps might not line up any more.
            UNDO_STEPS.clear();
        }
    }

    /** Leaving the world: forget the Undo steps. */
    static void forgetUndo() {
        UNDO_STEPS.clear();
        rowsWhenClosed = List.of();
    }

    @Override
    public void removed() {
        super.removed();
        rowsWhenClosed = new ArrayList<>(this.rows);
    }

    /** True if list starts with the same spots as start (same names and pictures, same order). */
    private static boolean startsWith(List<ClientState.Entry> list, List<ClientState.Entry> start) {
        return list.size() >= start.size() && sameRows(list.subList(0, start.size()), start);
    }

    @Override
    protected void init() {
        this.visible = Math.max(1, (this.height - TOP - 48) / STEP);
        int blockWidth = Math.min(MAX_ROW_WIDTH, this.width - 30);
        int blockLeft = (this.width - blockWidth) / 2 - 3;
        // Room on the left for the row numbers, as wide as the biggest number this anchor can need.
        int digits = Integer.toString(Math.max(9, Math.max(ClientState.slots(), this.rows.size()))).length();
        this.rowLeft = blockLeft + this.font.width("0".repeat(digits)) + 5;
        this.rowRight = blockLeft + blockWidth;
        this.numberRight = this.rowLeft - 3;
        this.renameLabel = Component.translatable("screen.jumper.list.rename");
        this.renameRight = this.rowRight - 66;
        this.renameLeft = this.renameRight - this.font.width(this.renameLabel) - 10;
        this.buttonTop = this.height - 28;
        layoutButtons();
        clampScroll(true);
    }

    /** "Done" alone in the middle; once there is something to undo, "Done" and "Undo" side by side. */
    private void layoutButtons() {
        int middle = this.width / 2;
        if (UNDO_STEPS.isEmpty()) {
            this.doneLeft = middle - 30;
            this.doneRight = middle + 30;
        } else {
            this.doneLeft = middle - 64;
            this.doneRight = middle - 4;
            this.undoLeft = middle + 4;
            this.undoRight = middle + 64;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- keeping up with the server ----

    @Override
    public void tick() {
        if (this.waitingFor != 0) {
            this.waitTicks++;
            if (ClientState.editSeq() == this.waitingFor || this.waitTicks > WAIT_TICKS) {
                this.waitingFor = 0;
                copyFromServer();
            }
        } else if (ClientState.spots() != this.copiedFrom) {
            copyFromServer();
        }
    }

    private void copyFromServer() {
        List<ClientState.Entry> server = ClientState.spots();
        if (!sameRows(server, this.rows)) {
            // Not what this screen expected (an edit was refused, or the list changed from outside): the Undo steps
            // would no longer line up with the list, so forget them rather than risk undoing the wrong thing.
            UNDO_STEPS.clear();
        }
        this.copiedFrom = server;
        this.rows = new ArrayList<>(server);
        if (this.confirmRow >= this.rows.size() || (this.confirmRow >= 0
                && !this.rows.get(this.confirmRow).name().equals(this.confirmName))) {
            this.confirmRow = -1;
        }
        clampScroll(false);
    }

    /** True if both lists have the same spots, names and pictures, in the same order. */
    private static boolean sameRows(List<ClientState.Entry> a, List<ClientState.Entry> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).name().equals(b.get(i).name()) || !a.get(i).iconId().equals(b.get(i).iconId())) {
                return false;
            }
        }
        return true;
    }

    private void send(byte action, int index, String expected, String text) {
        this.waitingFor = ClientEdits.send(action, index, expected, text);
        this.waitTicks = 0;
    }

    // ---- scrolling ----

    private int maxScroll() {
        return Math.max(0, this.rows.size() - this.visible);
    }

    /**
     * Keeps the list on screen. tight: back inside the normal range (no gaps); otherwise only far enough that at least
     * one row still shows, so the gap left by moving a spot stays until the player scrolls.
     */
    private void clampScroll(boolean tight) {
        if (tight) {
            this.scroll = Math.max(0, Math.min(maxScroll(), this.scroll));
        } else {
            this.scroll = Math.max(-(this.visible - 1), Math.min(Math.max(0, this.rows.size() - 1), this.scroll));
        }
    }

    /** Scrolls just enough that this row is on screen, closing any gap. */
    private void showRow(int index) {
        if (index < this.scroll) {
            this.scroll = index;
        } else if (index >= this.scroll + this.visible) {
            this.scroll = index - this.visible + 1;
        }
        clampScroll(true);
    }

    @Override
    protected boolean onScroll(double amount) {
        if (amount == 0.0) {
            return false;
        }
        clampScroll(true);
        this.scroll += amount < 0.0 ? 1 : -1;
        clampScroll(true);
        return true;
    }

    // ---- gliding and lighting up ----

    /** Call just before a change that can move rows: notes where every row is drawn right now. */
    private void rememberPlaces() {
        long now = Util.getMillis();
        this.placesBefore.clear();
        for (int i = 0; i < this.rows.size(); i++) {
            String name = this.rows.get(i).name();
            this.placesBefore.put(name, rowTop(i - this.scroll) + glideOffset(name, now));
        }
    }

    /** Call just after the change: each row starts where it was drawn and glides to its new place. */
    private void startGlide() {
        this.glideOffsets.clear();
        // A row coming from far away starts just off the list instead of flying across it.
        float limit = (this.visible + 1) * STEP;
        for (int i = 0; i < this.rows.size(); i++) {
            String name = this.rows.get(i).name();
            Float before = this.placesBefore.get(name);
            if (before != null) {
                float offset = before - rowTop(i - this.scroll);
                if (offset != 0.0F) {
                    this.glideOffsets.put(name, Math.max(-limit, Math.min(limit, offset)));
                }
            }
        }
        this.placesBefore.clear();
        this.glideStart = Util.getMillis();
    }

    /** How far (in pixels) the row with this name is still drawn from its real place. */
    private float glideOffset(String name, long now) {
        if (this.glideOffsets.isEmpty()) {
            return 0.0F;
        }
        long elapsed = now - this.glideStart;
        if (elapsed >= GLIDE_MS || elapsed < 0) {
            this.glideOffsets.clear();
            return 0.0F;
        }
        Float offset = this.glideOffsets.get(name);
        if (offset == null) {
            return 0.0F;
        }
        float left = 1.0F - elapsed / (float) GLIDE_MS;
        return offset * left * left; // fast at first, settling gently
    }

    private void lightUp(String name) {
        this.glowName = name;
        this.glowStart = Util.getMillis();
    }

    /** 1 while the lit row is fully lit, fading to 0. */
    private float glowStrength(long now) {
        if (this.glowName == null) {
            return 0.0F;
        }
        long elapsed = now - this.glowStart;
        if (elapsed < 0 || elapsed >= GLOW_HOLD_MS + GLOW_FADE_MS) {
            return 0.0F;
        }
        return elapsed < GLOW_HOLD_MS ? 1.0F : 1.0F - (elapsed - GLOW_HOLD_MS) / (float) GLOW_FADE_MS;
    }

    // ---- what is where ----

    private int rowTop(int screenRow) {
        return TOP + screenRow * STEP;
    }

    /** The list row under the mouse, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < this.rowLeft || mouseX >= this.rowRight || mouseY < TOP) {
            return -1;
        }
        int screenRow = (int) ((mouseY - TOP) / STEP);
        if (screenRow >= this.visible || mouseY - rowTop(screenRow) >= ROW) {
            return -1;
        }
        int index = this.scroll + screenRow;
        return index >= 0 && index < this.rows.size() ? index : -1;
    }

    /** Which part of a row is at this x (the y is already known to be on the row). */
    private int partAt(double mouseX, double mouseY, int index) {
        double y = mouseY - rowTop(index - this.scroll);
        boolean buttonHeight = y >= 2 && y < 18;
        if (buttonHeight && mouseX >= this.rowLeft + 2 && mouseX < this.rowLeft + 4 + ICON + 2) {
            return PICTURE;
        }
        if (buttonHeight && mouseX >= this.rowRight - 22 && mouseX < this.rowRight - 6) {
            return DELETE;
        }
        if (this.confirmRow == index) {
            return ROW_ONLY;
        }
        if (buttonHeight && mouseX >= this.rowRight - 42 && mouseX < this.rowRight - 26) {
            return DOWN;
        }
        if (buttonHeight && mouseX >= this.rowRight - 62 && mouseX < this.rowRight - 46) {
            return UP;
        }
        if (buttonHeight && mouseX >= this.renameLeft && mouseX < this.renameRight) {
            return RENAME;
        }
        return ROW_ONLY;
    }

    private void updateHover(double mouseX, double mouseY) {
        layoutButtons();
        this.hoveredRow = rowAt(mouseX, mouseY);
        boolean buttonRow = mouseY >= this.buttonTop && mouseY < this.buttonTop + 20;
        if (this.hoveredRow >= 0) {
            this.hoveredPart = partAt(mouseX, mouseY, this.hoveredRow);
        } else if (buttonRow && mouseX >= this.doneLeft && mouseX < this.doneRight) {
            this.hoveredPart = DONE;
        } else if (buttonRow && !UNDO_STEPS.isEmpty() && mouseX >= this.undoLeft && mouseX < this.undoRight) {
            this.hoveredPart = UNDO;
        } else {
            this.hoveredPart = NOTHING;
        }
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        updateHover(mouseX, mouseY);
        int index = this.hoveredRow;
        int part = this.hoveredPart;
        if (part != DELETE) {
            this.confirmRow = -1;
        }
        if (part == DONE) {
            click(1.0F);
            onClose();
        } else if (part == UNDO) {
            undoLast();
        } else if (index >= 0) {
            clickRow(index, part);
        }
        return true;
    }

    private void clickRow(int index, int part) {
        ClientState.Entry entry = this.rows.get(index);
        if (part == UP && index > 0) {
            click(1.2F);
            rememberPlaces();
            swap(index, index - 1);
            this.scroll--;
            startGlide();
            lightUp(entry.name());
            remember(new Change(UP, index - 1, entry, entry));
            send(EditPayload.MOVE_UP, index, entry.name(), "");
        } else if (part == DOWN && index < this.rows.size() - 1) {
            click(0.9F);
            rememberPlaces();
            swap(index, index + 1);
            this.scroll++;
            startGlide();
            lightUp(entry.name());
            remember(new Change(DOWN, index + 1, entry, entry));
            send(EditPayload.MOVE_DOWN, index, entry.name(), "");
        } else if (part == DELETE) {
            click(1.0F);
            if (this.confirmRow == index && entry.name().equals(this.confirmName)) {
                this.confirmRow = -1;
                rememberPlaces();
                this.rows.remove(index);
                clampScroll(false);
                startGlide();
                remember(new Change(DELETE, index, entry, null));
                send(EditPayload.DELETE, index, entry.name(), "");
            } else {
                this.confirmRow = index;
                this.confirmName = entry.name();
            }
        } else if (part == PICTURE) {
            ItemStack held = heldItem();
            if (!held.isEmpty()) {
                click(1.0F);
                ClientState.Entry changed = new ClientState.Entry(entry.name(), held.copyWithCount(1),
                        BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
                this.rows.set(index, changed);
                if (!changed.iconId().equals(entry.iconId())) {
                    remember(new Change(PICTURE, index, entry, changed));
                }
                send(EditPayload.PICTURE, index, entry.name(), "");
            }
        } else if (part == RENAME) {
            click(1.0F);
            List<ClientState.Entry> others = List.copyOf(this.rows);
            Minecraft.getInstance().setScreen(new NameScreen(Component.translatable("screen.jumper.name.rename"),
                    entry.icon(), entry.name(), others, index, name -> rename(index, entry, name), this));
        }
    }

    /** The name box said Save. The row may have moved meanwhile only if the server changed the list; check by name. */
    private void rename(int index, ClientState.Entry entry, String name) {
        if (name.equals(entry.name()) || index >= this.rows.size()
                || !this.rows.get(index).name().equals(entry.name())) {
            return;
        }
        ClientState.Entry before = this.rows.get(index);
        ClientState.Entry after = new ClientState.Entry(name, before.icon(), before.iconId());
        this.rows.set(index, after);
        remember(new Change(RENAME, index, before, after));
        send(EditPayload.RENAME, index, entry.name(), name);
    }

    private void remember(Change change) {
        UNDO_STEPS.add(change);
        if (UNDO_STEPS.size() > MAX_UNDO) {
            UNDO_STEPS.remove(0);
        }
    }

    /** Takes back the newest change: shows it at once and asks the server to do the same. */
    private void undoLast() {
        if (UNDO_STEPS.isEmpty()) {
            return;
        }
        Change change = UNDO_STEPS.remove(UNDO_STEPS.size() - 1);
        this.confirmRow = -1;
        rememberPlaces();
        boolean done = applyUndo(change);
        startGlide();
        if (!done) {
            // The list doesn't look the way that change left it; stop rather than change the wrong spot.
            UNDO_STEPS.clear();
        }
    }

    private boolean applyUndo(Change change) {
        int index = change.index();
        String name = change.before().name();
        if (change.kind() == PICTURE || change.kind() == RENAME) {
            if (!rowIs(index, change.after().name())) {
                return false;
            }
            click(1.0F);
            this.rows.set(index, change.before());
            showRow(index);
            lightUp(name);
            if (change.kind() == PICTURE) {
                send(EditPayload.PICTURE_SET, index, change.after().name(), change.before().iconId());
            } else {
                send(EditPayload.RENAME, index, change.after().name(), name);
            }
            return true;
        }
        if (change.kind() == UP) {
            // It went from index + 1 to index; move it back down.
            if (!rowIs(index, name) || index + 1 >= this.rows.size()) {
                return false;
            }
            click(0.9F);
            swap(index, index + 1);
            showRow(index + 1);
            lightUp(name);
            send(EditPayload.MOVE_DOWN, index, name, "");
            return true;
        }
        if (change.kind() == DOWN) {
            // It went from index - 1 to index; move it back up.
            if (!rowIs(index, name) || index < 1) {
                return false;
            }
            click(1.2F);
            swap(index, index - 1);
            showRow(index - 1);
            lightUp(name);
            send(EditPayload.MOVE_UP, index, name, "");
            return true;
        }
        if (change.kind() == DELETE) {
            if (ClientEdits.taken(name, this.rows, -1)) {
                return false;
            }
            click(1.0F);
            int at = Math.min(index, this.rows.size());
            this.rows.add(at, change.before());
            showRow(at);
            lightUp(name);
            send(EditPayload.RESTORE, at, name, "");
            return true;
        }
        return false;
    }

    private boolean rowIs(int index, String name) {
        return index >= 0 && index < this.rows.size() && this.rows.get(index).name().equals(name);
    }

    private void swap(int a, int b) {
        ClientState.Entry first = this.rows.get(a);
        this.rows.set(a, this.rows.get(b));
        this.rows.set(b, first);
    }

    private static ItemStack heldItem() {
        var player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getMainHandItem();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The inventory key (E unless rebound) closes the list, the same as Esc and as on the wheel.
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_UP || keyCode == InputConstants.KEY_DOWN) {
            return onScroll(keyCode == InputConstants.KEY_UP ? 1.0 : -1.0);
        }
        // Ctrl+Z (Cmd+Z on a Mac: the game counts Cmd as Ctrl there).
        if (keyCode == InputConstants.KEY_Z && hasControlDown() && !hasShiftDown()) {
            undoLast();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No super.render: no blur, the world stays visible behind the list.
        updateHover(mouseX, mouseY);
        long now = Util.getMillis();
        graphics.fill(0, 0, this.width, this.height, COLOR_BACK);
        graphics.drawCenteredString(this.font, Component.translatable("screen.jumper.list.count", this.rows.size(),
                ClientState.slots()), this.width / 2, 6, COLOR_TEXT);
        // Rows gliding in or out are cut off at the edges of the list.
        graphics.enableScissor(0, TOP, this.width, TOP + this.visible * STEP);
        int litRow = -1;
        boolean lit = glowStrength(now) > 0.0F;
        int first = Math.max(0, this.scroll - this.visible - 1);
        int last = Math.min(this.rows.size(), this.scroll + 2 * this.visible + 1);
        for (int index = first; index < last; index++) {
            if (lit && this.rows.get(index).name().equals(this.glowName)) {
                litRow = index; // drawn last, so rows gliding past go under it
                continue;
            }
            drawRowAt(graphics, index, now);
        }
        if (litRow >= 0) {
            drawRowAt(graphics, litRow, now);
        }
        graphics.disableScissor();
        drawScrollbar(graphics);
        drawShrunk(graphics, hint(), this.width / 2, this.height - 42, this.width - 12, COLOR_TEXT_SOFT);
        drawButton(graphics, this.doneLeft, this.doneRight, Component.translatable("screen.jumper.list.done"),
                this.hoveredPart == DONE);
        if (!UNDO_STEPS.isEmpty()) {
            drawButton(graphics, this.undoLeft, this.undoRight, Component.translatable("screen.jumper.list.undo"),
                    this.hoveredPart == UNDO);
        }
    }

    private void drawButton(GuiGraphics graphics, int left, int right, Component label, boolean hovered) {
        graphics.fill(left, this.buttonTop, right, this.buttonTop + 20, hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        graphics.drawCenteredString(this.font, label, (left + right) / 2, this.buttonTop + 6,
                hovered ? COLOR_TEXT : COLOR_TEXT_SOFT);
    }

    /** Draws one row where it is right now (its place, plus what is left of a glide), if that is inside the list. */
    private void drawRowAt(GuiGraphics graphics, int index, long now) {
        int y = Math.round(rowTop(index - this.scroll) + glideOffset(this.rows.get(index).name(), now));
        if (y <= TOP - ROW || y >= TOP + this.visible * STEP) {
            return;
        }
        drawRow(graphics, index, y, now);
        // The line under the last spot that still works with no power or an unloaded base.
        int tierOneSlots = ClientState.baseSlots();
        if (index == tierOneSlots - 1 && ClientState.slots() > tierOneSlots && index < this.rows.size() - 1) {
            graphics.fill(this.rowLeft, y + ROW, this.rowRight, y + ROW + 1, COLOR_DIVIDER);
        }
    }

    private void drawRow(GuiGraphics graphics, int index, int y, long now) {
        ClientState.Entry entry = this.rows.get(index);
        boolean confirming = this.confirmRow == index;
        boolean hovered = this.hoveredRow == index;
        boolean locked = index >= ClientState.usable();
        graphics.fill(this.rowLeft, y, this.rowRight, y + ROW,
                confirming ? COLOR_ROW_DELETE : hovered ? COLOR_ROW_HOVER : COLOR_ROW);
        float glow = entry.name().equals(this.glowName) ? glowStrength(now) : 0.0F;
        if (glow > 0.0F) {
            graphics.fill(this.rowLeft, y, this.rowRight, y + ROW, (int) (0xA0 * glow) << 24 | GLOW_FILL_RGB);
            int line = (int) (0xFF * glow) << 24 | GLOW_LINE_RGB;
            graphics.fill(this.rowLeft, y, this.rowRight, y + 1, line);
            graphics.fill(this.rowLeft, y + ROW - 1, this.rowRight, y + ROW, line);
        }
        // The spot's number, to the left of the row.
        String number = Integer.toString(index + 1);
        int numberColor = mix(locked ? COLOR_TEXT_LOCKED : COLOR_TEXT_SOFT, COLOR_DIVIDER, glow);
        graphics.drawString(this.font, number, this.numberRight - this.font.width(number), y + 6, numberColor);

        graphics.renderItem(entry.icon(), this.rowLeft + 4, y + 2);
        int textLeft = this.rowLeft + 26;
        if (confirming) {
            String question = Component.translatable("screen.jumper.list.delete_question", entry.name()).getString();
            graphics.drawString(this.font, fit(question, this.rowRight - 66 - textLeft), textLeft, y + 6,
                    COLOR_TEXT);
        } else {
            // Spots past the working slots (no power or base unloaded) are drawn dim, as on the wheel.
            int color = locked ? COLOR_TEXT_LOCKED : hovered || glow > 0.0F ? COLOR_TEXT : COLOR_TEXT_SOFT;
            graphics.drawString(this.font, fit(entry.name(), this.renameLeft - 4 - textLeft), textLeft, y + 6, color);
            boolean renameHovered = hovered && this.hoveredPart == RENAME;
            graphics.fill(this.renameLeft, y + 2, this.renameRight, y + 18,
                    renameHovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
            graphics.drawCenteredString(this.font, this.renameLabel, (this.renameLeft + this.renameRight) / 2, y + 6,
                    renameHovered ? COLOR_TEXT : COLOR_TEXT_SOFT);
        }
        drawSquare(graphics, this.rowRight - 62, y + 2, hovered && this.hoveredPart == UP);
        drawArrow(graphics, this.rowRight - 58, y + 8, true, index > 0);
        drawSquare(graphics, this.rowRight - 42, y + 2, hovered && this.hoveredPart == DOWN);
        drawArrow(graphics, this.rowRight - 38, y + 8, false, index < this.rows.size() - 1);
        drawSquare(graphics, this.rowRight - 22, y + 2, hovered && this.hoveredPart == DELETE);
        drawCross(graphics, this.rowRight - 18, y + 6);
    }

    /** Blends two opaque colours: t = 0 gives a, t = 1 gives b. */
    private static int mix(int a, int b, float t) {
        if (t <= 0.0F) {
            return a;
        }
        if (t >= 1.0F) {
            return b;
        }
        int red = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int green = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int blue = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void drawSquare(GuiGraphics graphics, int x, int y, boolean hovered) {
        graphics.fill(x, y, x + 16, y + 16, hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
    }

    /** A small filled triangle, 7 wide and 4 high. */
    private static void drawArrow(GuiGraphics graphics, int x, int y, boolean up, boolean usable) {
        int color = usable ? COLOR_ARROW : COLOR_TEXT_LOCKED & 0x80FFFFFF;
        for (int n = 0; n < 4; n++) {
            int half = up ? n : 3 - n;
            graphics.fill(x + 3 - half, y + n, x + 4 + half, y + n + 1, color);
        }
    }

    /** A 7 x 7 X. */
    private static void drawCross(GuiGraphics graphics, int x, int y) {
        for (int i = 0; i < 7; i++) {
            graphics.fill(x + i, y + i, x + i + 1, y + i + 1, COLOR_CROSS);
            graphics.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, COLOR_CROSS);
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int size = this.rows.size();
        if (size <= this.visible) {
            return;
        }
        int x = this.rowRight + 4;
        int trackTop = TOP;
        int trackHeight = this.visible * STEP - 2;
        graphics.fill(x, trackTop, x + 3, trackTop + trackHeight, COLOR_TRACK);
        int thumb = Math.max(8, trackHeight * this.visible / size);
        int first = Math.max(0, Math.min(maxScroll(), this.scroll));
        int thumbTop = trackTop + (trackHeight - thumb) * first / Math.max(1, maxScroll());
        graphics.fill(x, thumbTop, x + 3, thumbTop + thumb, COLOR_THUMB);
    }

    /** The text, cut with "..." if it's wider than the room. */
    private String fit(String text, int room) {
        if (this.font.width(text) <= room) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, room - this.font.width("..."))).trim() + "...";
    }

    /** The line above "Done": what the thing under the mouse does. */
    private Component hint() {
        if (this.hoveredPart == UNDO && !UNDO_STEPS.isEmpty()) {
            return undoHint(UNDO_STEPS.get(UNDO_STEPS.size() - 1));
        }
        if (this.rows.isEmpty()) {
            return Component.translatable("screen.jumper.list.empty");
        }
        if (this.hoveredRow >= 0) {
            String name = this.rows.get(this.hoveredRow).name();
            if (this.confirmRow == this.hoveredRow) {
                return Component.translatable("screen.jumper.list.delete_confirm", name);
            }
            switch (this.hoveredPart) {
                case PICTURE:
                    return Component.translatable(heldItem().isEmpty() ? "screen.jumper.list.picture_empty"
                            : "screen.jumper.list.picture", name);
                case RENAME:
                    return Component.translatable("screen.jumper.list.rename_hint", name);
                case UP:
                    return Component.translatable("screen.jumper.list.up", name);
                case DOWN:
                    return Component.translatable("screen.jumper.list.down", name);
                case DELETE:
                    return Component.translatable("screen.jumper.list.delete", name);
                default:
                    break;
            }
        }
        int tierOneSlots = ClientState.baseSlots();
        if (ClientState.slots() > tierOneSlots && this.rows.size() > tierOneSlots) {
            return Component.translatable("screen.jumper.list.divider", tierOneSlots);
        }
        return Component.empty();
    }

    /** What Undo would do now. */
    private static Component undoHint(Change change) {
        String name = change.before().name();
        return switch (change.kind()) {
            case PICTURE -> Component.translatable("screen.jumper.list.undo_picture", name);
            case RENAME -> Component.translatable("screen.jumper.list.undo_rename", change.after().name(), name);
            case UP -> Component.translatable("screen.jumper.list.undo_up", name);
            case DOWN -> Component.translatable("screen.jumper.list.undo_down", name);
            default -> Component.translatable("screen.jumper.list.undo_delete", name);
        };
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

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }
}
