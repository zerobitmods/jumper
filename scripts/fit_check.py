"""Checks that the wheel fits at every window size: nothing overlaps (ring, buttons, hint line, page label) and every
name stays inside its wedge. Uses wheelsim.py (same maths as WheelScreen). Sweeps the GUI sizes Minecraft actually
produces for common windows and GUI scales (the game picks the largest scale that keeps the GUI at least 320 x 240),
plus a few odd ones, with the longest hint and two pages of spots. Prints every problem it finds and draws a sheet of
the sizes most worth looking at.

Usage: python3 fit_check.py <output folder>   (needs Pillow and JUMPER_FONT, see wheelsim.py)
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wheelsim import *  # noqa: E402,F401

OUT = sys.argv[1] if len(sys.argv) > 1 else "."

SPOTS = [("Furnace", (120, 120, 120)), ("Crafting Table", (150, 105, 60)), ("Storage Room", (160, 120, 70)),
         ("Iron Golem Farm", (200, 200, 205)), ("Enchanting Table", (60, 40, 90)), ("Mob Grinder", (90, 110, 90)),
         ("Supercalifragilistic", (70, 130, 180)), ("Nether Portal", (110, 50, 160)),
         ("Quarry", (130, 130, 110)), ("Wheat Farm", (200, 180, 80))]
CAP = 240 * 72000

# Windows people use (framebuffer pixels; Mac Retina windows count double).
WINDOWS = [(854, 480), (1024, 768), (1280, 720), (1280, 800), (1366, 768), (1440, 900), (1600, 900), (1680, 1050),
           (1920, 1080), (1920, 1200), (2560, 1080), (2560, 1440), (2880, 1800), (3024, 1964), (3456, 2234),
           (3840, 2160), (1000, 1000), (900, 1200)]


def gui_size(fw, fh, chosen):
    """Window.calculateScale: the chosen scale (0 = auto), lowered until the GUI is at least 320 x 240."""
    scale = 1
    while scale != chosen and scale < fw and scale < fh and fw // (scale + 1) >= 320 and fh // (scale + 1) >= 240:
        scale += 1
    return math.ceil(fw / scale), math.ceil(fh / scale)


def sizes():
    seen = set()
    for fw, fh in WINDOWS:
        for chosen in (0, 1, 2, 3, 4, 5, 6):
            seen.add(gui_size(fw, fh, chosen))
    seen.update({(280, 240), (300, 240), (320, 240), (480, 270), (240, 320), (320, 400)})
    return sorted(seen)


def rect_hits_circle(rect, cx, cy, radius):
    x0, y0, x1, y1 = rect
    dx = max(x0 - cx, 0, cx - x1)
    dy = max(y0 - cy, 0, cy - y1)
    return dx * dx + dy * dy < radius * radius


def rects_hit(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def check(w, h, scale=1, page=0):
    c = Canvas(w, h, scale)
    c.backdrop()
    # Out of power and not loaded, standing too far: the longest hint there is; Back shown; Save reserved.
    wheel = Wheel(c, SPOTS, 16, power=POWER_BOTH, energy=0, capacity=CAP, rate=240, here=HERE_LIMITED, usable=8,
                  page=page)
    wheel.render()
    # Save can't show in this state, but its place is reserved: draw it anyway so the picture shows every button.
    wheel.button(wheel.save_left, wheel.save_right, tr("screen.jumper.wheel.save"), False, False)
    problems = []
    cx, cy = w // 2, h // 2
    top = wheel.button_top
    buttons = {"Save": (wheel.save_left, top, wheel.save_right, top + BUTTON_HEIGHT),
               "Home": (wheel.home_left, top, wheel.home_right, top + BUTTON_HEIGHT),
               "Back": (wheel.back_left, top, wheel.back_right, top + BUTTON_HEIGHT),
               "Edit spots": (BUTTON_MARGIN, BUTTON_MARGIN, wheel.edit_right, BUTTON_MARGIN + BUTTON_HEIGHT)}
    for name, rect in buttons.items():
        if rect_hits_circle(rect, cx, cy, wheel.outer):
            problems.append("%s button touches the ring" % name)
        if rect[0] < 0 or rect[2] > w or rect[1] < 0 or rect[3] > h:
            problems.append("%s button is off screen" % name)
    hint_w = width(wheel.hint_text) * wheel.hint_scale
    hint_top = wheel.hint_y + 4.0 * (1.0 - wheel.hint_scale)
    hint = (cx - hint_w / 2, hint_top, cx + hint_w / 2, hint_top + 8 * wheel.hint_scale)
    for name, rect in buttons.items():
        if rects_hit(hint, rect):
            problems.append("hint line overlaps the %s button" % name)
    if rect_hits_circle(hint, cx, cy, wheel.outer):
        problems.append("hint line overlaps the ring")
    if hint[0] < 0 or hint[2] > w or hint[3] > h:
        problems.append("hint line is cut off")
    if wheel.hint_scale < 0.6:
        problems.append("hint shrunk to %.2f" % wheel.hint_scale)
    if wheel.pages > 1:
        label = tr("screen.jumper.wheel.page", page + 1, wheel.pages)
        ly = max(4, cy - wheel.outer - 14)
        lw = width(label)
        box = (cx - lw // 2, ly, cx - lw // 2 + lw, ly + 8)
        if rects_hit(box, buttons["Edit spots"]):
            problems.append("page label overlaps Edit spots")
        if rect_hits_circle(box, cx, cy, wheel.outer):
            problems.append("page label overlaps the ring")
    for row in wheel.layout_report:
        if row[0].startswith("  !"):
            problems.append("name outside its wedge: %s" % row[1])
    return c, wheel, problems


if __name__ == "__main__":
    total = 0
    for w, h in sizes():
        _, wheel, problems = check(w, h)
        total += len(problems)
        print("%4d x %-4d outer %3d  hint scale %.2f  %s" % (w, h, wheel.outer, wheel.hint_scale,
                                                         "; ".join(problems) if problems else "ok"))
    print("problems:", total)
    if len(sys.argv) > 2 and sys.argv[2] == "--sheet":
        from PIL import Image, ImageDraw, ImageFont
        shown = [(280, 240), (320, 240), (480, 270), (427, 240), (640, 360), (960, 540)]
        cell_w, cell_h, title_h, pad = 820, 520, 26, 14
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 17)
        out = Image.new("RGB", (3 * (cell_w + pad) + pad, 2 * (cell_h + title_h + pad) + pad), (24, 24, 28))
        draw = ImageDraw.Draw(out)
        for n, (w, h) in enumerate(shown):
            c, wheel, problems = check(w, h, scale=2)
            img = c.img.convert("RGB")
            img.thumbnail((cell_w, cell_h), Image.LANCZOS)
            x = pad + (n % 3) * (cell_w + pad)
            y = pad + (n // 3) * (cell_h + title_h + pad)
            label = "%d x %d GUI px: %s" % (w, h, "fits" if not [p for p in problems if "wedge" not in p]
                                             else problems[0])
            draw.text((x + 2, y + 2), label, font=font, fill=(235, 235, 235))
            out.paste(img, (x, y + title_h))
        path = os.path.join(OUT, "jumper-wheel-fit.png")
        out.save(path)
        print(path, out.size)
