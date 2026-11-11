"""Placeholder 16x16 item textures for the three anchor upgrades. Deterministic: same pixels every run.

A dark crystal in the anchor's colours with a core in the tier's colour and one dot per tier along the bottom.
Usage: python3 upgrade_item_textures.py <folder>  (writes anchor_upgrade_2.png .. anchor_upgrade_4.png)

Superseded: the textures in the mod now come from the design session (design/jumper/jumper-spec.md). This script
refuses to write into a textures folder so it can't overwrite them by accident.
"""
import sys
from PIL import Image

out = sys.argv[1]
if out.rstrip("/").endswith(("textures/block", "textures/item")):
    sys.exit("Superseded by the design session's final textures; write the placeholders to another folder.")
EDGE = (30, 28, 34)
SHELL = [(52, 49, 58), (66, 62, 73)]
GLOW = (78, 226, 208)
CORES = {2: ((60, 190, 200), (150, 240, 245)),
         3: ((80, 190, 80), (170, 245, 160)),
         4: ((176, 108, 255), (235, 210, 255))}

for tier, (core, shine) in CORES.items():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # crystal: a diamond shape from row 1 to row 12, centred on x 7.5
    for y in range(1, 13):
        half = min(y - 1, 12 - y) * 1.05 + 1.0
        for x in range(16):
            d = abs(x - 7.5)
            if d <= half:
                edge = d > half - 1.0 or y in (1, 12)
                img.putpixel((x, y), (EDGE if edge else SHELL[(x + y) % 2]) + (255,))
    # core
    for y in range(4, 10):
        half = min(y - 4, 9 - y) * 1.2 + 0.5
        for x in range(16):
            if abs(x - 7.5) <= half:
                img.putpixel((x, y), core + (255,))
    img.putpixel((7, 5), shine + (255,))
    img.putpixel((6, 6), shine + (255,))
    # one dot per tier along the bottom
    start = 8 - tier * 2 + 1
    for n in range(tier):
        x = start + n * 2 - 1
        img.putpixel((x, 14), GLOW + (255,))
        img.putpixel((x, 15), EDGE + (255,))
    img.save("%s/anchor_upgrade_%d.png" % (out, tier))
