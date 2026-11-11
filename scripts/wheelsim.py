"""Draws the Jumper wheel offline with the same maths as WheelScreen.java (as of step 10b), for checking layouts and
for mock-ups. Keep it in step with WheelScreen when the wheel's layout changes. Sizes are in GUI pixels; the picture is
drawn `scale` times bigger. Text widths follow Minecraft's default font (the ADV table), so what fits here fits in game.

Needs Pillow and a Minecraft-style font for the glyph shapes (not shipped here): clone
https://github.com/IdreesInc/Minecraft-Font (OFL licence) and point JUMPER_FONT at its Minecraft.otf.
Used by wheel_check.py (the wheel in each power state at several window sizes) and mock_naming.py (option mock-ups).
"""
import json
import math
import os
import pathlib
from PIL import Image, ImageDraw, ImageFont

FONT_PATH = os.environ.get("JUMPER_FONT", "/home/claude/work/fonts/mcfont/Minecraft.otf")
LANG = json.load(open(pathlib.Path(__file__).resolve().parent.parent
                      / "src/main/resources/assets/jumper/lang/en_us.json"))

# Advance of each character in Minecraft's default font (glyph + 1 px gap); anything not listed is 6.
ADV = {' ': 4, '!': 2, '"': 4, "'": 2, '(': 4, ')': 4, '*': 4, ',': 2, '.': 2, ':': 2, ';': 2, '<': 5, '>': 5, '@': 7,
       'I': 4, '[': 4, ']': 4, '`': 3, 'f': 5, 'i': 2, 'k': 5, 'l': 3, 't': 4, '{': 4, '|': 2, '}': 4, '~': 7}


def width(text):
    return sum(ADV.get(ch, 6) for ch in text)


def substr_by_width(text, max_width):
    """Font.plainSubstrByWidth: the longest start of the text that fits."""
    total = 0
    for i, ch in enumerate(text):
        total += ADV.get(ch, 6)
        if total > max_width:
            return text[:i]
    return text


def tr(key, *args):
    text = LANG.get(key, key)
    for a in args:
        text = text.replace("%s", str(a), 1)
    return text


def jround(x):
    return int(math.floor(x + 0.5))


class Canvas:
    def __init__(self, w, h, scale=4):
        self.w, self.h, self.s = w, h, scale
        self.img = Image.new("RGBA", (w * scale, h * scale), (0, 0, 0, 255))
        self._fonts = {}

    def font(self, size):
        if size not in self._fonts:
            self._fonts[size] = ImageFont.truetype(FONT_PATH, size)
        return self._fonts[size]

    def fill(self, x0, y0, x1, y1, argb):
        s = self.s
        X0, Y0, X1, Y1 = jround(x0 * s), jround(y0 * s), jround(x1 * s), jround(y1 * s)
        X0, Y0 = max(0, X0), max(0, Y0)
        X1, Y1 = min(self.img.width, X1), min(self.img.height, Y1)
        if X1 <= X0 or Y1 <= Y0:
            return
        rgba = ((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255)
        self.img.alpha_composite(Image.new("RGBA", (X1 - X0, Y1 - Y0), rgba), (X0, Y0))

    def text(self, text, x, y, argb, scale=1.0, shadow=True):
        font = self.font(max(4, jround(8 * self.s * scale)))
        draw = ImageDraw.Draw(self.img)
        r, g, b = (argb >> 16) & 255, (argb >> 8) & 255, argb & 255
        passes = ([(scale, (r // 4, g // 4, b // 4))] if shadow else []) + [(0.0, (r, g, b))]
        for offset, color in passes:
            cx = x + offset
            for ch in text:
                draw.text((cx * self.s, (y - scale + offset) * self.s), ch, font=font, fill=color + (255,))
                cx += ADV.get(ch, 6) * scale

    def centered(self, text, cx, y, argb, scale=1.0):
        # drawCenteredString: x - width / 2 (whole pixels at full size)
        w = width(text)
        self.text(text, cx - (w // 2 if scale == 1.0 else w * scale / 2.0), y, argb, scale)

    def shrunk(self, text, cx, y, room, argb):
        """WheelScreen.drawShrunk"""
        w = width(text)
        if w <= room:
            self.centered(text, cx, y, argb)
            return 1.0
        scale = max(0.5, room / float(w))
        self.centered(text, cx, y + 4.0 * (1.0 - scale), argb, scale)
        return scale

    def item(self, x, y, rgb):
        """A stand-in for a 16x16 item picture: a little shaded block."""
        r, g, b = rgb
        lighter = 0xFF000000 | (min(255, r + 40) << 16) | (min(255, g + 40) << 8) | min(255, b + 40)
        base = 0xFF000000 | (r << 16) | (g << 8) | b
        darker = 0xFF000000 | (int(r * 0.6) << 16) | (int(g * 0.6) << 8) | int(b * 0.6)
        self.fill(x + 1, y + 1, x + 15, y + 15, 0xFF101010)
        self.fill(x + 2, y + 2, x + 14, y + 14, base)
        self.fill(x + 2, y + 2, x + 14, y + 5, lighter)
        self.fill(x + 11, y + 5, x + 14, y + 14, darker)

    def backdrop(self):
        """A rough stand-in for the world behind the wheel."""
        w, h = self.w, self.h
        for y in range(h):
            t = y / h
            if t < 0.55:
                c = (int(120 + 60 * t), int(170 + 40 * t), 235)
            else:
                c = (int(96 - 40 * (t - 0.55)), int(150 - 60 * (t - 0.55)), int(70 - 20 * (t - 0.55)))
            self.fill(0, y, w, y + 1, 0xFF000000 | (c[0] << 16) | (c[1] << 8) | c[2])
        for (bx, by, bw, bh, col) in ((0.08, 0.38, 0.16, 0.22, 0xFF7A7A7A), (0.70, 0.30, 0.22, 0.30, 0xFF8B6B45),
                                      (0.42, 0.44, 0.12, 0.14, 0xFF5E5E66)):
            self.fill(w * bx, h * by, w * (bx + bw), h * (by + bh), col)
            self.fill(w * bx, h * by, w * (bx + bw), h * by + 3, 0x40FFFFFF)

    def save(self, path):
        self.img.convert("RGB").save(path)


NOTHING, HUB, SAVE, BACK, HOME = -1, -2, -3, -4, -5
PER_PAGE = 8
GAP = 1.0
BUTTON_HEIGHT, BUTTON_MARGIN = 20, 8
HINT_MARGIN = 4
MIN_OUTER = 50


def distance_to(px, py, rect):
    """Whole pixels from a point to the nearest point of a rectangle (WheelScreen.distanceTo)."""
    x0, y0, x1, y1 = rect
    dx = max(x0 - px, 0, px - x1)
    dy = max(y0 - py, 0, py - y1)
    return int(math.floor(math.sqrt(dx * dx + dy * dy)))
COLOR_DIM = 0x50000000
COLOR_WEDGE, COLOR_WEDGE_HOVER, COLOR_HUB = 0xB8141418, 0xD8207068, 0xC80C0C10
COLOR_TEXT, COLOR_TEXT_SOFT = 0xFFFFFFFF, 0xFFB8C0C8
COLOR_WEDGE_LOCKED, COLOR_WEDGE_LOCKED_HOVER, COLOR_TEXT_LOCKED = 0x70141418, 0xB0502828, 0xFF707478
COLOR_POWER, COLOR_POWER_EMPTY, COLOR_NO_POWER, COLOR_NOT_LOADED = 0xFF1D9E75, 0x50FFFFFF, 0xFFE24B4A, 0xFFEF9F27
RING_THICKNESS = 3
TWO_LINE_MIN_RING = 44
POWER_FREE, POWER_ON, POWER_NONE, POWER_NOT_LOADED, POWER_BOTH = 0, 1, 2, 3, 4
HERE_OK, HERE_LIMITED, HERE_TOO_FAR, HERE_OTHER_DIMENSION = 0, 1, 2, 3
HINT_STATE = ["screen.jumper.wheel.no_power_hint", "screen.jumper.wheel.unloaded_hint",
              "screen.jumper.wheel.unloaded_no_power_hint"]
HINT_LIMITED = ["screen.jumper.wheel.limited_no_power_hint", "screen.jumper.wheel.limited_unloaded_hint",
                "screen.jumper.wheel.limited_unloaded_no_power_hint"]
HINT_LOCKED = ["screen.jumper.wheel.locked_no_power", "screen.jumper.wheel.locked_unloaded",
               "screen.jumper.wheel.locked_unloaded_no_power"]


class Wheel:
    """spots: list of (name, (r, g, b))."""

    def __init__(self, canvas, spots, slots, usable=None, power=POWER_FREE, energy=0, capacity=0, rate=0, here=HERE_OK,
                 far=(), has_back=True, page=0, hovered=NOTHING, click_mode=False, rng=2048, one_line_only=False):
        self.c = canvas
        self.width, self.height = canvas.w, canvas.h
        self.spots, self.slots = spots, slots
        self.usable = slots if usable is None else usable
        self.power, self.energy, self.capacity, self.rate = power, energy, capacity, rate
        self.here, self.far, self.has_back, self.page = here, far, has_back, page
        self.hovered, self.click_mode, self.range = hovered, click_mode, rng
        self.one_line_only = one_line_only
        self.pages = max(1, (len(spots) + PER_PAGE - 1) // PER_PAGE)
        # Bottom of the screen, from the bottom up: the hint line (alone on its row, so it gets the full width), then
        # the buttons in the corners. The ring takes most of the shorter side but never reaches either.
        self.hint_y = self.height - HINT_MARGIN - 8
        self.button_top = self.hint_y - 4 - BUTTON_HEIGHT
        self.save_left = BUTTON_MARGIN
        self.save_right = self.save_left + width(tr("screen.jumper.wheel.save")) + 16
        self.back_right = self.width - BUTTON_MARGIN
        self.back_left = self.back_right - width(tr("screen.jumper.wheel.back")) - 16
        self.home_right = self.back_left - 4
        self.home_left = self.home_right - width(tr("screen.jumper.wheel.home")) - 16
        self.edit_right = BUTTON_MARGIN + width(tr("screen.jumper.wheel.edit")) + 16
        cx, cy = self.width // 2, self.height // 2
        room = self.hint_y - 3 - cy
        for rect in ((self.save_left, self.button_top, self.save_right, self.button_top + BUTTON_HEIGHT),
                     (self.home_left, self.button_top, self.back_right, self.button_top + BUTTON_HEIGHT),
                     (BUTTON_MARGIN, BUTTON_MARGIN, self.edit_right, BUTTON_MARGIN + BUTTON_HEIGHT)):
            room = min(room, distance_to(cx, cy, rect) - 2)
        self.outer = max(MIN_OUTER, min(max(70, min(230, jround(min(self.width, self.height) * 0.42))), room))
        self.inner = max(30, jround(self.outer * 0.31))
        self.name_top, self.name_bottom = [None] * PER_PAGE, [None] * PER_PAGE
        self.layout_report = []

    # ---- state helpers
    def can_save(self):
        return self.here == HERE_OK and len(self.spots) < min(self.slots, self.usable)

    def is_far(self, index):
        return 0 <= index < len(self.far) and self.far[index]

    def locked(self, index):
        return self.here != HERE_OK or index >= self.usable or self.is_far(index)

    def out_of_range(self):
        return self.here in (HERE_TOO_FAR, HERE_OTHER_DIMENSION)

    def page_start(self):
        return self.page * PER_PAGE

    def page_count(self):
        return max(0, min(PER_PAGE, len(self.spots) - self.page_start()))

    # ---- shape
    def ring_at(self, dx, dy, count):
        d2 = dx * dx + dy * dy
        hub = self.inner - 2.0
        if d2 < hub * hub:
            return HUB
        if count == 0 or d2 < self.inner * self.inner or d2 > self.outer * self.outer:
            return NOTHING
        turn = (math.atan2(dy, dx) + math.pi / 2.0) / (2.0 * math.pi)
        position = (turn - math.floor(turn)) * count + 0.5
        whole = math.floor(position)
        if count > 1:
            part = position - whole
            to_edge = min(part, 1.0 - part) * (2.0 * math.pi / count) * math.sqrt(d2)
            if to_edge < GAP:
                return NOTHING
        return int(whole) % count

    def strips(self, count):
        found = []
        for y in range(-self.outer, self.outer):
            start, current = -self.outer, NOTHING
            for x in range(-self.outer, self.outer + 1):
                ident = NOTHING if x == self.outer else self.ring_at(x + 0.5, y + 0.5, count)
                if ident != current:
                    if current != NOTHING:
                        found.append((y, start, x, current))
                    start, current = x, ident
        return found

    # ---- names
    def fit(self, text, max_width):
        if width(text) <= max_width:
            return text
        return substr_by_width(text, max(0, max_width - width("..."))) + "..."

    def layout_name(self, slot, name, max_width, two_lines):
        self.name_bottom[slot] = None
        if not two_lines or width(name) <= max_width:
            self.name_top[slot] = self.fit(name, max_width)
            return
        best, best_difference = -1, 1 << 30
        i = name.find(' ')
        while i >= 0:
            first, second = name[:i].strip(), name[i + 1:].strip()
            if first and second:
                first_width = width(first)
                if first_width > max_width:
                    break
                difference = abs(first_width - width(second))
                if difference < best_difference:
                    best_difference, best = difference, i
            i = name.find(' ', i + 1)
        if best >= 0:
            first, second = name[:best].strip(), name[best + 1:].strip()
        elif name.find(' ') > 0:
            space = name.find(' ')
            first = self.fit(name[:space], max_width)
            second = name[space + 1:].strip()
        else:
            first = substr_by_width(name, max_width)
            second = name[len(first):]
        if not first or not second:
            self.name_top[slot] = self.fit(name, max_width)
            return
        self.name_top[slot] = first
        self.name_bottom[slot] = self.fit(second, max_width)

    # ---- drawing
    def render(self):
        c = self.c
        c.fill(0, 0, self.width, self.height, COLOR_DIM)
        cx, cy = self.width // 2, self.height // 2
        count = self.page_count()
        for (row, x0, x1, ident) in self.strips(count):
            if ident == HUB:
                color = COLOR_HUB
            elif self.locked(self.page_start() + ident):
                color = COLOR_WEDGE_LOCKED_HOVER if ident == self.hovered else COLOR_WEDGE_LOCKED
            else:
                color = COLOR_WEDGE_HOVER if ident == self.hovered else COLOR_WEDGE
            c.fill(cx + x0, cy + row, cx + x1, cy + row + 1, color)

        middle = self.inner + jround((self.outer - self.inner) * 0.6)
        label_width = 110 if count <= 1 else max(24, min(110, int(2.0 * middle * math.sin(math.pi / count)) - 10))
        self.label_width = label_width
        room_for_two = (self.outer - self.inner >= TWO_LINE_MIN_RING) and not self.one_line_only
        for i in range(count):
            self.layout_name(i, self.spots[self.page_start() + i][0], label_width, room_for_two)
        for i in range(count):
            name, rgb = self.spots[self.page_start() + i]
            angle = -math.pi / 2.0 + i * (2.0 * math.pi / count)
            x = cx + jround(math.cos(angle) * middle)
            y = cy + jround(math.sin(angle) * middle)
            two = self.name_bottom[i] is not None
            picture_top = y - 18 if two else y - 13
            c.item(x - 8, picture_top, rgb)
            is_locked = self.locked(self.page_start() + i)
            if is_locked:
                c.fill(x - 8, picture_top, x + 8, picture_top + 16, 0xA0000000)
            color = COLOR_TEXT_LOCKED if is_locked else COLOR_TEXT if i == self.hovered else COLOR_TEXT_SOFT
            if two:
                c.centered(self.name_top[i], x, y, color)
                c.centered(self.name_bottom[i], x, y + 10, color)
            else:
                c.centered(self.name_top[i], x, y + 5, color)
            self.layout_report.append((name, self.name_top[i], self.name_bottom[i]))
            # Does every corner of the drawn block stay inside this wedge?
            boxes = [(x - 8, picture_top, x + 8, picture_top + 16)]
            lines = [(self.name_top[i], y if two else y + 5)] + ([(self.name_bottom[i], y + 10)] if two else [])
            for text, ty in lines:
                w = width(text)
                boxes.append((x - w // 2, ty, x - w // 2 + w - 1, ty + 8))
            for (bx0, by0, bx1, by1) in boxes:
                for (px, py) in ((bx0, by0), (bx1, by0), (bx0, by1), (bx1, by1)):
                    if self.ring_at(px - cx + 0.0, py - cy + 0.0, count) != i:
                        self.layout_report.append(("  ! outside its wedge", name, (px - cx, py - cy)))
                        break

        self.draw_hub(cx, cy)
        if self.pages > 1:
            c.centered(tr("screen.jumper.wheel.page", self.page + 1, self.pages), cx,
                       max(4, cy - self.outer - 14), COLOR_TEXT)
        if self.can_save():
            self.button(self.save_left, self.save_right, tr("screen.jumper.wheel.save"), self.hovered == SAVE, False)
        if self.has_back:
            self.button(self.back_left, self.back_right, tr("screen.jumper.wheel.back"), self.hovered == BACK,
                        self.here != HERE_OK)
        self.button(self.home_left, self.home_right, tr("screen.jumper.wheel.home"), self.hovered == HOME,
                    self.out_of_range())
        self.hint_text = self.hint()
        self.hint_scale = c.shrunk(self.hint_text, cx, self.hint_y, self.width - 8, COLOR_TEXT)

    def hint(self):
        power, here, hov = self.power, self.here, self.hovered
        state = 2 if power == POWER_BOTH else 1 if power == POWER_NOT_LOADED else 0
        if here == HERE_OTHER_DIMENSION:
            return tr("screen.jumper.wheel.other_dimension_hint")
        if here == HERE_TOO_FAR:
            return tr("screen.jumper.wheel.too_far_hint", self.range)
        if hov == HOME:
            return tr("screen.jumper.wheel.home_hint")
        if here == HERE_LIMITED:
            return tr(HINT_LIMITED[state], 64)
        if hov >= 0:
            index = self.page_start() + hov
            name = self.spots[index][0]
            if index >= self.usable:
                return tr(HINT_LOCKED[state], name)
            if self.is_far(index):
                return tr("screen.jumper.wheel.spot_far", name)
            return tr("screen.jumper.wheel.jump_click" if self.click_mode else "screen.jumper.wheel.jump", name)
        if hov == SAVE:
            return tr("screen.jumper.wheel.save_hint")
        if hov == BACK:
            return tr("screen.jumper.wheel.back_hint")
        if power in (POWER_NONE, POWER_NOT_LOADED, POWER_BOTH):
            return tr(HINT_STATE[state])
        if not self.spots:
            return tr("screen.jumper.wheel.empty")
        if self.pages > 1:
            return tr("screen.jumper.wheel.pages_hint")
        return tr("screen.jumper.wheel.click_mode" if self.click_mode else "screen.jumper.wheel.idle")

    def draw_hub(self, cx, cy):
        used = "%d / %d" % (len(self.spots), self.slots)
        power = self.power
        needs_power = power != POWER_FREE
        unloaded = power in (POWER_NOT_LOADED, POWER_BOTH)
        empty = power in (POWER_NONE, POWER_BOTH)
        charge = 0 if self.capacity <= 0 else int(max(0, min(1000, int(self.energy * 1000.0 / self.capacity))))
        if needs_power:
            if empty:
                self.ring(cx, cy, 1000, COLOR_NO_POWER)
            else:
                self.ring(cx, cy, charge, COLOR_NOT_LOADED if unloaded else COLOR_POWER)
        radius = self.inner - 2 - (RING_THICKNESS if needs_power else 0) - 3
        percent = "%d%%" % (charge // 10)
        if self.out_of_range():
            self.hub_lines(cx, cy, radius, tr("screen.jumper.wheel.out_of_range"), COLOR_NO_POWER, None,
                           COLOR_TEXT_SOFT, used)
        elif unloaded:
            self.hub_lines(cx, cy, radius, tr("screen.jumper.wheel.unloaded"), COLOR_NOT_LOADED,
                           tr("screen.jumper.wheel.no_power") if empty else percent,
                           COLOR_NO_POWER if empty else COLOR_TEXT_SOFT, used)
        elif empty:
            self.hub_lines(cx, cy, radius, tr("screen.jumper.wheel.no_power"), COLOR_NO_POWER, None, COLOR_TEXT_SOFT,
                           used)
        elif needs_power:
            self.hub_lines(cx, cy, radius, percent, COLOR_TEXT,
                           None if self.rate <= 0 else duration(self.energy // self.rate), COLOR_TEXT_SOFT, used)
        else:
            self.hub_lines(cx, cy, radius, used, COLOR_TEXT, None, COLOR_TEXT_SOFT, None)

    def hub_lines(self, cx, cy, radius, top, top_color, middle, middle_color, bottom):
        lines = 1 + (0 if middle is None else 1) + (0 if bottom is None else 1)
        y = cy - (lines * 10 - 2) // 2
        self.hub_scales = [self.fitted(top, cx, cy, y, radius, top_color)]
        if middle is not None:
            y += 10
            self.hub_scales.append(self.fitted(middle, cx, cy, y, radius, middle_color))
        if bottom is not None:
            y += 10
            self.hub_scales.append(self.fitted(bottom, cx, cy, y, radius, COLOR_TEXT_SOFT))

    def fitted(self, text, cx, cy, y, radius, color):
        away = max(abs(y - cy), abs(y + 8 - cy))
        room = 8 if away >= radius else int(2.0 * math.sqrt(radius * radius - away * away))
        return (text, room, round(self.c.shrunk(text, cx, y, room, color), 2))

    def ring(self, cx, cy, filled, color):
        r_in, r_out = self.inner - 2 - RING_THICKNESS, self.inner - 2
        for y in range(-r_out, r_out):
            for x in range(-r_out, r_out):
                px, py = x + 0.5, y + 0.5
                d2 = px * px + py * py
                if d2 < r_in * r_in or d2 >= r_out * r_out:
                    continue
                turn = (math.atan2(py, px) + math.pi / 2.0) / (2.0 * math.pi)
                thousandth = int((turn - math.floor(turn)) * 1000.0)
                self.c.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, color if thousandth < filled else COLOR_POWER_EMPTY)

    def button(self, left, right, label, hovered, dim):
        if dim:
            fill = COLOR_WEDGE_LOCKED_HOVER if hovered else COLOR_WEDGE_LOCKED
        else:
            fill = COLOR_WEDGE_HOVER if hovered else COLOR_WEDGE
        self.c.fill(left, self.button_top, right, self.button_top + BUTTON_HEIGHT, fill)
        self.c.centered(label, (left + right) // 2, self.button_top + 6,
                        COLOR_TEXT_LOCKED if dim else COLOR_TEXT if hovered else COLOR_TEXT_SOFT)


def duration(ticks):
    seconds = ticks // 20
    if seconds >= 3600:
        return "%d h %d min" % (seconds // 3600, (seconds % 3600) // 60)
    if seconds >= 60:
        return "%d min" % (seconds // 60)
    return "%d s" % seconds


def sheet(panels, columns, path, title_height=26, pad=14, background=(24, 24, 28)):
    """panels: list of (title, Canvas). Lays them out in a grid with a title above each."""
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 17)
    pw, ph = panels[0][1].img.size
    rows = (len(panels) + columns - 1) // columns
    out = Image.new("RGB", (columns * (pw + pad) + pad, rows * (ph + title_height + pad) + pad), background)
    draw = ImageDraw.Draw(out)
    for n, (title, canvas) in enumerate(panels):
        x = pad + (n % columns) * (pw + pad)
        y = pad + (n // columns) * (ph + title_height + pad)
        draw.text((x + 2, y + 2), title, font=font, fill=(235, 235, 235))
        out.paste(canvas.img.convert("RGB"), (x, y + title_height))
    out.save(path)
    return out.size
