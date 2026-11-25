package com.jumper.client;

import com.jumper.JumperClientConfig;
import com.jumper.net.EditPayload;
import com.jumper.net.JumperNetwork;
import com.jumper.net.WheelActionPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * The jump screen, in the layout the player picked ({@link JumpLayout}, from the Layout button, top right):
 * <ul>
 * <li>SQUARE (default, step 25): eight squares around a middle square, in the same places and order the ring's wedges
 * have (the first straight up, the rest clockwise), so a spot keeps its direction. The middle square shows the power.
 * <li>GRID (step 26): a plain grid, as many columns and rows as the player picked (fewer if the window is too small);
 * no names on the squares (the hint line names the one under the mouse); the power is on the top line.
 * <li>WHEEL: the ring of wedges, one per spot, with the power in its middle circle.
 * </ul>
 * Hold the key, move the mouse onto a spot, let go: you jump there. Scroll (or the left / right arrow keys) flips to the next page. The inventory key closes it, like Esc. Buttons sit in the bottom
 * corners: "Save here" on the left (while a slot is free); "Home" (your anchor) and "Back" (once there's somewhere
 * to jump back to) on the right. Under them, the bottom row is the hint line (what letting go will do, or why not); the
 * ring is sized so it never reaches the buttons or the hint, at any window size (scripts/fit_check.py checks this).
 * Letting go on the middle circle, or on nothing, closes the wheel without doing anything.
 *
 * A name too long for its square (or wedge) goes on two lines (broken at a space where it can), when the ring is thick enough.
 *
 * Buttons and the ring's wedges are chosen only while the mouse is on their drawn shape (the same test draws and picks
 * them). In the grid, the thin gaps between squares count as the nearest square, so the mouse never falls between two.
 *
 * A quick tap of the key (released before the mouse moved) leaves the wheel open, so it also works by clicking.
 *
 * Nothing here is trusted by the server; a pick is only a request.
 */
public class WheelScreen extends WheelScreenBase {
    /** Spots shown at once in the SQUARE and WHEEL layouts. */
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
    /** Top of the GRID layout's area (under the top row of buttons and the power line). */
    private static final int TILES_TOP = 32;
    /** Square sizes tried for the GRID layout, biggest first, with the picture size each holds. */
    private static final int[][] TILE_SIZES = {{40, 32}, {32, 24}, {22, 16}};
    /** Pixels between two squares of the grid. */
    private static final int GRID_GAP = 2;
    /** Largest square, so pictures don't float in big empty squares on large windows. */
    private static final int MAX_CELL = 76;
    /** Smallest grid (half its width), for windows too small to keep everything apart. */
    private static final int MIN_GRID_HALF = 36;
    /**
     * Which square (column, row) each spot of a page sits in, in the ring's order: straight up first, then clockwise.
     * The middle square (1, 1) is the power display.
     */
    private static final int[][] GRID_CELLS = {{1, 0}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {0, 1}, {0, 0}};
    /** An empty square (a page with fewer than eight spots). */
    private static final int COLOR_CELL_EMPTY = 0x40141418;

    /** What the mouse can be on, besides a wedge (0 and up, counted on the current page). */
    private static final int NOTHING = -1;
    private static final int HUB = -2;
    private static final int SAVE = -3;
    private static final int BACK = -4;
    private static final int HOME = -5;
    private static final int EDIT = -6;
    private static final int LAYOUT = -7;

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
    private final JumpLayout layout;
    /** Spots per page and number of pages: fixed for SQUARE and WHEEL, worked out per window size for GRID. */
    private int perPage = PER_PAGE;
    private int pages = 1;
    private int page;

    /** Radius of the hole in the middle and of the ring's outer edge, in screen pixels. */
    private int inner;
    private int outer;
    /** The grid: size of one square, its whole width, its top-left corner, and the picture size in it. */
    private int cell;
    private int gridSize;
    private int gridLeft;
    private int gridTop;
    private int pictureSize = 16;
    /** The GRID layout as fitted to the window: columns, rows, square size, picture size, top-left corner. */
    private int tileColumns;
    private int tileRows;
    private int tileCell;
    private int tilePicture;
    private int tileLeft;
    private int tileTop;
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
    private int layoutLeft;
    private int layoutRight;
    private Component layoutLabel = Component.empty();

    /** Ticks between power refreshes while the wheel is open. */
    private static final int STATUS_REFRESH_TICKS = 20;

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
        this.layout = JumperClientConfig.layout();
    }

    private boolean square() {
        return this.layout == JumpLayout.SQUARE;
    }

    private boolean tiles() {
        return this.layout == JumpLayout.GRID;
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
        // Top right: the layout settings.
        this.layoutLabel = Component.translatable("screen.jumper.wheel.layout");
        this.layoutRight = this.width - BUTTON_MARGIN;
        this.layoutLeft = this.layoutRight - this.font.width(this.layoutLabel) - 16;

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
        room = Math.min(room, distanceTo(centerX, centerY, this.layoutLeft, BUTTON_MARGIN, this.layoutRight,
                BUTTON_MARGIN + BUTTON_HEIGHT) - 2);
        int preferred = Math.max(70, Math.min(230, Math.round(Math.min(this.width, this.height) * 0.42F)));
        this.outer = Math.max(MIN_OUTER, Math.min(preferred, room));
        // The middle circle is big enough for three short lines of text inside the power ring.
        this.inner = Math.max(30, Math.round(this.outer * 0.31F));
        this.stripsByCount = new int[PER_PAGE + 1][];
        this.namesPage = -1;
        this.ringPixels = buildRing(this.inner - 2 - RING_THICKNESS, this.inner - 2);
        layoutGrid(centerX, centerY);
        layoutTiles(centerY);

        // Pages (init runs again when the window is resized: stay on the page holding the first spot shown before).
        int firstShown = this.page * this.perPage;
        this.perPage = tiles() ? this.tileColumns * this.tileRows : PER_PAGE;
        this.pages = Math.max(1, (this.spots.size() + this.perPage - 1) / this.perPage);
        this.page = Math.min(this.pages - 1, firstShown / this.perPage);
        this.namesPage = -1;
    }

    /** Fits the GRID layout to the window: see {@link #fitTiles}. */
    private void layoutTiles(int centerY) {
        int[] fit = fitTiles(this.width, this.height, JumperClientConfig.gridColumns(), JumperClientConfig.gridRows());
        this.tileColumns = fit[0];
        this.tileRows = fit[1];
        this.tileCell = fit[2];
        this.tilePicture = fit[3];
        int wide = this.tileColumns * this.tileCell + (this.tileColumns - 1) * GRID_GAP;
        int tall = this.tileRows * this.tileCell + (this.tileRows - 1) * GRID_GAP;
        this.tileLeft = (this.width - wide) / 2;
        int bottom = tilesBottom(this.height);
        this.tileTop = Math.max(TILES_TOP, Math.min(centerY - tall / 2, bottom - tall));
    }

    /** Lowest the GRID layout may reach: 3 pixels above the bottom buttons. */
    private static int tilesBottom(int height) {
        return height - HINT_MARGIN - 8 - 4 - BUTTON_HEIGHT - 3;
    }

    /**
     * How the GRID layout fits a window this big (GUI pixels) with this many columns and rows wanted: {columns, rows,
     * square size, picture size}. It takes the biggest squares that fit; if even the smallest don't, it shows fewer
     * columns or rows. Used by the jump screen and by the layout screen's note.
     */
    static int[] fitTiles(int width, int height, int columns, int rows) {
        int roomWide = width - 2 * BUTTON_MARGIN;
        int roomTall = tilesBottom(height) - TILES_TOP;
        for (int[] size : TILE_SIZES) {
            if (columns * size[0] + (columns - 1) * GRID_GAP <= roomWide
                    && rows * size[0] + (rows - 1) * GRID_GAP <= roomTall) {
                return new int[]{columns, rows, size[0], size[1]};
            }
        }
        int[] smallest = TILE_SIZES[TILE_SIZES.length - 1];
        int step = smallest[0] + GRID_GAP;
        return new int[]{Math.max(1, Math.min(columns, (roomWide + GRID_GAP) / step)),
                Math.max(1, Math.min(rows, (roomTall + GRID_GAP) / step)), smallest[0], smallest[1]};
    }

    /**
     * Sizes the grid: as big as fits (up to {@link #MAX_CELL} squares) without reaching the hint line, a button (their
     * places stay reserved even when not shown) or the page line above it. Pictures are 32 pixels when the squares
     * have room for them and a name under them, else 24, else the normal 16.
     */
    private void layoutGrid(int centerX, int centerY) {
        int[][] keepClear = {
                {this.saveLeft, this.buttonTop, this.saveRight, this.buttonTop + BUTTON_HEIGHT},
                {this.homeLeft, this.buttonTop, this.backRight, this.buttonTop + BUTTON_HEIGHT},
                {BUTTON_MARGIN, BUTTON_MARGIN, this.editRight, BUTTON_MARGIN + BUTTON_HEIGHT},
                {this.layoutLeft, BUTTON_MARGIN, this.layoutRight, BUTTON_MARGIN + BUTTON_HEIGHT}};
        int half = Math.min(Math.round(Math.min(this.width, this.height) * 0.40F), (3 * MAX_CELL + 2 * GRID_GAP) / 2);
        while (half > MIN_GRID_HALF && !gridFits(centerX, centerY, half, keepClear)) {
            half--;
        }
        half = Math.max(MIN_GRID_HALF, half);
        this.cell = (2 * half - 2 * GRID_GAP) / 3;
        this.gridSize = 3 * this.cell + 2 * GRID_GAP;
        this.gridLeft = centerX - this.gridSize / 2;
        this.gridTop = centerY - this.gridSize / 2;
        this.pictureSize = this.cell >= 46 ? 32 : this.cell >= 38 ? 24 : 16;
    }

    /** True if a grid this big (half its width) around the middle keeps 2 pixels clear of everything else. */
    private boolean gridFits(int centerX, int centerY, int half, int[][] keepClear) {
        // The hint line below, and the page line above (drawn 14 pixels over the grid).
        if (centerY + half > this.hintTop - 3 || centerY - half - 14 < 4) {
            return false;
        }
        for (int[] box : keepClear) {
            if (centerX - half - 2 < box[2] && centerX + half + 2 > box[0] && centerY - half - 2 < box[3]
                    && centerY + half + 2 > box[1]) {
                return false;
            }
        }
        return true;
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
        return this.page * this.perPage;
    }

    /** How many wedges the current page has. */
    private int pageCount() {
        return Math.max(0, Math.min(this.perPage, this.spots.size() - pageStart()));
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
        if (mouseY >= BUTTON_MARGIN && mouseY < BUTTON_MARGIN + BUTTON_HEIGHT && mouseX >= this.layoutLeft
                && mouseX < this.layoutRight) {
            return LAYOUT;
        }
        if (square()) {
            return gridAt(mouseX, mouseY, pageCount());
        }
        if (tiles()) {
            return tilesAt(mouseX, mouseY, pageCount());
        }
        return ringAt(mouseX - this.width / 2.0, mouseY - this.height / 2.0, pageCount());
    }

    private static boolean pickable(int target) {
        return target >= 0 || target == SAVE || target == BACK || target == HOME || target == EDIT || target == LAYOUT;
    }

    /**
     * What part of the grid is at a point on screen: a spot number on this page, {@link #HUB} for the middle square, or
     * {@link #NOTHING} (outside the grid, or a square with no spot). A gap counts as the nearer square.
     */
    private int gridAt(double mouseX, double mouseY, int count) {
        if (mouseX < this.gridLeft || mouseX >= this.gridLeft + this.gridSize || mouseY < this.gridTop
                || mouseY >= this.gridTop + this.gridSize) {
            return NOTHING;
        }
        int step = this.cell + GRID_GAP;
        int column = Math.min(2, (int) Math.floor((mouseX - this.gridLeft + GRID_GAP / 2.0) / step));
        int row = Math.min(2, (int) Math.floor((mouseY - this.gridTop + GRID_GAP / 2.0) / step));
        if (column == 1 && row == 1) {
            return HUB;
        }
        for (int i = 0; i < count; i++) {
            if (GRID_CELLS[i][0] == column && GRID_CELLS[i][1] == row) {
                return i;
            }
        }
        return NOTHING;
    }

    /** What part of the GRID layout is at a point on screen: a spot number on this page, or {@link #NOTHING}. */
    private int tilesAt(double mouseX, double mouseY, int count) {
        int step = this.tileCell + GRID_GAP;
        int wide = this.tileColumns * step - GRID_GAP;
        int tall = this.tileRows * step - GRID_GAP;
        if (mouseX < this.tileLeft || mouseX >= this.tileLeft + wide || mouseY < this.tileTop
                || mouseY >= this.tileTop + tall) {
            return NOTHING;
        }
        int column = Math.min(this.tileColumns - 1, (int) Math.floor((mouseX - this.tileLeft + GRID_GAP / 2.0) / step));
        int row = Math.min(this.tileRows - 1, (int) Math.floor((mouseY - this.tileTop + GRID_GAP / 2.0) / step));
        int index = row * this.tileColumns + column;
        return index < count ? index : NOTHING;
    }

    private int cellLeft(int column) {
        return this.gridLeft + column * (this.cell + GRID_GAP);
    }

    private int cellTop(int row) {
        return this.gridTop + row * (this.cell + GRID_GAP);
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
        // Fresh power numbers once a second while open (the server answers at most every 10 ticks).
        if (this.ticksOpen % STATUS_REFRESH_TICKS == 0) {
            JumperNetwork.sendToServer(WheelActionPayload.status());
        }
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
            Picture picture = ClientEdits.suggestedPicture();
            List<ClientState.Entry> current = ClientState.spots();
            Minecraft.getInstance().setScreen(new NameScreen(Component.translatable("screen.jumper.name.new"), picture,
                    ClientEdits.suggestedName(picture, current), current, -1,
                    name -> ClientEdits.send(EditPayload.SAVE, 0, "", name), null));
            return;
        } else if (target == LAYOUT) {
            Minecraft.getInstance().setScreen(new LayoutScreen());
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
        if (square()) {
            renderGrid(graphics, count, hoveredNow);
            drawHub(graphics, cellLeft(1) + this.cell / 2, cellTop(1) + this.cell / 2);
        } else if (tiles()) {
            renderTiles(graphics, count, hoveredNow);
            drawTopLine(graphics, centerX);
        } else {
            renderRing(graphics, centerX, centerY, count, hoveredNow);
            drawHub(graphics, centerX, centerY);
        }
        if (this.pages > 1 && !tiles()) {
            // Above the squares (or ring), so the middle has room for the power. The plain grid has it on its top line.
            graphics.drawCenteredString(this.font, Component.translatable("screen.jumper.wheel.page", this.page + 1,
                    this.pages), centerX, Math.max(4, (square() ? this.gridTop : centerY - this.outer) - 14), COLOR_TEXT);
        }

        if (!this.spots.isEmpty()) {
            drawButtonAt(graphics, BUTTON_MARGIN, this.editRight, BUTTON_MARGIN, this.editLabel, hoveredNow == EDIT,
                    false);
        }

        drawButtonAt(graphics, this.layoutLeft, this.layoutRight, BUTTON_MARGIN, this.layoutLabel,
                hoveredNow == LAYOUT, false);

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

    /**
     * The grid: eight squares around the middle one. Each spot's square has its picture with the name under it, both
     * centered in the square; squares with no spot on this page are drawn faint.
     */
    private void renderGrid(GuiGraphics graphics, int count, int hoveredNow) {
        for (int i = 0; i < GRID_CELLS.length; i++) {
            int left = cellLeft(GRID_CELLS[i][0]);
            int top = cellTop(GRID_CELLS[i][1]);
            int color;
            if (i >= count) {
                color = COLOR_CELL_EMPTY;
            } else if (locked(pageStart() + i)) {
                color = i == hoveredNow ? COLOR_WEDGE_LOCKED_HOVER : COLOR_WEDGE_LOCKED;
            } else {
                color = i == hoveredNow ? COLOR_WEDGE_HOVER : COLOR_WEDGE;
            }
            graphics.fill(left, top, left + this.cell, top + this.cell, color);
        }
        graphics.fill(cellLeft(1), cellTop(1), cellLeft(1) + this.cell, cellTop(1) + this.cell, COLOR_HUB);

        int labelWidth = this.cell - 6;
        if (this.namesPage != this.page || this.namesWidth != labelWidth) {
            layoutNames(count, labelWidth, this.pictureSize + 2 + 18 <= this.cell);
        }
        int size = this.pictureSize;
        for (int i = 0; i < count; i++) {
            ClientState.Entry spot = this.spots.get(pageStart() + i);
            int x = cellLeft(GRID_CELLS[i][0]) + this.cell / 2;
            boolean twoLines = this.nameBottom[i] != null;
            // Picture, 2 pixels of air, then one or two lines of name: the whole stack centered in the square.
            int stack = size + 2 + (twoLines ? 18 : 8);
            int pictureTop = cellTop(GRID_CELLS[i][1]) + (this.cell - stack) / 2;
            int pictureLeft = x - size / 2;
            spot.icon().draw(graphics, pictureLeft, pictureTop, size);
            boolean isLocked = locked(pageStart() + i);
            if (isLocked) {
                // A dark veil over the picture (drawn above items, which sit higher than flat fills).
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                graphics.fill(pictureLeft, pictureTop, pictureLeft + size, pictureTop + size, 0xA0000000);
                graphics.pose().popPose();
            }
            int nameColor = isLocked ? COLOR_TEXT_LOCKED : i == hoveredNow ? COLOR_TEXT : COLOR_TEXT_SOFT;
            int nameTopY = pictureTop + size + 2;
            graphics.drawCenteredString(this.font, this.nameTop[i], x, nameTopY, nameColor);
            if (twoLines) {
                graphics.drawCenteredString(this.font, this.nameBottom[i], x, nameTopY + 10, nameColor);
            }
        }
    }

    /** The GRID layout: one square per spot, with its picture; squares with no spot on this page are drawn faint. */
    private void renderTiles(GuiGraphics graphics, int count, int hoveredNow) {
        int size = this.tilePicture;
        for (int i = 0; i < this.tileColumns * this.tileRows; i++) {
            int left = this.tileLeft + (i % this.tileColumns) * (this.tileCell + GRID_GAP);
            int top = this.tileTop + (i / this.tileColumns) * (this.tileCell + GRID_GAP);
            if (i >= count) {
                graphics.fill(left, top, left + this.tileCell, top + this.tileCell, COLOR_CELL_EMPTY);
                continue;
            }
            boolean isLocked = locked(pageStart() + i);
            int color = isLocked ? (i == hoveredNow ? COLOR_WEDGE_LOCKED_HOVER : COLOR_WEDGE_LOCKED)
                    : (i == hoveredNow ? COLOR_WEDGE_HOVER : COLOR_WEDGE);
            graphics.fill(left, top, left + this.tileCell, top + this.tileCell, color);
            int pictureLeft = left + (this.tileCell - size) / 2;
            int pictureTop = top + (this.tileCell - size) / 2;
            this.spots.get(pageStart() + i).icon().draw(graphics, pictureLeft, pictureTop, size);
            if (isLocked) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, 0.0F, 200.0F);
                graphics.fill(pictureLeft, pictureTop, pictureLeft + size, pictureTop + size, 0xA0000000);
                graphics.pose().popPose();
            }
        }
    }

    /**
     * The GRID layout's top line, between Edit spots and Layout: the power (as the middle circle would say it, on one
     * line, with a thin bar under it when the anchor needs power), slots used, and the page.
     */
    private void drawTopLine(GuiGraphics graphics, int centerX) {
        int power = ClientState.power();
        boolean needsPower = power != ClientState.POWER_FREE;
        boolean unloaded = power == ClientState.POWER_NOT_LOADED || power == ClientState.POWER_BOTH;
        boolean empty = power == ClientState.POWER_NONE || power == ClientState.POWER_BOTH;
        long energy = ClientState.energyNow();
        long capacity = ClientState.capacity();
        int charge = capacity <= 0 ? 0 : (int) Math.max(0L, Math.min(1000L, (long) (energy * 1000.0 / capacity)));
        MutableComponent line = Component.empty();
        if (outOfRange()) {
            line.append(colored(Component.translatable("screen.jumper.wheel.out_of_range"), COLOR_NO_POWER));
        } else if (unloaded) {
            line.append(colored(Component.translatable("screen.jumper.wheel.unloaded"), COLOR_NOT_LOADED));
            line.append(colored(Component.literal("  "), COLOR_TEXT_SOFT));
            line.append(empty ? colored(Component.translatable("screen.jumper.wheel.no_power"), COLOR_NO_POWER)
                    : colored(Component.literal((charge / 10) + "%"), COLOR_TEXT_SOFT));
        } else if (empty) {
            line.append(colored(Component.translatable("screen.jumper.wheel.no_power"), COLOR_NO_POWER));
        } else if (needsPower) {
            long rate = ClientState.rate();
            line.append(colored(Component.literal((charge / 10) + "%"), COLOR_TEXT));
            if (rate > 0) {
                line.append(colored(Component.literal("  " + duration(energy / rate)), COLOR_TEXT_SOFT));
            }
        }
        if (!line.getSiblings().isEmpty()) {
            line.append(colored(Component.literal("   "), COLOR_TEXT_SOFT));
        }
        line.append(colored(Component.literal(this.spots.size() + " / " + this.slots), COLOR_TEXT_SOFT));
        if (this.pages > 1) {
            line.append(colored(Component.literal("   "), COLOR_TEXT_SOFT));
            line.append(colored(Component.translatable("screen.jumper.wheel.page", this.page + 1, this.pages),
                    COLOR_TEXT));
        }
        int room = this.layoutLeft - this.editRight - 8;
        drawShrunk(graphics, line, centerX, BUTTON_MARGIN + 3, room, COLOR_TEXT);
        if (needsPower) {
            int barWidth = Math.max(10, Math.min(80, room));
            int left = centerX - barWidth / 2;
            int top = BUTTON_MARGIN + 14;
            int color = empty ? COLOR_NO_POWER : unloaded ? COLOR_NOT_LOADED : COLOR_POWER;
            int filled = empty ? barWidth : Math.round(barWidth * charge / 1000.0F);
            graphics.fill(left, top, left + barWidth, top + 2, COLOR_POWER_EMPTY);
            graphics.fill(left, top, left + filled, top + 2, color);
        }
    }

    private static MutableComponent colored(MutableComponent text, int argb) {
        return text.withStyle(style -> style.withColor(TextColor.fromRgb(argb & 0xFFFFFF)));
    }

    /** The old ring: one wedge per spot, each with its picture and name. */
    private void renderRing(GuiGraphics graphics, int centerX, int centerY, int count, int hoveredNow) {
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
            layoutNames(count, labelWidth, this.outer - this.inner >= TWO_LINE_MIN_RING);
        }
        for (int i = 0; i < count; i++) {
            ClientState.Entry spot = this.spots.get(pageStart() + i);
            double angle = -Math.PI / 2.0 + i * (2.0 * Math.PI / count);
            int x = centerX + (int) Math.round(Math.cos(angle) * middle);
            int y = centerY + (int) Math.round(Math.sin(angle) * middle);
            // With the name on two lines the picture moves up, so picture and name stay centered on the same point.
            boolean twoLines = this.nameBottom[i] != null;
            int pictureTop = twoLines ? y - 18 : y - 13;
            spot.icon().draw(graphics, x - 8, pictureTop);
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
    }

    /** The line under the grid (or ring). */
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
        if (hoveredNow == LAYOUT) {
            return Component.translatable("screen.jumper.wheel.layout_hint");
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
            int filled = empty ? 1000 : charge;
            int color = empty ? COLOR_NO_POWER : unloaded ? COLOR_NOT_LOADED : COLOR_POWER;
            if (square()) {
                drawSquareRing(graphics, cellLeft(1), cellTop(1), this.cell, filled, color);
            } else {
                drawRing(graphics, centerX, centerY, filled, color);
            }
        }

        // Text stays inside the ring (or the circle's edge when there is no ring), with a little air. In the grid,
        // textRadius is half the width a line may use inside the middle square.
        int textRadius = square() ? (this.cell - 2 * (needsPower ? RING_THICKNESS : 0) - 6) / 2
                : this.inner - 2 - (needsPower ? RING_THICKNESS : 0) - 3;
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
        if (square()) {
            // The middle square is as wide at every line.
            drawShrunk(graphics, text, centerX, y, 2 * radius, color);
            return;
        }
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

    /**
     * The power ring of the grid's middle square: a band this thick just inside its edge, filled clockwise from the
     * middle of the top edge up to filled (thousandths), the rest faint.
     */
    private static void drawSquareRing(GuiGraphics graphics, int left, int top, int size, int filled, int color) {
        int t = RING_THICKNESS;
        int right = left + size;
        int bottom = top + size;
        int middle = left + size / 2;
        // The five stretches in the order the charge fills them: top right half, right side, bottom, left side, top
        // left half. Each is {left, top, right, bottom, direction}: 0 left to right, 1 top to bottom, 2 right to left,
        // 3 bottom to top. They don't overlap; each corner belongs to one stretch.
        int[][] stretches = {
                {middle, top, right, top + t, 0},
                {right - t, top + t, right, bottom, 1},
                {left, bottom - t, right - t, bottom, 2},
                {left, top, left + t, bottom - t, 3},
                {left + t, top, middle, top + t, 0}};
        int total = 0;
        for (int[] s : stretches) {
            total += s[4] % 2 == 0 ? s[2] - s[0] : s[3] - s[1];
        }
        int remaining = Math.round(total * Math.max(0, Math.min(1000, filled)) / 1000.0F);
        for (int[] s : stretches) {
            int length = s[4] % 2 == 0 ? s[2] - s[0] : s[3] - s[1];
            int on = Math.max(0, Math.min(length, remaining));
            remaining -= on;
            graphics.fill(s[0], s[1], s[2], s[3], COLOR_POWER_EMPTY);
            if (on == 0) {
                continue;
            }
            switch (s[4]) {
                case 0 -> graphics.fill(s[0], s[1], s[0] + on, s[3], color);
                case 1 -> graphics.fill(s[0], s[1], s[2], s[1] + on, color);
                case 2 -> graphics.fill(s[2] - on, s[1], s[2], s[3], color);
                default -> graphics.fill(s[0], s[3] - on, s[2], s[3], color);
            }
        }
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
    private void layoutNames(int count, int labelWidth, boolean roomForTwo) {
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
