import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wheelsim import *

# Pictures go to the folder given as the first argument (default: the current folder).
OUT = sys.argv[1] if len(sys.argv) > 1 else "."

W, H, S = 280, 240, 3
SPOTS = [("Furnace", (120, 120, 120)), ("Crafting Table", (150, 105, 60)), ("Storage Room", (160, 120, 70)),
         ("Iron Farm", (200, 200, 205)), ("Enchanting Table", (60, 40, 90)), ("Mob Grinder", (90, 110, 90)),
         ("Quarry", (130, 130, 110)), ("Nether Portal", (110, 50, 160)),
         ("Wheat Farm", (200, 180, 80)), ("Bedroom", (170, 60, 60))]
CAP = 240 * 72000
TEAL, TEAL_FILL = 0xFF2FA89A, 0xD8207068
PANEL, BORDER = 0xF4101014, 0xFF3A3A44


def wheel(**kw):
    c = Canvas(W, H, S)
    c.backdrop()
    args = dict(power=POWER_ON, energy=int(CAP * 0.62), capacity=CAP, rate=240)
    args.update(kw)
    w = Wheel(c, SPOTS, 16, **args)
    return c, w


def box(c, x0, y0, x1, y1, fill=PANEL, border=BORDER):
    c.fill(x0, y0, x1, y1, border)
    c.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill)


def button(c, x0, y0, x1, label, hovered=False, color=None, height=16):
    c.fill(x0, y0, x1, y0 + height, TEAL_FILL if hovered else 0xFF26262C)
    c.centered(label, (x0 + x1) // 2, y0 + (height - 8) // 2, color or (COLOR_TEXT if hovered else COLOR_TEXT_SOFT))


def edit_box(c, x0, y0, x1, text, selected=True, cursor=False):
    c.fill(x0, y0, x1, y0 + 16, 0xFFFFFFFF)          # a focused text box has a white edge
    c.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 15, 0xFF000000)
    if selected:
        c.fill(x0 + 3, y0 + 3, x0 + 5 + width(text), y0 + 13, 0xFF2A4FD0)
    c.text(text, x0 + 4, y0 + 4, COLOR_TEXT)
    if cursor:
        c.fill(x0 + 4 + width(text), y0 + 3, x0 + 5 + width(text), y0 + 13, 0xFFFFFFFF)


def arrow(c, x, y, up, color=0xFFB8C0C8):
    rows = [(3, 1), (2, 3), (1, 5), (0, 7)]
    for n, (off, w) in enumerate(rows if up else rows[::-1]):
        c.fill(x + off, y + n, x + off + w, y + n + 1, color)


def cross(c, x, y, color=0xFFE24B4A):
    for i in range(7):
        c.fill(x + i, y + i, x + i + 1, y + i + 1, color)
        c.fill(x + 6 - i, y + i, x + 7 - i, y + i + 1, color)


def name_panel(c, title, name, rgb, note):
    c.fill(0, 0, W, H, 0x90000000)
    x0, y0, x1, y1 = 44, 78, 236, 162
    box(c, x0, y0, x1, y1, border=TEAL)
    c.centered(title, W // 2, y0 + 8, COLOR_TEXT)
    c.item(x0 + 10, y0 + 24, rgb)
    edit_box(c, x0 + 32, y0 + 24, x1 - 10, name)
    button(c, W // 2 - 62, y0 + 48, W // 2 - 4, "Save", hovered=True)
    button(c, W // 2 + 4, y0 + 48, W // 2 + 62, "Cancel")
    c.shrunk(note, W // 2, y0 + 70, x1 - x0 - 12, COLOR_TEXT_LOCKED)


panels_a, panels_b = [], []

# ---- A1: a name box every time you save
c, w = wheel()
w.render()
name_panel(c, "Name this spot", "Grass Block", (96, 150, 70), "Enter: save     Esc: cancel")
panels_a.append(("A1  Name box every time you save (suggested name filled in)", c))

# ---- A2: quick save stays; right-click to type a name
c, w = wheel(hovered=SAVE, click_mode=True)
w.hint = lambda: 'Click: save as "Grass Block".  Right-click: type a name first.'
w.render()
panels_a.append(("A2  Quick save as now; right-click \"+ Save here\" to type a name", c))

# ---- B1: right-click a spot for a small menu
c, w = wheel(hovered=1, click_mode=True)
w.hint = lambda: "Crafting Table: click to jump, right-click to edit"
w.render()
mx, my = 176, 70
items = [("Rename", COLOR_TEXT), ("Change picture", COLOR_TEXT_SOFT), ("Move earlier", COLOR_TEXT_SOFT),
         ("Move later", COLOR_TEXT_SOFT), ("Delete", COLOR_NO_POWER)]
mw = max(width(t) for t, _ in items) + 14
box(c, mx, my, mx + mw, my + len(items) * 13 + 5, border=TEAL)
for n, (text, color) in enumerate(items):
    top = my + 3 + n * 13
    if n == 0:
        c.fill(mx + 2, top, mx + mw - 2, top + 12, TEAL_FILL)
    c.text(text, mx + 7, top + 2, color)
panels_b.append(("B1  Right-click a spot: small menu", c))

# ---- B2: an Edit button, then click a spot for an edit panel
c, w = wheel(click_mode=True)
w.hint = lambda: "Edit mode: click a spot to change it"
w.render()
button(c, 8, 8, 8 + width("Done") + 16, "Done", hovered=True, height=20)
c.fill(0, 0, W, H, 0x90000000)
x0, y0, x1, y1 = 34, 70, 246, 170
box(c, x0, y0, x1, y1, border=TEAL)
c.centered("Edit spot", W // 2, y0 + 8, COLOR_TEXT)
c.item(x0 + 10, y0 + 24, (150, 105, 60))
edit_box(c, x0 + 32, y0 + 24, x1 - 10, "Crafting Table", selected=False, cursor=True)
bx = x0 + 10
for label, color in (("Picture", None), ("Earlier", None), ("Later", None), ("Delete", COLOR_NO_POWER)):
    bw = width(label) + 12
    button(c, bx, y0 + 48, bx + bw, label, color=color)
    bx += bw + 4
button(c, W // 2 - 30, y0 + 74, W // 2 + 30, "Done", hovered=True)
panels_b.append(("B2  \"Edit\" button, then click a spot: one panel with everything", c))

# ---- B3: a list screen
c = Canvas(W, H, S)
c.backdrop()
c.fill(0, 0, W, H, 0xC0000000)
c.centered("Your spots (10 of 16)", W // 2, 8, COLOR_TEXT)
top = 24
for n, (name, rgb) in enumerate(SPOTS[:8]):
    y = top + n * 22
    c.fill(16, y, 258, y + 20, 0xD8207068 if n == 1 else 0x60141418)
    c.item(20, y + 2, rgb)
    c.text(name, 42, y + 6, COLOR_TEXT if n == 1 else COLOR_TEXT_SOFT)
    button(c, 150, y + 2, 196, "Rename", hovered=(n == 1))
    c.fill(200, y + 2, 216, y + 18, 0xFF26262C); arrow(c, 204, y + 8, True)
    c.fill(220, y + 2, 236, y + 18, 0xFF26262C); arrow(c, 224, y + 8, False)
    c.fill(240, y + 2, 256, y + 18, 0xFF26262C); cross(c, 244, y + 6)
c.fill(262, top, 266, top + 8 * 22 - 2, 0xFF1A1A1E)
c.fill(262, top, 266, top + 140, 0xFF707478)
button(c, W // 2 - 30, 212, W // 2 + 30, "Done", height=20)
panels_b.append(("B3  A list screen (opened from a button on the wheel)", c))

if __name__ == "__main__":
    print(sheet(panels_a, 2, os.path.join(OUT, "jumper-naming-options.png")))
    print(sheet(panels_b, 3, os.path.join(OUT, "jumper-editing-options.png")))
