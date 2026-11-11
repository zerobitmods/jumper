import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wheelsim import *
from mock_naming import TEAL, TEAL_FILL, box

# Upgrade item options (step 12), drawn at the owner's window size. Pictures go to the folder given first.
OUT = sys.argv[1] if len(sys.argv) > 1 else "."
W, H, S = 280, 240, 3

DIAMOND, PEARL, GOLD = (90, 220, 215), (30, 100, 90), (240, 200, 60)
EYE, NETHERITE, STAR, CRYING = (60, 140, 70), (70, 60, 65), (235, 235, 250), (60, 20, 90)
UP2, UP3, UP4 = (60, 190, 200), (90, 200, 90), (190, 120, 230)
ANCHOR = (40, 30, 60)
GREEN, RED = 0xFF7FE07F, 0xFFFF6B6B


def upgrade(c, x, y, rgb, label):
    c.item(x, y, rgb)
    c.text(label, x + 16 - width(label) - 1, y + 9, COLOR_TEXT)


def slot(c, x, y):
    c.fill(x, y, x + 18, y + 18, 0xFF373737)
    c.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF)
    c.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B)


def hotbar(c, held_rgb, held_label):
    x0 = W // 2 - 91
    c.fill(x0, H - 22, x0 + 182, H, 0xC0303030)
    for i in range(9):
        c.fill(x0 + 1 + i * 20, H - 21, x0 + 19 + i * 20, H - 1, 0x80606060)
    c.fill(x0 + 79, H - 23, x0 + 103, H + 1, 0xFFE0E0E0)
    c.fill(x0 + 81, H - 21, x0 + 101, H - 1, 0xC0303030)
    upgrade(c, x0 + 83, H - 19, held_rgb, held_label)


def anchor_face(c):
    x0, y0, size = W // 2 - 45, 40, 90
    c.fill(x0, y0, x0 + size, y0 + size, 0xFF1A1424)
    for gx in range(6):
        for gy in range(6):
            shade = 0xFF2A2038 if (gx + gy) % 2 else 0xFF221A2E
            c.fill(x0 + 2 + gx * 14.6, y0 + 2 + gy * 14.6, x0 + 15 + gx * 14.6, y0 + 15 + gy * 14.6, shade)
    c.fill(x0 + 35, y0 + 35, x0 + 55, y0 + 55, 0xFF6040A0)
    c.fill(x0 + 40, y0 + 40, x0 + 50, y0 + 50, 0xFFB090F0)
    c.fill(W // 2 - 4, 84, W // 2 + 5, 85, 0xFFFFFFFF)   # crosshair
    c.fill(W // 2, 80, W // 2 + 1, 89, 0xFFFFFFFF)


def world_panel(message, color, held_rgb, held_label):
    c = Canvas(W, H, S)
    c.backdrop()
    anchor_face(c)
    c.shrunk(message, W // 2, H - 40, W - 10, color)
    hotbar(c, held_rgb, held_label)
    return c


def tooltip(c, x, y, lines):
    w = max(width(t) for t, _ in lines) + 8
    h = len(lines) * 10 + 4
    c.fill(x, y, x + w, y + h, 0xF0100010)
    c.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF5000FF)
    c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF28007F)
    for n, (text, color) in enumerate(lines):
        c.text(text, x + 4, y + 3 + n * 10 + (2 if n else 0), color)


def inventory_bg(c):
    c.fill(0, 0, W, H, 0xFF101014)
    for gy in range(0, H, 16):
        for gx in range(0, W, 16):
            if (gx // 16 + gy // 16) % 2:
                c.fill(gx, gy, gx + 16, gy + 16, 0xFF141418)


panels = []

c = world_panel("Anchor upgraded to tier 3: 2048 blocks, 32 spots", GREEN, UP3, "III")
panels.append(("A. Hold an upgrade, right-click the anchor", c))

c = world_panel("This anchor is tier 1. Use a Tier 2 Upgrade first", RED, UP3, "III")
panels.append(("A. In order only: a skipped tier is refused", c))

c = Canvas(W, H, S)
inventory_bg(c)
for n, (rgb, label) in enumerate(((UP2, "II"), (UP3, "III"), (UP4, "IV"))):
    slot(c, 20 + n * 20, 20)
    upgrade(c, 21 + n * 20, 21, rgb, label)
tooltip(c, 50, 44, [("Anchor Upgrade: Tier 3", 0xFF55FFFF),
                    ("Right-click a tier 2 anchor", 0xFFAAAAAA),
                    ("Range: 2048 blocks", 0xFFAAAAAA),
                    ("Spots: 32", 0xFFAAAAAA),
                    ("Upkeep: 200 FE/t", 0xFFAAAAAA)])
slot(c, 20, 140)
c.item(21, 141, ANCHOR)
tooltip(c, 44, 140, [("Jumper Anchor", 0xFFFFFFFF),
                     ("Tier 3 (upgrades kept)", 0xFF55FF55),
                     ("Stored power is lost", 0xFFAAAAAA)])
panels.append(("Tooltips (bottom: if breaking keeps the tier)", c))

c = Canvas(W, H, S)
c.backdrop()
c.fill(0, 0, W, H, 0x80000000)
gx, gy, gw, gh = W // 2 - 88, 20, 176, 190
c.fill(gx, gy, gx + gw, gy + gh, 0xFFC6C6C6)
c.fill(gx, gy, gx + gw, gy + 2, 0xFFFFFFFF)
c.fill(gx, gy + gh - 2, gx + gw, gy + gh, 0xFF555555)
c.text("Jumper Anchor", gx + 8, gy + 6, 0xFF404040, shadow=False)
for n, (rgb, label, filled) in enumerate(((UP2, "II", True), (UP3, "III", True), (UP4, "IV", False))):
    slot(c, gx + 8 + n * 22, gy + 20)
    if filled:
        upgrade(c, gx + 9 + n * 22, gy + 21, rgb, label)
c.text("Tier 3", gx + 8, gy + 44, 0xFF404040, shadow=False)
c.text("2048 blocks, 32 spots", gx + 8, gy + 54, 0xFF404040, shadow=False)
c.fill(gx + 150, gy + 18, gx + 166, gy + 70, 0xFF373737)
c.fill(gx + 151, gy + 40, gx + 165, gy + 69, 0xFFD03030)
c.text("62%", gx + 120, gy + 62, 0xFF404040, shadow=False)
for row in range(3):
    for col in range(9):
        slot(c, gx + 7 + col * 18, gy + 84 + row * 18)
for col in range(9):
    slot(c, gx + 7 + col * 18, gy + 142 + 4)
panels.append(("B. Or: an anchor screen with 3 upgrade slots", c))

c = Canvas(W, H, S)
inventory_bg(c)
recipes = [("Tier 2", UP2, "II", [DIAMOND, PEARL, DIAMOND, PEARL, GOLD, PEARL, DIAMOND, PEARL, DIAMOND]),
           ("Tier 3", UP3, "III", [EYE, DIAMOND, EYE, DIAMOND, NETHERITE, DIAMOND, EYE, DIAMOND, EYE]),
           ("Tier 4", UP4, "IV", [CRYING, EYE, CRYING, EYE, STAR, EYE, CRYING, EYE, CRYING])]
for r, (name, rgb, label, grid) in enumerate(recipes):
    y = 12 + r * 74
    c.text(name, 10, y + 24, COLOR_TEXT)
    for i, item in enumerate(grid):
        slot(c, 56 + (i % 3) * 18, y + (i // 3) * 18)
        c.item(57 + (i % 3) * 18, y + 1 + (i // 3) * 18, item)
    c.text("->", 124, y + 23, COLOR_TEXT)
    slot(c, 146, y + 18)
    upgrade(c, 147, y + 19, rgb, label)
c.text("diamond, pearl, gold block", 172, 34, COLOR_TEXT_SOFT, 0.75)
c.text("eye, diamond, netherite", 172, 108, COLOR_TEXT_SOFT, 0.75)
c.text("crying obsidian, eye,", 172, 178, COLOR_TEXT_SOFT, 0.75)
c.text("nether star", 172, 186, COLOR_TEXT_SOFT, 0.75)
panels.append(("Placeholder recipes (plain recipe files)", c))

print(sheet(panels, 3, os.path.join(OUT, "jumper-upgrade-options.png")))
