import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wheelsim import *
from mock_naming import SPOTS, TEAL, TEAL_FILL, box, button, arrow, cross, wheel

# The spot list screen (B3) as it will be built, at the owner's window size. Pictures go to the folder given first.
OUT = sys.argv[1] if len(sys.argv) > 1 else "."
W, H, S = 280, 240, 3
ROW, STEP, TOP = 20, 22, 20
VISIBLE = (H - TOP - 48) // STEP


def cursor(c, x, y):
    for n in range(9):
        c.fill(x, y + n, x + max(1, n // 2 + 1), y + n + 1, 0xFF000000)
        c.fill(x + 1, y + n, x + max(1, n // 2), y + n + 1, 0xFFFFFFFF)


def list_screen(spots, scroll, hover=None, confirm=None, hint="", slots=16):
    c = Canvas(W, H, S)
    c.backdrop()
    c.fill(0, 0, W, H, 0xC0000000)
    c.centered("Your spots (%d of %d)" % (len(spots), slots), W // 2, 6, COLOR_TEXT)
    for screen_row in range(VISIBLE):
        i = scroll + screen_row
        y = TOP + screen_row * STEP
        if i < 0 or i >= len(spots):
            continue
        name, rgb = spots[i]
        red = confirm == i
        c.fill(12, y, 262, y + ROW, 0xB0502828 if red else (TEAL_FILL if hover and hover[0] == i else 0x60141418))
        c.item(16, y + 2, rgb)
        if red:
            c.text("Delete %s?" % name, 38, y + 6, COLOR_TEXT)
        else:
            c.text(name, 38, y + 6, COLOR_TEXT if hover and hover[0] == i else COLOR_TEXT_SOFT)
            button(c, 158, y + 2, 196, "Rename", hovered=bool(hover and hover == (i, "rename")))
        for x, kind in ((200, "up"), (220, "down"), (240, "del")):
            on = hover and hover == (i, kind)
            c.fill(x, y + 2, x + 16, y + 18, TEAL_FILL if on else 0xFF26262C)
            if kind == "del":
                cross(c, x + 4, y + 6)
            else:
                arrow(c, x + 4, y + 8, kind == "up")
        if i == 7 and slots > 8:   # the first 8 still work with no power or an unloaded base
            c.fill(12, y + ROW, 262, y + ROW + 1, TEAL)
    total = max(len(spots), 1)
    first = max(0, scroll)
    c.fill(266, TOP, 269, TOP + VISIBLE * STEP - 2, 0xFF1A1A1E)
    bar = (VISIBLE * STEP - 2) * min(1.0, VISIBLE / total)
    top = TOP + (VISIBLE * STEP - 2 - bar) * (first / max(1, total - VISIBLE))
    c.fill(266, int(top), 269, int(top + bar), 0xFF707478)
    c.shrunk(hint, W // 2, H - 42, W - 12, COLOR_TEXT_SOFT)
    button(c, W // 2 - 30, H - 28, W // 2 + 30, "Done", height=20)
    return c


panels = []
row_y = lambda screen_row: TOP + screen_row * STEP + 9
spots = list(SPOTS)

c = list_screen(spots, 0, hover=(3, "up"), hint="Move Iron Farm up")
cursor(c, 206, row_y(3))
panels.append(("1. Mouse on Iron Farm's up arrow", c))

moved = list(spots)
moved.insert(1, moved.pop(3))
c = list_screen(moved, -2, hover=(1, "up"), hint="Move Iron Farm up")
cursor(c, 206, row_y(3))
panels.append(("2. Two clicks later: Iron Farm is 2nd, still under the mouse", c))

c = list_screen(spots, 2, hover=(5, "del"), confirm=5, hint="Click the X again to delete Mob Grinder")
cursor(c, 246, row_y(3))
panels.append(("3. Delete asks once (scrolled down a bit)", c))

c = list_screen(spots, 0, hover=(1, "pic"), hint="Click the picture to use the item in your hand")
cursor(c, 22, row_y(1))
panels.append(("4. Picture: click it to use what you're holding", c))

c, w = wheel(click_mode=True)
w.render()
button(c, 8, 8, 8 + width("Edit spots") + 16, "Edit spots", hovered=True, height=20)
panels.append(("5. The wheel gets an \"Edit spots\" button (top left)", c))

print(sheet(panels, 3, os.path.join(OUT, "jumper-list-screen.png")))
