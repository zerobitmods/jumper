import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wheelsim import *

# Pictures go to the folder given as the first argument (default: the current folder).
OUT = sys.argv[1] if len(sys.argv) > 1 else "."

SPOTS = [("Furnace", (120, 120, 120)), ("Crafting Table", (150, 105, 60)), ("Storage Room", (160, 120, 70)),
         ("Iron Golem Farm", (200, 200, 205)), ("Enchanting Table", (60, 40, 90)), ("Mob Grinder", (90, 110, 90)),
         ("Supercalifragilistic", (70, 130, 180)), ("Nether Portal", (110, 50, 160)),
         ("Quarry", (130, 130, 110)), ("Wheat Farm", (200, 180, 80))]
CAP = 240 * 72000
def panel(w, h, scale, title, **kw):
    c = Canvas(w, h, scale)
    c.backdrop()
    wheel = Wheel(c, SPOTS, 16, **kw)
    wheel.render()
    print("==", title, "| window", w, "x", h, "| outer", wheel.outer, "inner", wheel.inner, "| label width", wheel.label_width)
    for row in wheel.layout_report:
        print("   ", row)
    print("    hub:", getattr(wheel, "hub_scales", None))
    print("    hint: %r (width %d, scale %.2f)" % (wheel.hint_text, width(wheel.hint_text), wheel.hint_scale))
    return (title, c)

small = [
    panel(280, 240, 3, "Working: names on two lines", power=POWER_ON, energy=int(CAP * 0.62), capacity=CAP, rate=240, hovered=1),
    panel(280, 240, 3, "Base not loaded, has power", power=POWER_NOT_LOADED, energy=int(CAP * 0.62), capacity=CAP, rate=240, here=HERE_LIMITED, usable=8),
    panel(280, 240, 3, "Base not loaded AND no power", power=POWER_BOTH, energy=0, capacity=CAP, rate=240, here=HERE_LIMITED, usable=8),
    panel(280, 240, 3, "No power (base loaded), near the anchor", power=POWER_NONE, energy=0, capacity=CAP, rate=240, usable=8, hovered=3),
]
print(sheet(small, 2, os.path.join(OUT, "check10b_small.png")))
big = [
    panel(480, 270, 2, "Wider window: working", power=POWER_ON, energy=int(CAP * 0.62), capacity=CAP, rate=240, hovered=1),
    panel(480, 270, 2, "Wider window: not loaded + no power", power=POWER_BOTH, energy=0, capacity=CAP, rate=240, here=HERE_LIMITED, usable=8),
    panel(200, 170, 3, "Tiny window: one line only", power=POWER_NOT_LOADED, energy=int(CAP * 0.3), capacity=CAP, rate=240, here=HERE_LIMITED, usable=8),
    panel(640, 360, 2, "Large window", power=POWER_NOT_LOADED, energy=int(CAP * 0.3), capacity=CAP, rate=240, here=HERE_LIMITED, usable=8),
]
big[0][1].save(os.path.join(OUT, "check10b_wide_working.png")); big[1][1].save(os.path.join(OUT, "check10b_wide_both.png"))
big[2][1].save(os.path.join(OUT, "check10b_tiny.png")); big[3][1].save(os.path.join(OUT, "check10b_large.png"))
