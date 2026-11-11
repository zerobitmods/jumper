"""Mock-up of the anchor's right-click menu with the range-dome toggle (the owner's picks after step 13: the full dome
(R1), only for your own anchor, turned on and off from a menu on the anchor). Reuses the first-person scene from
mock_hologram.py.

Usage: python3 mock_anchor_menu.py <output folder>   (needs Pillow, numpy and JUMPER_FONT, see wheelsim.py)
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mock_hologram as mh  # noqa: E402
from wheelsim import Canvas, sheet, width  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
W, H, S = mh.W, mh.H, mh.S
WHITE, SOFT, DIM, GREEN, RED, AMBER = 0xFFFFFFFF, 0xFFB8C0C8, 0xFF707478, 0xFF7FE07F, 0xFFE24B4A, 0xFFE8AA3C
EDGE, PANEL, BUTTON, BUTTON_ON, BUTTON_HOVER = 0xFF2FA89A, 0xF4101014, 0xFF26262C, 0xFF1E6A62, 0xD8207068


def look_from(cam, look):
    """Moves mock_hologram's camera."""
    mh.CAM = np.array(cam, dtype=float)
    f = np.array(look, dtype=float) - mh.CAM
    mh.forward = f / np.linalg.norm(f)
    r = np.cross(mh.forward, [0.0, 1.0, 0.0])
    mh.right = r / np.linalg.norm(r)
    mh.up = np.cross(mh.right, mh.forward)


def dome_grid(layer, radius, color, spacing=8.0):
    """The dome as a cage with a line about every `spacing` blocks along its surface (so the wall is easy to see when
    you walk up to it), brighter near the ground where you meet it."""
    import math
    step = math.degrees(spacing / radius)
    lat = 0.0
    while lat < 88:
        r = radius * math.cos(math.radians(lat))
        mh.polyline(layer, mh.ring(r, mh.CENTER[1] + radius * math.sin(math.radians(lat)), 720), color,
                    200 if lat == 0 else 120, 2)
        lat += step
    lon = 0.0
    while lon < 360:
        pts = []
        for n in range(0, 91):
            la = math.radians(n)
            r = radius * math.cos(la)
            pts.append((mh.CENTER[0] + r * math.cos(math.radians(lon)), mh.CENTER[1] + radius * math.sin(la),
                        mh.CENTER[2] + r * math.sin(math.radians(lon))))
        mh.polyline(layer, pts, color, 120, 2)
        lon += step


def world(dome=None, hud=True, crosshair=True):
    c = Canvas(W, H, S)
    img = mh.scene()
    if dome:
        radius, color = dome
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        dome_grid(layer, radius, color)
        img.alpha_composite(layer)
    c.img = img
    if crosshair:
        c.fill(W / 2 - 0.5, H / 2 - 4.5, W / 2 + 0.5, H / 2 + 4.5, 0xD0FFFFFF)
        c.fill(W / 2 - 4.5, H / 2 - 0.5, W / 2 + 4.5, H / 2 + 0.5, 0xD0FFFFFF)
    if hud:
        mh.hud(c)
    return c


def button(c, left, top, right, label, fill, color):
    c.fill(left, top, right, top + 18, fill)
    c.centered(label, (left + right) // 2, top + 5, color)


def menu(c, lines, dome_label, dome_fill, dome_color, link_button=False, note=""):
    """The anchor menu: a panel like the name box (teal edge), info lines, the dome toggle and Done."""
    c.fill(0, 0, W, H, 0x60000000)
    pw, ph = 204, 124
    left, top = (W - pw) // 2, (H - ph) // 2 - 6
    c.fill(left, top, left + pw, top + ph, EDGE)
    c.fill(left + 1, top + 1, left + pw - 1, top + ph - 1, PANEL)
    c.item(left + 8, top + 6, (52, 40, 78))
    c.text("Jumper Anchor", left + 30, top + 10, WHITE)
    y = top + 28
    for text, color in lines:
        c.text(text, left + 10, y, color)
        y += 11
    if link_button:
        button(c, left + 10, y + 1, left + pw - 10, "Link to this anchor", BUTTON_HOVER, WHITE)
    by = top + ph - 26
    button(c, left + 8, by, left + 136, dome_label, dome_fill, dome_color)
    button(c, left + 142, by, left + pw - 8, "Done", BUTTON, SOFT)
    if note:
        c.shrunk(note, W // 2, top + ph + 6, W - 12, SOFT)
    return c


def main():
    look_from([0.5, 1.62, 5.8], [0.5, 0.5, 1.0])
    yours = [("Tier 2 - reaches 256 blocks", SOFT), ("Power: 62%, 4h 10m left", GREEN),
             ("Your spots: 10 of 16", SOFT), ("Linked: this is your anchor", GREEN)]
    panels = [
        ("Menu on your anchor, dome on",
         menu(world((64, mh.TEAL), hud=False, crosshair=False),
              [("Tier 1 - reaches 64 blocks", SOFT), ("Power: none needed", SOFT), ("Your spots: 6 of 8", SOFT),
               ("Linked: this is your anchor", GREEN)],
              "Range dome: On", BUTTON_ON, WHITE, note="The dome shows how far your anchor reaches")),
        ("Menu on an anchor you aren't linked to",
         menu(world(None, hud=False, crosshair=False),
              [("Not your anchor", AMBER), ("You're linked to another one", SOFT)],
              "Range dome: link first", BUTTON, DIM, link_button=True,
              note="Linking here moves your spots' reach to this anchor")),
        ("Menu, your tier 2 anchor out of power",
         menu(world((64, mh.AMBER), hud=False, crosshair=False),
              [("Tier 2 - reaches 64 for now", AMBER), ("Power: none (256 once powered)", RED),
               ("Your spots: 10 of 16 (8 work)", SOFT), ("Linked: this is your anchor", GREEN)],
              "Range dome: On", BUTTON_ON, WHITE, note="Amber: it works as a tier 1 anchor until it has power")),
    ]
    # Menu closed, dome on: standing in the base, and walking out near the edge.
    look_from([6.0, 1.62, 10.0], [-10.0, 6.0, -40.0])
    panels.append(("Menu closed, dome on: in your base", world((64, mh.TEAL))))
    look_from([0.5, 1.62, -50.0], [0.5, 4.0, -80.0])
    panels.append(("Walking out: the edge 14 blocks ahead", world((64, mh.TEAL))))
    look_from([0.5, 1.62, -50.0], [0.5, 4.0, -80.0])
    panels.append(("Same place, dome off (as now)", world(None)))
    print(sheet(panels, 3, os.path.join(OUT, "jumper-anchor-menu-options.png")))


if __name__ == "__main__":
    main()
