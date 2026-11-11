import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wheelsim import *
from mock_naming import SPOTS, TEAL, TEAL_FILL, wheel, button, arrow, cross

# Options for three fixes after the owner's 10b-11 test: the HUD under the wheel, a clearer list move, and Undo.
OUT = sys.argv[1] if len(sys.argv) > 1 else "."
W, H, S = 280, 240, 3
ROW, STEP, TOP = 20, 22, 20
VISIBLE = (H - TOP - 48) // STEP


def hud(c):
    """Vanilla HUD at the bottom: hotbar, xp bar, hearts and food."""
    x0 = W // 2 - 91
    c.fill(x0, H - 22, x0 + 182, H, 0xC0303030)
    for i in range(9):
        c.fill(x0 + 1 + i * 20, H - 21, x0 + 19 + i * 20, H - 1, 0x80606060)
        if i in (0, 1, 3, 6):
            c.item(x0 + 3 + i * 20, H - 19, [(120, 120, 130), (150, 105, 60), (90, 220, 215), (200, 60, 60)][[0, 1, 3, 6].index(i)])
    c.fill(x0 - 1, H - 23, x0 + 23, H + 1, 0xFFE0E0E0)
    c.fill(x0 + 1, H - 21, x0 + 21, H - 1, 0xC0303030)
    c.fill(x0, H - 29, x0 + 182, H - 24, 0xFF202020)
    c.fill(x0, H - 29, x0 + 120, H - 24, 0xFF80FF20)
    for i in range(10):
        c.fill(x0 + i * 8, H - 39, x0 + i * 8 + 7, H - 32, 0xFFD02020)
        c.fill(x0 + 182 - 7 - i * 8, H - 39, x0 + 182 - i * 8, H - 32, 0xFFB07030)


def cursor(c, x, y):
    for n in range(9):
        c.fill(x, y + n, x + max(1, n // 2 + 1), y + n + 1, 0xFF000000)
        c.fill(x + 1, y + n, x + max(1, n // 2), y + n + 1, 0xFFFFFFFF)


def list_screen(spots, scroll, hover=None, numbers=False, glow=None, ghost=None, undo=None, hint="", slots=16):
    c = Canvas(W, H, S)
    c.backdrop()
    c.fill(0, 0, W, H, 0xC0000000)
    c.centered("Your spots (%d of %d)" % (len(spots), slots), W // 2, 6, COLOR_TEXT)
    left = 26 if numbers else 12
    for screen_row in range(VISIBLE):
        i = scroll + screen_row
        y = TOP + screen_row * STEP + (ghost or 0)
        if i < 0 or i >= len(spots):
            continue
        name, rgb = spots[i]
        fill = 0x60141418
        if glow == i:
            fill = 0xD8207068
        c.fill(left, y, 262, y + ROW, fill)
        if glow == i:
            c.fill(left, y, 262, y + 1, TEAL)
            c.fill(left, y + ROW - 1, 262, y + ROW, TEAL)
        if numbers:
            label = "%d" % (i + 1)
            c.text(label, 22 - width(label), y + 6, TEAL if glow == i else COLOR_TEXT_SOFT)
        c.item(left + 4, y + 2, rgb)
        c.text(name, left + 26, y + 6, COLOR_TEXT if glow == i else COLOR_TEXT_SOFT)
        button(c, 158, y + 2, 196, "Rename")
        for x, kind in ((200, "up"), (220, "down"), (240, "del")):
            on = hover and hover == (i, kind)
            c.fill(x, y + 2, x + 16, y + 18, TEAL_FILL if on else 0xFF26262C)
            if kind == "del":
                cross(c, x + 4, y + 6)
            else:
                arrow(c, x + 4, y + 8, kind == "up")
    c.fill(266, TOP, 269, TOP + VISIBLE * STEP - 2, 0xFF1A1A1E)
    c.fill(266, TOP + 10, 269, TOP + 70, 0xFF707478)
    c.shrunk(hint, W // 2, H - 42, W - 12, COLOR_TEXT_SOFT)
    if undo:
        button(c, W // 2 - 64, H - 28, W // 2 - 4, "Done", height=20)
        button(c, W // 2 + 4, H - 28, W // 2 + 64, "Undo", hovered=True, height=20)
    else:
        button(c, W // 2 - 30, H - 28, W // 2 + 30, "Done", height=20)
    return c


panels = []

c, w = wheel(click_mode=True, hovered=2)
hud(c)
w.render()
panels.append(("Now: the HUD shows through under the wheel", c))

c, w = wheel(click_mode=True, hovered=2)
w.render()
panels.append(("H1: hide the HUD while the wheel is open", c))

c, w = wheel(click_mode=True, hovered=2)
hud(c)
c.fill(0, H - 44, W, H, 0xE0101014)
w.render()
panels.append(("H2: dark band behind the bottom row", c))

spots = list(SPOTS)
moved = list(spots)
moved.insert(2, moved.pop(3))
c = list_screen(moved, -1, hover=(2, "up"), numbers=True, glow=2, hint="Move Iron Farm up")
cursor(c, 206, TOP + 3 * STEP + 9)
panels.append(("L1: numbers + the moved row lights up", c))

c = list_screen(moved, -1, hover=(2, "up"), numbers=True, glow=2, ghost=-9, hint="Move Iron Farm up")
cursor(c, 206, TOP + 3 * STEP + 9)
panels.append(("L2: ...and rows glide one row (mid-slide)", c))

c = list_screen(spots, 0, numbers=True, undo=True, hint="Changed Iron Farm's picture. Undo puts it back.")
panels.append(("U1: Undo after any change", c))

print(sheet(panels, 3, os.path.join(OUT, "jumper-polish-options.png")))
