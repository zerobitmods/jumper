package com.jumper.client;

import com.jumper.net.EditPayload;
import com.jumper.net.JumperNetwork;
import com.jumper.net.WheelActionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The jump wheel: a ring cut into one wedge per spot, eight spots to a page. Hold the key, move the mouse onto a wedge,
 * let go: you jump there. Scroll (or the left / right arrow keys) flips to the next eight. The inventory key closes it, like Esc. Buttons sit in the bottom
 * corners: "Save here" on the left (while a slot is free); "Home" (your anchor) and "Back" (once there's somewhere
 * to jump back to) on the right. Under them, the bottom row is the hint line (what letting go will do, or why not); the
 * ring is sized so it never reaches the buttons or the hint, at any window size (scripts/fit_check.py checks this).
 * Letting go on the middle circle, or on nothing, closes the wheel without doing anything.
 *
 * A name too long for its wedge goes on two lines (broken at a space where it can), when the ring is thick enough.
 *
 * What you see is exactly what reacts: a wedge or button is chosen only while the mouse is on its drawn shape (the same
 * test draws it and picks it).
 *
 * A quick tap of the key (released before the mouse moved) leaves the wheel open, so it also works by clicking.
 *
 * Nothing here is trusted by the server; a pick is only a request.
 */
public class WheelScreen extends WheelScreenBase {
    /** Spots shown at once. */
    public static final int PER_PAGE = 8;
    /** Pixels left empty along each edge between two wedges. */
    private static final double GAP = 1.0;
    /** A release this soon after opening, with nothing chosen, counts as a tap (wheel stays open). */
    private static final int TAP_TICKS = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_MARGIN = 8;
    /** Space under the hint line, the bottom row of the screen. */
    private static final int HINT_MARGIN = 4;
    /** Smallest ring, for windows too small to keep everything apart. */
    private static final int MIN_OUTER = 50;

    /** What the mouse can be on, besides a wedge (0 and up, counted on the current page). */
    private static final int NOTHING = -1;
    private static final int HUB = -2;
    private static final int SAVE = -3;
    private static final int BACK = -4;
    private static final int HOME = -5;
    private static final int EDIT = -6;

    private static final int COLOR_DIM = 0x50000000;
    private static final int COLOR_WEDGE = 0xB8141418;
    private static final int COLOR_WEDGE_HOVER = 0xD8207068;
    private static final int COLOR_HUB = 0xC80C0C10;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SOFT = 0xFFB8C0C8;
    private static final int COLOR_WEDGE_LOCKED = 0x70141418;
    private static final int COLOR_WEDGE_LOCKED_HOVER = 0xB0502828;
    private static final int COLOR_TEXT_LOCKED = 0xFF707478;
    private static final int COLOR_POWER = 0xFF1D9E75;
    private static final int COLOR_POWER_EMPTY = 0x50FFFFFF;
    private static final int COLOR_NO_POWER = 0xFFE24B4A;
    private static final int COLOR_NOT_LOADED = 0xFFEF9F27;
    /** The power ring sits just inside the edge of the middle circle, this many pixels thick. */
    private static final int RING_THICKNESS = 3;
    /** A ring thinner than this (from the middle circle to the outer edge) has no room for names on two lines. */
    private static final int TWO_LINE_MIN_RING = 44;

    /** Wording for an anchor that isn't fully working: 0 out of power, 1 base not loaded, 2 both. */
    private static final String[] HINT_STATE = {"screen.jumper.wheel.no_power_hint",
            "screen.jumper.wheel.unloaded_hint", "screen.jumper.wheel.unloaded_no_power_hint"};
    private static final String[] HINT_LIMITED = {"screen.jumper.wheel.limited_no_power_hint",
            "screen.jumper.wheel.limited_unloaded_hint", "screen.jumper.wheel.limited_unloaded_no_power_hint"};
    private static final String[] HINT_LOCKED = {"screen.jumper.wheel.locked_no_power",
            "screen.jumper.wheel.locked_unloaded", "screen.jumper.wheel.locked_unloaded_no_power"};

    private final List<ClientState.Entry> spots;
    private final int slots;
    private final boolean hasBack;
    private final int pages;
    private int page;

    /** Radius of the hole in the middle and of the ring's outer edge, in screen pixels. */
    private int inner;
    private int outer;
    /**
     * The ring as horizontal strips for each possible number of wedges (worked out the first time it's needed, per
     * screen size): 4 numbers each (row, first column, end column, id), measured from the middle of the screen.
     */
    private int[][] stripsByCount = new int[PER_PAGE + 1][];
    /**
     * The power ring's pixels, 3 numbers each (row, column, how far round clockwise from the top in thousandths),
     * measured from the middle of the screen and sorted by row then column.
     */
    private int[] ringPixels = new int[0];
    /**
     * The names on the current page as drawn: the first line, and the second line (null when the name is on one line).
     * Worked out once per page and label width, not every frame.
     */
    private final String[] nameTop = new String[PER_PAGE];
    private final String[] nameBottom = new String[PER_PAGE];
    private int namesPage = -1;
    private int namesWidth = -1;
    private Component saveLabel = Component.empty();
    private Component backLabel = Component.empty();
    private Component homeLabel = Component.empty();
    private int saveLeft;
    private int saveRight;
    private int backLeft;
    private int backRight;
    private int homeLeft;
    private int homeRight;
    private int buttonTop;
    private int hintTop;
    private int editRight;
    private Component editLabel;

    private int ticksOpen;
    /** True once the key was let go without picking: the wheel then works by clicking. */
    private boolean clickMode;
    /** What the mouse is on: a wedge number on this page, or one of the codes above. */
    private int hovered = NOTHING;

    public WheelScreen() {
        super(Component.translatable("screen.jumper.wheel.title"));
        // A copy, so a list update arriving while the wheel is open can't shift what a wedge means on screen.
        this.spots = List.copyOf(ClientState.spots());
        this.slots = ClientState.slots();
        this.hasBack = ClientState.hasBack();
        this.pages = Math.max(1, (this.spots.size() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected void init() {
        // The bottom of the screen, from the bottom up: the hint line, alone on its row so it can use the full width,
        // then the buttons in the two corners.
        this.hintTop = this.height - HINT_MARGIN - 8;
        this.buttonTop = this.hintTop - 4 - BUTTON_HEIGHT;
        this.saveLabel = Component.translatable("screen.jumper.wheel.save");
        this.backLabel = Component.translatable("screen.jumper.wheel.back");
        this.homeLabel = Component.translatable("screen.jumper.wheel.home");
        this.saveLeft = BUTTON_MARGIN;
        this.saveRight = this.saveLeft + this.font.width(this.saveLabel) + 16;
        this.backRight = this.width - BUTTON_MARGIN;
        this.backLeft = this.backRight - this.font.width(this.backLabel) - 16;
        // Home sits just left of where Back goes (Back's place stays reserved even before there's a back spot).
        this.homeRight = this.backLeft - 4;
        this.homeLeft = this.homeRight - this.font.width(this.homeLabel) - 16;
        // Top left: the list screen, for renaming, reordering and deleting.
        this.editLabel = Component.translatable("screen.jumper.wheel.edit");
        this.editRight = BUTTON_MARGIN + this.font.width(this.editLabel) + 16;

        // The ring uses most of the screen's shorter side, so there's room for pictures and names, but never reaches
        // the hint line or a button (the places of Save, Home + Back and Edit stay reserved even when not shown).
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int room = this.hintTop - 3 - centerY;
        room = Math.min(room, distanceTo(centerX, centerY, this.saveLeft, this.buttonTop, this.saveRight,
                this.buttonTop + BUTTON_HEIGHT) - 2);
        room = Math.min(room, distanceTo(centerX, centerY, this.homeLeft, this.buttonTop, this.backRight,
                this.buttonTop + BUTTON_HEIGHT) - 2);
        room = Math.min(room, distanceTo(centerX, centerY, BUTTON_MARGIN, BUTTON_MARGIN, this.editRight,
                BUTTON_MARGIN + BUTTON_HEIGHT) - 2);
        int preferred = Math.max(70, Math.min(230, Math.round(Math.min(this.width, this.height) * 0.42F)));
        this.outer = Math.max(MIN_OUTER, Math.min(preferred, room));
        // The middle circle is big enough for three short lines of text inside the power ring.
        this.inner = Math.max(30, Math.round(this.outer * 0.31F));
        this.stripsByCount = new int[PER_PAGE + 1][];
        this.namesPage = -1;
        this.ringPixels = buildRing(this.inner - 2 - RING_THICKNESS, this.inner - 2);
    }

    /** Whole pixels from a point to the nearest point of a rectangle. */
    private static int distanceTo(int x, int y, int left, int top, int right, int bottom) {
        int dx = Math.max(Math.max(left - x, 0), x - right);
        int dy = Math.max(Math.max(top - y, 0), y - bottom);
        return (int) Math.floor(Math.sqrt((double) dx * dx + (double) dy * dy));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** "+ Save here" only shows while a working slot is free (the power numbers can change while the wheel is open). */
    private boolean canSave() {
        return ClientState.here() == ClientState.HERE_OK
                && this.spots.size() < Math.min(this.slots, ClientState.usable());
    }

    /**
     * True if this spot (index in the whole list) can't be jumped to right now: the player is outside the anchor's
     * reach, the slot needs power or a loaded base, or the spot itself is out of reach.
     */
    private static boolean locked(int index) {
        return ClientState.here() != ClientState.HERE_OK || index >= ClientState.usable() || ClientState.far(index);
    }

    /** True if even Home can't work from where the player stands. */
    private static boolean outOfRange() {
        return ClientState.here() == ClientState.HERE_TOO_FAR || ClientState.here() == ClientState.HERE_OTHER_DIMENSION;
    }

    private int pageStart() {
        return this.page * PER_PAGE;
    }

    /** How many wedges the current page has. */
    private int pageCount() {
        return Math.max(0, Math.min(PER_PAGE, this.spots.size() - pageStart()));
    }

    // ---- shape: one test for both drawing and picking ----

    /**
     * What part of a ring with this many wedges is at a point measured from the middle of the screen: a wedge number,
     * {@link #HUB} for the middle circle, or {@link #NOTHING}. Wedge 0 is centered straight up; the rest follow clockwise.
     */
    private int ringAt(double dx, double dy, int count) {
        double distanceSquared = dx * dx + dy * dy;
        double hubRadius = this.inner - 2.0;
        if (distanceSquared < hubRadius * hubRadius) {
            return HUB;
        }
        if (count == 0 || distanceSquared < (double) this.inner * this.inner
                || distanceSquared > (double) this.outer * this.outer) {
            return NOTHING;
        }
        double turn = (Math.atan2(dy, dx) + Math.PI / 2.0) / (2.0 * Math.PI);
        double position = (turn - Math.floor(turn)) * count + 0.5;
        double whole = Math.floor(position);
        if (count > 1) {
            // Distance along the arc to the nearer edge of this wedge, in pixels.
            double part = position - whole;
            double toEdge = Math.min(part, 1.0 - part) * (2.0 * Math.PI / count) * Math.sqrt(distanceSquared);
            if (toEdge < GAP) {
                return NOTHING;
            }
        }
        return (int) whole % count;
    }

    /** What is under the mouse: a wedge of this page, a button, the hub or nothing. */
    private int targetAt(double mouseX, double mouseY) {
        if (!this.spots.isEmpty() && mouseY >= BUTTON_MARGIN && mouseY < BUTTON_MARGIN + BUTTON_HEIGHT
                && mouseX >= BUTTON_MARGIN && mouseX < this.editRight) {
            return EDIT;
        }
        if (mouseY >= this.buttonTop && mouseY < this.buttonTop + BUTTON_HEIGHT) {
            if (canSave() && mouseX >= this.saveLeft && mouseX < this.saveRight) {
                return SAVE;
            }
            if (this.hasBack && mouseX >= this.backLeft && mouseX < this.backRight) {
                return BACK;
            }
            if (mouseX >= this.homeLeft && mouseX < this.homeRight) {
                return HOME;
            }
        }
        return ringAt(mouseX - this.width / 2.0, mouseY - this.height / 2.0, pageCount());
    }

    private static boolean pickable(int target) {
        return target >= 0 || target == SAVE || target == BACK || target == HOME || target == EDIT;
    }

    private int[] strips(int count) {
        int[] cached = this.stripsByCount[count];
        if (cached != null) {
            return cached;
        }
        List<int[]> found = new ArrayList<>();
        for (int y = -this.outer; y < this.outer; y++) {
            int start = -this.outer;
            int current = NOTHING;
            for (int x = -this.outer; x <= this.outer; x++) {
                // The last column only ends whatever strip is open.
                int id = x == this.outer ? NOTHING : ringAt(x + 0.5, y + 0.5, count);
                if (id != current) {
                    if (current != NOTHING) {
                        found.add(new int[]{y, start, x, current});
                    }
                    start = x;
                    current = id;
                }
            }
        }
        int[] flat = new int[found.size() * 4];
        for (int i = 0; i < found.size(); i++) {
            System.arraycopy(found.get(i), 0, flat, i * 4, 4);
        }
        this.stripsByCount[count] = flat;
        return flat;
    }

    // ---- input ----

    @Override
    public void tick() {
        this.ticksOpen++;
        // The key-up event can be missed (for example when it happens in the same instant the wheel opens), so the
        // keyboard is also asked directly.
        if (!this.clickMode && JumperKeys.WHEEL.getKey().getType() == InputConstants.Type.KEYSYM
                && !InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(),
                        JumperKeys.WHEEL.getKey().getValue())) {
            released();
        }
    }

    /** The key (or mouse button) that opened the wheel was let go. */
    private void released() {
        if (this.clickMode) {
            return;
        }
        if (pickable(this.hovered)) {
            pick(this.hovered);
        } else if (this.ticksOpen <= TAP_TICKS) {
            this.clickMode = true;
        } else {
            onClose();
        }
    }

    private void pick(int target) {
        click(1.0F);
        if (target >= 0 && pageStart() + target < this.spots.size()) {
            JumperNetwork.sendToServer(WheelActionPayload.jump(pageStart() + target));
        } else if (target == SAVE && canSave()) {
            // Name it first: the box opens with the suggested name filled in and selected.
            ItemStack picture = ClientEdits.suggestedPicture();
            List<ClientState.Entry> current = ClientState.spots();
            Minecraft.getInstance().setScreen(new NameScreen(Component.translatable("screen.jumper.name.new"), picture,
                    ClientEdits.suggestedName(picture, current), current, -1,
                    name -> ClientEdits.send(EditPayload.SAVE, 0, "", name), null));
            return;
        } else if (target == EDIT && !this.spots.isEmpty()) {
            Minecraft.getInstance().setScreen(new SpotListScreen());
            return;
        } else if (target == BACK && this.hasBack) {
            JumperNetwork.sendToServer(WheelActionPayload.back());
        } else if (target == HOME) {
            JumperNetwork.sendToServer(WheelActionPayload.home());
        }
        onClose();
    }

    /** Moves forward (1) or back (-1) one page, wrapping around. */
    private boolean flip(int direction) {
        if (this.pages <= 1) {
            return false;
        }
        this.page = Math.floorMod(this.page + direction, this.pages);
        click(1.4F);
        return true;
    }

    @Override
    protected boolean onScroll(double amount) {
        if (amount == 0.0) {
            return false;
        }
        return flip(amount < 0.0 ? 1 : -1);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (JumperKeys.WHEEL.matches(keyCode, scanCode)) {
            released();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.clickMode && JumperKeys.WHEEL.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        // The inventory key (E unless rebound) closes the wheel, the same as Esc.
        if (Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_RIGHT) {
            flip(1);
            return true;
        }
        if (keyCode == InputConstants.KEY_LEFT) {
            flip(-1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (JumperKeys.WHEEL.matchesMouse(button)) {
            released();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.hovered = targetAt(mouseX, mouseY);
            if (pickable(this.hovered)) {
                pick(this.hovered);
            } else if (this.clickMode) {
                onClose();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No super.render: the world stays visible behind the wheel (no blur, no dark menu background).
        this.hovered = targetAt(mouseX, mouseY);
        graphics.fill(0, 0, this.width, this.height, COLOR_DIM);

        final int centerX = this.width / 2;
        final int centerY = this.height / 2;
        final int hoveredNow = this.hovered;
        final int count = pageCount();
        final int[] drawn = strips(count);
        // One batch for all the strips instead of one draw per strip.
        graphics.drawManaged(() -> {
            for (int i = 0; i < drawn.length; i += 4) {
                int id = drawn[i + 3];
                int color;
                if (id == HUB) {
                    color = COLOR_HUB;
                } else if (locked(pageStart() + id)) {
                    color = id == hoveredNow ? COLOR_WEDGE_LOCKED_HOVER : COLOR_WEDGE_LOCKED;
                } else {
                    color = id == hoveredNow ? COLOR_WEDGE_HOVER : COLOR_WEDGE;
                }
                graphics.fill(centerX + drawn[i + 1], centerY + drawn[i], centerX + drawn[i + 2],
                        centerY + drawn[i] + 1, color);
            }
        });

        // Each wedge: a picture with its name under it, a little outside the wedge's middle (it's wider out there).
        int middle = this.inner + Math.round((this.outer - this.inner) * 0.6F);
        int labelWidth = count <= 1 ? 110
                : Math.max(24, Math.min(110, (int) (2.0 * middle * Math.sin(Math.PI / count)) - 10));
        if (this.namesPage != this.page || this.namesWidth != labelWidth) {
            layoutNames(count, labelWidth);
        }
        for (int i = 0; i < count; i++) {
            ClientState.Entry spot = this.spots.get(pageStart() + i);
            double angle = -Math.PI / 2.0 + i * (2.0 * Math.PI / count);
            int x = centerX + (int) Math.round(Math.cos(angle) * middle);
            int y = centerY + (int) Math.round(Math.sin(angle) * middle);
            // With the name on two lines the picture moves up, so picture and name stay centered on the same point.
            boolean twoLines = this.nameBottom[i] != null;
            int pictureTop = twoLines ? y - 18 : y - 13;
            graphics.renderItem(spot.icon(), x - 8, pictureTop);
            boolean isLocked = locked(pageStart() + i);
            if (isLocked) {
                // A dark veil over the picture (drawn above items, which sit higher than flat fills).
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                graphics.fill(x - 8, pictureTop, x + 8, pictureTop + 16, 0xA0000000);
                graphics.pose().popPose();
            }
            int nameColor = isLocked ? COLOR_TEXT_LOCKED : i == hoveredNow ? COLOR_TEXT : COLOR_TEXT_SOFT;
            if (twoLines) {
                graphics.drawCenteredString(this.font, this.nameTop[i], x, y, nameColor);
                graphics.drawCenteredString(this.font, this.nameBottom[i], x, y + 10, nameColor);
            } else {
                graphics.drawCenteredString(this.font, this.nameTop[i], x, y + 5, nameColor);
            }
        }

        drawHub(graphics, centerX, centerY);
        if (this.pages > 1) {
            // Above the ring, so the middle circle has room for the power.
            graphics.drawCenteredString(this.font, Component.translatable("screen.jumper.wheel.page", this.page + 1,
                    this.pages), centerX, Math.max(4, centerY - this.outer - 14), COLOR_TEXT);
        }

        if (!this.spots.isEmpty()) {
            drawButtonAt(graphics, BUTTON_MARGIN, this.editRight, BUTTON_MARGIN, this.editLabel, hoveredNow == EDIT,
                    false);
        }

        // Bottom corners: the two buttons.
        if (canSave()) {
            drawButton(graphics, this.saveLeft, this.saveRight, this.saveLabel, hoveredNow == SAVE, false);
        }
        if (this.hasBack) {
            drawButton(graphics, this.backLeft, this.backRight, this.backLabel, hoveredNow == BACK,
                    ClientState.here() != ClientState.HERE_OK);
        }
        drawButton(graphics, this.homeLeft, this.homeRight, this.homeLabel, hoveredNow == HOME, outOfRange());

        // Bottom row, under the ring and the buttons: what letting go (or clicking) will do, or why it won't.
        drawShrunk(graphics, hint(hoveredNow), centerX, this.hintTop, this.width - 8, COLOR_TEXT);
    }

    /** The line under the ring. */
    private Component hint(int hoveredNow) {
        int power = ClientState.power();
        int here = ClientState.here();
        // Which wording: 0 out of power, 1 base not loaded, 2 both.
        int state = power == ClientState.POWER_BOTH ? 2 : power == ClientState.POWER_NOT_LOADED ? 1 : 0;
        // Standing somewhere the anchor doesn't reach comes first: it explains every dimmed wedge at once.
        if (here == ClientState.HERE_OTHER_DIMENSION) {
            return Component.translatable("screen.jumper.wheel.other_dimension_hint");
        }
        if (here == ClientState.HERE_TOO_FAR) {
            return Component.translatable("screen.jumper.wheel.too_far_hint", ClientState.range());
        }
        if (hoveredNow == HOME) {
            return Component.translatable("screen.jumper.wheel.home_hint");
        }
        if (here == ClientState.HERE_LIMITED) {
            return Component.translatable(HINT_LIMITED[state], ClientState.baseRange());
        }
        if (hoveredNow >= 0) {
            int index = pageStart() + hoveredNow;
            String name = this.spots.get(index).name();
            if (index >= ClientState.usable()) {
                return Component.translatable(HINT_LOCKED[state], name);
            }
            if (ClientState.far(index)) {
                return Component.translatable("screen.jumper.wheel.spot_far", name);
            }
            return Component.translatable(this.clickMode ? "screen.jumper.wheel.jump_click"
                    : "screen.jumper.wheel.jump", name);
        }
        if (hoveredNow == SAVE) {
            return Component.translatable("screen.jumper.wheel.save_hint");
        }
        if (hoveredNow == BACK) {
            return Component.translatable("screen.jumper.wheel.back_hint");
        }
        if (hoveredNow == EDIT) {
            return Component.translatable("screen.jumper.wheel.edit_hint");
        }
        if (power == ClientState.POWER_NONE || power == ClientState.POWER_NOT_LOADED
                || power == ClientState.POWER_BOTH) {
            return Component.translatable(HINT_STATE[state]);
        }
        if (this.spots.isEmpty()) {
            return Component.translatable("screen.jumper.wheel.empty");
        }
        if (this.pages > 1) {
            return Component.translatable("screen.jumper.wheel.pages_hint");
        }
        return Component.translatable(this.clickMode ? "screen.jumper.wheel.click_mode" : "screen.jumper.wheel.idle");
    }

    /** The game's own button click, at a chosen pitch. */
    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
    }

    // ---- the middle circle: power and slots ----

    /**
     * The middle circle. With an anchor that needs power: a ring round its edge showing the stored charge clockwise
     * (green while it works, amber while the base isn't loaded), or all red when it's empty. Inside: the charge and
     * time left; or "No power"; or "Not loaded" with the stored charge (or "No power") under it; or "Out of range";
     * then slots used. Every line is shrunk to fit inside the ring.
     */
    private void drawHub(GuiGraphics graphics, int centerX, int centerY) {
        Component used = Component.literal(this.spots.size() + " / " + this.slots);
        int power = ClientState.power();
        boolean needsPower = power != ClientState.POWER_FREE;
        boolean unloaded = power == ClientState.POWER_NOT_LOADED || power == ClientState.POWER_BOTH;
        boolean empty = power == ClientState.POWER_NONE || power == ClientState.POWER_BOTH;
        long energy = ClientState.energyNow();
        long capacity = ClientState.capacity();
        // Stored charge in thousandths of full.
        int charge = capacity <= 0 ? 0 : (int) Math.max(0L, Math.min(1000L, (long) (energy * 1000.0 / capacity)));
        if (needsPower) {
            if (empty) {
                drawRing(graphics, centerX, centerY, 1000, COLOR_NO_POWER);
            } else {
                drawRing(graphics, centerX, centerY, charge, unloaded ? COLOR_NOT_LOADED : COLOR_POWER);
            }
        }

        // Text stays inside the ring (or the circle's edge when there is no ring), with a little air.
        int textRadius = this.inner - 2 - (needsPower ? RING_THICKNESS : 0) - 3;
        Component percent = Component.literal((charge / 10) + "%");
        if (outOfRange()) {
            drawHubLines(graphics, centerX, centerY, textRadius,
                    Component.translatable("screen.jumper.wheel.out_of_range"), COLOR_NO_POWER, null, COLOR_TEXT_SOFT,
                    used);
        } else if (unloaded) {
            // Said whenever the base isn't loaded. Under it: "No power", or what's stored (nothing drains meanwhile).
            drawHubLines(graphics, centerX, centerY, textRadius,
                    Component.translatable("screen.jumper.wheel.unloaded"), COLOR_NOT_LOADED,
                    empty ? Component.translatable("screen.jumper.wheel.no_power") : percent,
                    empty ? COLOR_NO_POWER : COLOR_TEXT_SOFT, used);
        } else if (empty) {
            drawHubLines(graphics, centerX, centerY, textRadius,
                    Component.translatable("screen.jumper.wheel.no_power"), COLOR_NO_POWER, null, COLOR_TEXT_SOFT,
                    used);
        } else if (needsPower) {
            long rate = ClientState.rate();
            drawHubLines(graphics, centerX, centerY, textRadius, percent, COLOR_TEXT,
                    rate <= 0 ? null : Component.literal(duration(energy / rate)), COLOR_TEXT_SOFT, used);
        } else {
            drawHubLines(graphics, centerX, centerY, textRadius, used, COLOR_TEXT, null, COLOR_TEXT_SOFT, null);
        }
    }

    /** One, two or three lines centered in the middle circle (middle and bottom may be null). */
    private void drawHubLines(GuiGraphics graphics, int centerX, int centerY, int radius, Component top, int topColor,
            Component middle, int middleColor, Component bottom) {
        int lines = 1 + (middle == null ? 0 : 1) + (bottom == null ? 0 : 1);
        int y = centerY - (lines * 10 - 2) / 2;
        drawFitted(graphics, top, centerX, centerY, y, radius, topColor);
        if (middle != null) {
            y += 10;
            drawFitted(graphics, middle, centerX, centerY, y, radius, middleColor);
        }
        if (bottom != null) {
            y += 10;
            drawFitted(graphics, bottom, centerX, centerY, y, radius, COLOR_TEXT_SOFT);
        }
    }

    /**
     * Draws a line of text centered on centerX with its top at y, shrunk if needed so it stays inside a circle of this
     * radius around (centerX, centerY).
     */
    private void drawFitted(GuiGraphics graphics, Component text, int centerX, int centerY, int y, int radius,
            int color) {
        // The circle is narrowest at whichever edge of the line (top or bottom) is farther from the middle.
        int away = Math.max(Math.abs(y - centerY), Math.abs(y + 8 - centerY));
        int room = away >= radius ? 8 : (int) (2.0 * Math.sqrt((double) radius * radius - (double) away * away));
        drawShrunk(graphics, text, centerX, y, room, color);
    }

    /**
     * Draws a line of text centered on centerX with its top at y. If it's wider than room it's drawn smaller (down to
     * half size), still centered on the same line.
     */
    private void drawShrunk(GuiGraphics graphics, Component text, int centerX, int y, int room, int color) {
        int width = this.font.width(text);
        if (width <= room) {
            graphics.drawCenteredString(this.font, text, centerX, y, color);
            return;
        }
        float scale = Math.max(0.5F, room / (float) width);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y + 4.0F * (1.0F - scale), 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(this.font, text, 0, 0, color);
        graphics.pose().popPose();
    }

    /** Ticks as "1 h 5 min", "37 min" or "45 s". */
    static String duration(long ticks) {
        long seconds = ticks / 20;
        if (seconds >= 3600) {
            return (seconds / 3600) + " h " + (seconds % 3600) / 60 + " min";
        }
        if (seconds >= 60) {
            return (seconds / 60) + " min";
        }
        return seconds + " s";
    }

    /** The ring's pixels between these two radii (see {@link #ringPixels}). Worked out once per screen size. */
    private static int[] buildRing(int innerRadius, int outerRadius) {
        List<int[]> found = new ArrayList<>();
        for (int y = -outerRadius; y < outerRadius; y++) {
            for (int x = -outerRadius; x < outerRadius; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double distanceSquared = px * px + py * py;
                if (distanceSquared < (double) innerRadius * innerRadius
                        || distanceSquared >= (double) outerRadius * outerRadius) {
                    continue;
                }
                double turn = (Math.atan2(py, px) + Math.PI / 2.0) / (2.0 * Math.PI);
                found.add(new int[]{y, x, (int) ((turn - Math.floor(turn)) * 1000.0)});
            }
        }
        int[] flat = new int[found.size() * 3];
        for (int i = 0; i < found.size(); i++) {
            System.arraycopy(found.get(i), 0, flat, i * 3, 3);
        }
        return flat;
    }

    /** Draws the ring: pixels up to filled (thousandths, clockwise from the top) in color, the rest faint. */
    private void drawRing(GuiGraphics graphics, int centerX, int centerY, int filled, int color) {
        final int[] pixels = this.ringPixels;
        graphics.drawManaged(() -> {
            // Neighbouring pixels in a row with the same color are drawn as one strip.
            int i = 0;
            while (i < pixels.length) {
                int row = pixels[i];
                int start = pixels[i + 1];
                int pixelColor = pixels[i + 2] < filled ? color : COLOR_POWER_EMPTY;
                int end = start + 1;
                int next = i + 3;
                while (next < pixels.length && pixels[next] == row && pixels[next + 1] == end
                        && (pixels[next + 2] < filled ? color : COLOR_POWER_EMPTY) == pixelColor) {
                    end++;
                    next += 3;
                }
                graphics.fill(centerX + start, centerY + row, centerX + end, centerY + row + 1, pixelColor);
                i = next;
            }
        });
    }

    /** dim: the button can't work right now (it still answers a click, with the reason). */
    private void drawButton(GuiGraphics graphics, int left, int right, Component label, boolean isHovered,
            boolean dim) {
        drawButtonAt(graphics, left, right, this.buttonTop, label, isHovered, dim);
    }

    private void drawButtonAt(GuiGraphics graphics, int left, int right, int top, Component label, boolean isHovered,
            boolean dim) {
        int fill = dim ? (isHovered ? COLOR_WEDGE_LOCKED_HOVER : COLOR_WEDGE_LOCKED)
                : (isHovered ? COLOR_WEDGE_HOVER : COLOR_WEDGE);
        graphics.fill(left, top, right, top + BUTTON_HEIGHT, fill);
        graphics.drawCenteredString(this.font, label, (left + right) / 2, top + 6,
                dim ? COLOR_TEXT_LOCKED : isHovered ? COLOR_TEXT : COLOR_TEXT_SOFT);
    }

    /** Works out how each name on the current page is drawn (see {@link #nameTop}) and remembers what it was for. */
    private void layoutNames(int count, int labelWidth) {
        boolean roomForTwo = this.outer - this.inner >= TWO_LINE_MIN_RING;
        for (int i = 0; i < count; i++) {
            layoutName(i, this.spots.get(pageStart() + i).name(), labelWidth, roomForTwo);
        }
        this.namesPage = this.page;
        this.namesWidth = labelWidth;
    }

    /**
     * One name in a space this wide. It stays on one line if it fits (or if two lines aren't allowed, shortened with
     * "..."). Otherwise it breaks at the space that leaves the two halves closest in width, as long as the first half
     * fits. If even the first word is too wide, that word is shortened and the rest goes underneath; a name that is one
     * long word simply runs on into the second line. The second line is shortened if needed.
     */
    private void layoutName(int slot, String name, int maxWidth, boolean twoLines) {
        this.nameBottom[slot] = null;
        if (!twoLines || this.font.width(name) <= maxWidth) {
            this.nameTop[slot] = fit(name, maxWidth);
            return;
        }
        int best = -1;
        int bestDifference = Integer.MAX_VALUE;
        for (int i = name.indexOf(' '); i >= 0; i = name.indexOf(' ', i + 1)) {
            String first = name.substring(0, i).trim();
            String second = name.substring(i + 1).trim();
            if (first.isEmpty() || second.isEmpty()) {
                continue;
            }
            int firstWidth = this.font.width(first);
            if (firstWidth > maxWidth) {
                break; // any later space only makes the first half wider
            }
            int difference = Math.abs(firstWidth - this.font.width(second));
            if (difference < bestDifference) {
                bestDifference = difference;
                best = i;
            }
        }
        String first;
        String second;
        if (best >= 0) {
            first = name.substring(0, best).trim();
            second = name.substring(best + 1).trim();
        } else if (name.indexOf(' ') > 0) {
            // The first word alone is too wide. Words stay whole where they can: shorten it, the rest goes underneath.
            int space = name.indexOf(' ');
            first = fit(name.substring(0, space), maxWidth);
            second = name.substring(space + 1).trim();
        } else {
            // One long word: it runs on into the second line.
            first = this.font.plainSubstrByWidth(name, maxWidth);
            second = name.substring(first.length());
        }
        if (first.isEmpty() || second.isEmpty()) {
            this.nameTop[slot] = fit(name, maxWidth);
            return;
        }
        this.nameTop[slot] = first;
        this.nameBottom[slot] = fit(second, maxWidth);
    }

    /** The text, shortened with "..." if it's wider than the space it has. */
    private String fit(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("..."))) + "...";
    }
}
