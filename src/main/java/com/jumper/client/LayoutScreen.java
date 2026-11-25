package com.jumper.client;

import com.jumper.JumperClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The jump screen's layout settings (step 26), opened from its Layout button: 3 x 3 (eight around the power square),
 * Grid (columns and rows of your choosing) or Wheel. Every change is saved at once in the client config; Done or Esc
 * goes back to the game. A small preview shows the shape, and a note says how the grid fits this window (the jump screen
 * uses the same window). The same file on 1.20.1 and 1.21.1.
 */
public class LayoutScreen extends Screen {
    private static final int CHOICE_WIDTH = 72;
    private static final int CHOICE_GAP = 4;
    private static final int BUTTON_HEIGHT = 20;
    private static final int STEPPER = 20;

    private static final int COLOR_BACK = 0xC0000000;
    private static final int COLOR_BUTTON = 0xC0141418;
    private static final int COLOR_BUTTON_HOVER = 0xD8207068;
    private static final int COLOR_CHOSEN = 0xFF2A4A46;
    private static final int COLOR_CHOSEN_EDGE = 0xFF2FA89A;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_SOFT = 0xFFB8C0C8;
    private static final int COLOR_TEXT_DIM = 0xFF707478;
    private static final int COLOR_CELL = 0xC0141418;
    private static final int COLOR_CELL_MIDDLE = 0xFF1D9E75;

    private static final JumpLayout[] CHOICES = {JumpLayout.SQUARE, JumpLayout.GRID, JumpLayout.WHEEL};
    private static final String[] CHOICE_KEYS = {"screen.jumper.layout.square", "screen.jumper.layout.grid",
            "screen.jumper.layout.wheel"};

    /** What the mouse is on: 0-2 a layout choice, 10/11 columns minus/plus, 12/13 rows minus/plus, 20 Done. */
    private static final int NOTHING = -1;
    private static final int COLUMNS_LESS = 10;
    private static final int COLUMNS_MORE = 11;
    private static final int ROWS_LESS = 12;
    private static final int ROWS_MORE = 13;
    private static final int DONE = 20;

    private JumpLayout layout;
    private int columns;
    private int rows;
    private int hovered = NOTHING;

    public LayoutScreen() {
        super(Component.translatable("screen.jumper.layout.title"));
        this.layout = JumperClientConfig.layout();
        this.columns = JumperClientConfig.gridColumns();
        this.rows = JumperClientConfig.gridRows();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- places (all from the middle of the screen) ----

    private int choiceLeft(int i) {
        int total = CHOICES.length * CHOICE_WIDTH + (CHOICES.length - 1) * CHOICE_GAP;
        return this.width / 2 - total / 2 + i * (CHOICE_WIDTH + CHOICE_GAP);
    }

    private static final int CHOICES_TOP = 30;
    private static final int COLUMNS_TOP = 60;
    private static final int ROWS_TOP = 84;
    private static final int PER_PAGE_Y = 110;
    private static final int PREVIEW_TOP = 124;

    /** Left edge of the minus button; the number sits between it and the plus button. */
    private int stepperLeft() {
        return this.width / 2 + 4;
    }

    private int doneTop() {
        return this.height - 28;
    }

    private boolean grid() {
        return this.layout == JumpLayout.GRID;
    }

    private int targetAt(double mouseX, double mouseY) {
        for (int i = 0; i < CHOICES.length; i++) {
            if (inside(mouseX, mouseY, choiceLeft(i), CHOICES_TOP, CHOICE_WIDTH, BUTTON_HEIGHT)) {
                return i;
            }
        }
        if (grid()) {
            int left = stepperLeft();
            if (inside(mouseX, mouseY, left, COLUMNS_TOP, STEPPER, STEPPER)) {
                return COLUMNS_LESS;
            }
            if (inside(mouseX, mouseY, left + 50, COLUMNS_TOP, STEPPER, STEPPER)) {
                return COLUMNS_MORE;
            }
            if (inside(mouseX, mouseY, left, ROWS_TOP, STEPPER, STEPPER)) {
                return ROWS_LESS;
            }
            if (inside(mouseX, mouseY, left + 50, ROWS_TOP, STEPPER, STEPPER)) {
                return ROWS_MORE;
            }
        }
        if (inside(mouseX, mouseY, this.width / 2 - 40, doneTop(), 80, BUTTON_HEIGHT)) {
            return DONE;
        }
        return NOTHING;
    }

    private static boolean inside(double x, double y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int target = targetAt(mouseX, mouseY);
        if (target == NOTHING) {
            return true;
        }
        if (target == DONE) {
            click();
            onClose();
            return true;
        }
        if (target >= 0 && target < CHOICES.length) {
            this.layout = CHOICES[target];
        } else if (target == COLUMNS_LESS) {
            this.columns = Math.max(JumperClientConfig.MIN_GRID, this.columns - 1);
        } else if (target == COLUMNS_MORE) {
            this.columns = Math.min(JumperClientConfig.MAX_GRID_COLUMNS, this.columns + 1);
        } else if (target == ROWS_LESS) {
            this.rows = Math.max(JumperClientConfig.MIN_GRID, this.rows - 1);
        } else if (target == ROWS_MORE) {
            this.rows = Math.min(JumperClientConfig.MAX_GRID_ROWS, this.rows + 1);
        }
        click();
        JumperClientConfig.setLayout(this.layout, this.columns, this.rows);
        return true;
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No super.render: no blur, just a dark wash over the world, like the other Jumper screens.
        this.hovered = targetAt(mouseX, mouseY);
        graphics.fill(0, 0, this.width, this.height, COLOR_BACK);
        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centerX, 12, COLOR_TEXT);

        for (int i = 0; i < CHOICES.length; i++) {
            int left = choiceLeft(i);
            boolean chosen = CHOICES[i] == this.layout;
            int fill = this.hovered == i ? COLOR_BUTTON_HOVER : chosen ? COLOR_CHOSEN : COLOR_BUTTON;
            graphics.fill(left, CHOICES_TOP, left + CHOICE_WIDTH, CHOICES_TOP + BUTTON_HEIGHT, fill);
            if (chosen) {
                graphics.renderOutline(left, CHOICES_TOP, CHOICE_WIDTH, BUTTON_HEIGHT, COLOR_CHOSEN_EDGE);
            }
            graphics.drawCenteredString(this.font, Component.translatable(CHOICE_KEYS[i]), left + CHOICE_WIDTH / 2,
                    CHOICES_TOP + 6, chosen || this.hovered == i ? COLOR_TEXT : COLOR_TEXT_SOFT);
        }

        drawStepper(graphics, Component.translatable("screen.jumper.layout.columns"), COLUMNS_TOP, this.columns,
                COLUMNS_LESS, COLUMNS_MORE);
        drawStepper(graphics, Component.translatable("screen.jumper.layout.rows"), ROWS_TOP, this.rows, ROWS_LESS,
                ROWS_MORE);

        Component perPage = grid()
                ? Component.translatable("screen.jumper.layout.per_page", this.columns * this.rows)
                : Component.translatable("screen.jumper.layout.per_page_fixed", WheelScreen.PER_PAGE);
        graphics.drawCenteredString(this.font, perPage, centerX, PER_PAGE_Y, COLOR_TEXT_SOFT);

        drawPreview(graphics, centerX);

        boolean doneHovered = this.hovered == DONE;
        graphics.fill(centerX - 40, doneTop(), centerX + 40, doneTop() + BUTTON_HEIGHT,
                doneHovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        graphics.drawCenteredString(this.font, Component.translatable("screen.jumper.layout.done"), centerX,
                doneTop() + 6, doneHovered ? COLOR_TEXT : COLOR_TEXT_SOFT);
    }

    /** A label, then minus, the number and plus. Dimmed (and not clickable) unless the Grid layout is chosen. */
    private void drawStepper(GuiGraphics graphics, Component label, int top, int value, int less, int more) {
        boolean on = grid();
        int left = stepperLeft();
        graphics.drawString(this.font, label, left - 8 - this.font.width(label), top + 6,
                on ? COLOR_TEXT_SOFT : COLOR_TEXT_DIM);
        drawSmallButton(graphics, left, top, "-", on, this.hovered == less);
        graphics.drawCenteredString(this.font, Integer.toString(value), left + 35, top + 6,
                on ? COLOR_TEXT : COLOR_TEXT_DIM);
        drawSmallButton(graphics, left + 50, top, "+", on, this.hovered == more);
    }

    private void drawSmallButton(GuiGraphics graphics, int left, int top, String label, boolean on, boolean isHovered) {
        graphics.fill(left, top, left + STEPPER, top + STEPPER, isHovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON);
        graphics.drawCenteredString(this.font, label, left + STEPPER / 2, top + 6,
                on ? (isHovered ? COLOR_TEXT : COLOR_TEXT_SOFT) : COLOR_TEXT_DIM);
    }

    /**
     * A small picture of the chosen shape between the per-page line and the Done button, with a note under it on how
     * the grid fits this window.
     */
    private void drawPreview(GuiGraphics graphics, int centerX) {
        int noteY = doneTop() - 14;
        int roomTall = noteY - 4 - PREVIEW_TOP;
        int previewColumns;
        int previewRows;
        Component note = null;
        if (grid()) {
            int[] fit = WheelScreen.fitTiles(this.width, this.height, this.columns, this.rows);
            previewColumns = fit[0];
            previewRows = fit[1];
            if (fit[0] < this.columns || fit[1] < this.rows) {
                note = Component.translatable("screen.jumper.layout.fit_fewer", fit[0], fit[1]);
            } else if (fit[3] < 32) {
                note = Component.translatable("screen.jumper.layout.fit_small");
            } else {
                note = Component.translatable("screen.jumper.layout.fit_big");
            }
        } else if (this.layout == JumpLayout.SQUARE) {
            previewColumns = 3;
            previewRows = 3;
        } else {
            drawWheelPreview(graphics, centerX, PREVIEW_TOP + roomTall / 2, Math.min(26, roomTall / 2));
            return;
        }
        int gap = 1;
        int cell = Math.min(12, Math.min((roomTall + gap) / previewRows - gap, (this.width - 40) / previewColumns - gap));
        if (cell >= 3) {
            int wide = previewColumns * (cell + gap) - gap;
            int tall = previewRows * (cell + gap) - gap;
            int left = centerX - wide / 2;
            int top = PREVIEW_TOP + Math.max(0, (roomTall - tall) / 2);
            for (int row = 0; row < previewRows; row++) {
                for (int column = 0; column < previewColumns; column++) {
                    boolean middle = !grid() && row == 1 && column == 1;
                    int x = left + column * (cell + gap);
                    int y = top + row * (cell + gap);
                    graphics.fill(x, y, x + cell, y + cell, COLOR_CELL);
                    if (middle) {
                        graphics.renderOutline(x, y, cell, cell, COLOR_CELL_MIDDLE);
                    }
                }
            }
        }
        if (note != null) {
            graphics.drawCenteredString(this.font, note, centerX, noteY, COLOR_TEXT_DIM);
        }
    }

    /**
     * A small wheel: eight wedges with a 1-pixel gap between them and the middle circle with its ring, drawn as
     * horizontal strips of same-colored pixels.
     */
    private static void drawWheelPreview(GuiGraphics graphics, int centerX, int centerY, int outer) {
        if (outer < 8) {
            return;
        }
        int inner = Math.max(4, Math.round(outer * 0.31F));
        for (int y = -outer; y < outer; y++) {
            int start = -outer;
            int current = 0;
            for (int x = -outer; x <= outer; x++) {
                int color = x == outer ? 0 : wheelPreviewColor(x + 0.5, y + 0.5, inner, outer);
                if (color != current) {
                    if (current != 0) {
                        graphics.fill(centerX + start, centerY + y, centerX + x, centerY + y + 1, current);
                    }
                    start = x;
                    current = color;
                }
            }
        }
    }

    /** The preview's color at a point measured from its middle, or 0 for nothing. */
    private static int wheelPreviewColor(double dx, double dy, int inner, int outer) {
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < inner - 1) {
            return distance >= inner - 3 ? COLOR_CELL_MIDDLE : COLOR_CELL;
        }
        if (distance < inner || distance >= outer) {
            return 0;
        }
        double turn = (Math.atan2(dy, dx) + Math.PI / 2.0) / (2.0 * Math.PI);
        double part = (turn - Math.floor(turn)) * 8 + 0.5;
        part -= Math.floor(part);
        double toEdge = Math.min(part, 1.0 - part) * (2.0 * Math.PI / 8) * distance;
        return toEdge < 0.5 ? 0 : COLOR_CELL;
    }
}
