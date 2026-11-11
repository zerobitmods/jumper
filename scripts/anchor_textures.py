"""Placeholder 16x16 textures for the Jumper Anchor (side and top). Deterministic: same pixels every run.

Superseded: the textures in the mod now come from the design session (design/jumper/jumper-spec.md). This script
refuses to write into a textures folder so it can't overwrite them by accident.
"""
import sys
from PIL import Image

out = sys.argv[1]
if out.rstrip("/").endswith(("textures/block", "textures/item")):
    sys.exit("Superseded by the design session's final textures; write the placeholders to another folder.")
BASE = [(52, 49, 58), (60, 57, 67), (45, 42, 50), (66, 62, 73)]
EDGE = (30, 28, 34)
GLOW = (78, 226, 208)
GLOW_DIM = (40, 150, 140)
CORE = (176, 108, 255)

def noise(x, y, salt):
    return ((x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)) % 4

def base(salt):
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = BASE[noise(x, y, salt)]
            if x in (0, 15) or y in (0, 15):
                c = EDGE
            img.putpixel((x, y), c + (255,))
    return img

side = base(1)
for x in range(1, 15):
    side.putpixel((x, 6), GLOW_DIM + (255,))
    side.putpixel((x, 7), GLOW + (255,))
    side.putpixel((x, 8), GLOW + (255,))
    side.putpixel((x, 9), GLOW_DIM + (255,))
side.save(out + "/jumper_anchor_side.png")

top = base(2)
for y in range(16):
    for x in range(16):
        d2 = (x - 7.5) ** 2 + (y - 7.5) ** 2
        if 20 <= d2 <= 32:
            top.putpixel((x, y), GLOW + (255,))
        elif 14 <= d2 < 20 or 32 < d2 <= 38:
            top.putpixel((x, y), GLOW_DIM + (255,))
        elif d2 <= 3:
            top.putpixel((x, y), CORE + (255,))
top.save(out + "/jumper_anchor_top.png")
