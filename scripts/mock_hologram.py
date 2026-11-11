"""Mock-up of range hologram options for the Jumper Anchor (the owner's idea after step 13: show the range when you look
at the anchor, like a storage controller's area box). Draws a first-person view a few blocks from an anchor on flat
ground, with Minecraft-like fog, and the options on top.

The range is a sphere around the anchor's middle (straight-line distance, JumpService.reach), so the true edge on flat
ground is a ring; at tier 2 (256 blocks) it is already past a typical view distance (12 chunks, fog from ~150 blocks).

Usage: python3 mock_hologram.py <output folder>   (needs Pillow, numpy and JUMPER_FONT, see wheelsim.py)
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from wheelsim import Canvas, sheet, width  # noqa: E402

OUT = sys.argv[1] if len(sys.argv) > 1 else "."
W, H, S = 280, 240, 3
FOV = math.radians(70)
FOG_START, FOG_END = 120.0, 190.0
TEAL = (47, 168, 154)
AMBER = (232, 170, 60)
SKY_TOP = np.array([120, 165, 255], dtype=float)
SKY_HORIZON = np.array([192, 216, 255], dtype=float)

CAM = np.array([0.5, 1.62, 5.8])
LOOK = np.array([0.5, 0.5, 1.0])  # the crosshair is on the anchor's front face
CENTER = np.array([0.5, 0.5, 0.5])  # ranges are measured from the middle of the anchor block

forward = LOOK - CAM
forward /= np.linalg.norm(forward)
right = np.cross(forward, [0.0, 1.0, 0.0])
right /= np.linalg.norm(right)
up = np.cross(right, forward)
FOCAL = (H * S / 2) / math.tan(FOV / 2)


def project(p):
    v = np.asarray(p, dtype=float) - CAM
    z = v @ forward
    if z < 0.05:
        return None
    return (W * S / 2 + FOCAL * (v @ right) / z, H * S / 2 - FOCAL * (v @ up) / z, z)


def fog(distance):
    return min(1.0, max(0.0, (distance - FOG_START) / (FOG_END - FOG_START)))


def world():
    """Sky and grass per pixel (numpy), with fog."""
    ys, xs = np.mgrid[0:H * S, 0:W * S].astype(float)
    dx = (xs - W * S / 2) / FOCAL
    dy = -(ys - H * S / 2) / FOCAL
    rays = forward[None, None, :] + dx[..., None] * right[None, None, :] + dy[..., None] * up[None, None, :]
    img = np.zeros((H * S, W * S, 3))
    elevation = rays[..., 1] / np.linalg.norm(rays, axis=-1)
    sky_t = np.clip(elevation / 0.5, 0, 1)[..., None]
    img[:] = SKY_HORIZON * (1 - sky_t) + SKY_TOP * sky_t
    down = rays[..., 1] < -1e-6
    t = np.where(down, -CAM[1] / np.where(down, rays[..., 1], -1), 0)
    hx = CAM[0] + t * rays[..., 0]
    hz = CAM[2] + t * rays[..., 2]
    dist = t * np.linalg.norm(rays, axis=-1)
    checker = ((np.floor(hx) + np.floor(hz)) % 2)[..., None]
    grass = np.array([92, 140, 60]) * (1 - checker) + np.array([86, 132, 56]) * checker
    f = np.clip((dist - FOG_START) / (FOG_END - FOG_START), 0, 1)[..., None]
    ground = grass * (1 - f) + SKY_HORIZON * f
    img = np.where(down[..., None], ground, img)
    return Image.fromarray(img.astype(np.uint8), "RGB").convert("RGBA")


def box(draw, x0, y0, z0, x1, y1, z1, top, side, front):
    """A solid box: the faces turned towards the camera, far ones first."""
    faces = [
        ((x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1), (0, 1, 0), top),
        ((x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1), (0, 0, 1), front),
        ((x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0), (1, 0, 0), side),
        ((x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0), (-1, 0, 0), side),
    ]
    for *corners, normal, color in faces:
        middle = np.mean(np.array(corners), axis=0)
        if np.dot(CAM - middle, normal) <= 0:
            continue
        points = [project(c) for c in corners]
        if any(p is None for p in points):
            continue
        f = fog(np.linalg.norm(middle - CAM))
        shade = tuple(int(c * (1 - f) + h * f) for c, h in zip(color, SKY_HORIZON))
        draw.polygon([(p[0], p[1]) for p in points], fill=shade + (255,))


def scene():
    img = world()
    draw = ImageDraw.Draw(img)
    # Things for scale, far ones first: a house, a tree, a stone pile.
    box(draw, -34, 0, -52, -24, 7, -42, (150, 110, 70), (120, 86, 52), (136, 98, 60))
    box(draw, 14.6, 0, -30.4, 15.4, 5, -29.6, (90, 66, 40), (80, 58, 34), (86, 62, 38))
    box(draw, 12.5, 4, -32.5, 17.5, 8, -27.5, (60, 120, 50), (48, 100, 40), (54, 110, 45))
    box(draw, -12, 0, -18, -8, 2, -14, (132, 132, 132), (104, 104, 104), (118, 118, 118))
    # The anchor: obsidian with a netherite-grey middle.
    box(draw, 0, 0, 0, 1, 1, 1, (52, 40, 78), (30, 22, 46), (40, 30, 60))
    a, b = project((0.3, 0.3, 1.0)), project((0.7, 0.7, 1.0))
    c, d = project((0.42, 0.42, 1.0)), project((0.58, 0.58, 1.0))
    if None not in (a, b, c, d) and CAM[2] > 1.0:
        draw.rectangle((min(a[0], b[0]), min(a[1], b[1]), max(a[0], b[0]), max(a[1], b[1])), fill=(70, 66, 74, 255))
        draw.rectangle((min(c[0], d[0]), min(c[1], d[1]), max(c[0], d[0]), max(c[1], d[1])), fill=(170, 120, 230, 255))
    return img


def polyline(layer, points, color, alpha, line_width):
    """3D points; segments behind the camera or under the ground are skipped; fog fades them."""
    draw = ImageDraw.Draw(layer)
    for p, q in zip(points, points[1:]):
        if p[1] < 0 or q[1] < 0:
            continue
        a, b = project(p), project(q)
        if a is None or b is None:
            continue
        f = fog(np.linalg.norm((np.asarray(p) + np.asarray(q)) / 2 - CAM))
        if f >= 1:
            continue
        draw.line((a[0], a[1], b[0], b[1]), fill=color + (int(alpha * (1 - f)),), width=line_width)


def ring(radius, y, steps=360):
    return [(CENTER[0] + radius * math.cos(2 * math.pi * i / steps), y,
             CENTER[2] + radius * math.sin(2 * math.pi * i / steps)) for i in range(steps + 1)]


def sphere(layer, radius, color):
    for lat in (0, 20, 40, 60):
        r = radius * math.cos(math.radians(lat))
        polyline(layer, ring(r, CENTER[1] + radius * math.sin(math.radians(lat))), color, 150, 2)
    for lon in range(0, 360, 15):
        pts = []
        for lat in range(0, 91, 2):
            r = radius * math.cos(math.radians(lat))
            pts.append((CENTER[0] + r * math.cos(math.radians(lon)), CENTER[1] + radius * math.sin(math.radians(lat)),
                        CENTER[2] + r * math.sin(math.radians(lon))))
        polyline(layer, pts, color, 110, 2)


def ground_ring(layer, radius, color, posts=True):
    r = math.sqrt(max(0.0, radius * radius - CENTER[1] ** 2))
    polyline(layer, ring(r, 0.05), color, 235, 3)
    if posts:
        polyline(layer, ring(r, 6.0), color, 90, 2)
        for deg in range(0, 360, 8):
            x = CENTER[0] + r * math.cos(math.radians(deg))
            z = CENTER[2] + r * math.sin(math.radians(deg))
            polyline(layer, [(x, 0.05, z), (x, 6.0, z)], color, 170, 2)


def label(canvas, lines):
    """A name-tag style label just above the anchor. lines: (text, argb)."""
    p = project((0.5, 1.35, 0.5))
    cx, bottom = p[0] / S, p[1] / S
    widest = max(width(t) for t, _ in lines)
    top = bottom - 10 * len(lines) - 2
    canvas.fill(cx - widest / 2 - 3, top, cx + widest / 2 + 3, bottom, 0x90000000)
    for n, (text, argb) in enumerate(lines):
        canvas.centered(text, cx, top + 2 + 10 * n, argb)


def hud(c):
    """Vanilla hotbar, xp, hearts and food, for a sense of the screen."""
    x0 = W // 2 - 91
    c.fill(x0, H - 22, x0 + 182, H, 0xC0303030)
    for i in range(9):
        c.fill(x0 + 1 + i * 20, H - 21, x0 + 19 + i * 20, H - 1, 0x80606060)
    c.fill(x0 - 1, H - 23, x0 + 23, H + 1, 0xFFE0E0E0)
    c.fill(x0 + 1, H - 21, x0 + 21, H - 1, 0xC0303030)
    c.fill(x0, H - 29, x0 + 182, H - 24, 0xFF202020)
    c.fill(x0, H - 29, x0 + 120, H - 24, 0xFF80FF20)
    for i in range(10):
        c.fill(x0 + i * 8, H - 39, x0 + i * 8 + 7, H - 32, 0xFFD02020)
        c.fill(x0 + 182 - 7 - i * 8, H - 39, x0 + 182 - i * 8, H - 32, 0xFFB07030)


def view(draw_hologram=None, lines=None):
    c = Canvas(W, H, S)
    img = scene()
    if draw_hologram:
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        draw_hologram(layer)
        img.alpha_composite(layer)
    c.img = img
    if lines:
        label(c, lines)
    c.fill(W / 2 - 0.5, H / 2 - 4.5, W / 2 + 0.5, H / 2 + 4.5, 0xD0FFFFFF)
    c.fill(W / 2 - 4.5, H / 2 - 0.5, W / 2 + 4.5, H / 2 + 0.5, 0xD0FFFFFF)
    hud(c)
    return c


WHITE, SOFT, GREEN, RED, AMBER_TEXT = 0xFFFFFFFF, 0xFFB8C0C8, 0xFF7FE07F, 0xFFE24B4A, 0xFFE8AA3C


def main():
    panels = [
        ("R1: true-size dome (tier 1, 64 blocks)", view(lambda l: sphere(l, 64, TEAL))),
        ("R2: ring on the ground + a low fence", view(lambda l: ground_ring(l, 64, TEAL))),
        ("R3: label only (any tier, any range)",
         view(None, [("Tier 1 anchor", WHITE), ("Reaches 64 blocks", SOFT), ("Linked: you", GREEN)])),
        ("R4: ring + label (my pick)",
         view(lambda l: ground_ring(l, 64, TEAL),
              [("Tier 1 anchor", WHITE), ("Reaches 64 blocks", SOFT), ("Linked: you", GREEN)])),
        ("R4 at tier 2: 256 blocks is past the fog",
         view(None, [("Tier 2 anchor - 62% power", WHITE), ("Reaches 256 blocks", SOFT),
                     ("(edge is past your view)", SOFT)])),
        ("R4, tier 2 with no power: works as tier 1",
         view(lambda l: ground_ring(l, 64, AMBER),
              [("Tier 2 anchor - No power", RED), ("Reaches 64 for now", AMBER_TEXT), ("256 once powered", SOFT)])),
    ]

    print(sheet(panels, 3, os.path.join(OUT, "jumper-hologram-options.png")))


if __name__ == "__main__":
    main()
