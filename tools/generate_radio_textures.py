"""Generate the small, deliberately pixel-aligned Minecraft cabinet and item textures.

Run with: python3 tools/generate_radio_textures.py
No third-party image library is required.
"""

from pathlib import Path
import struct
import zlib


ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/radio/textures"
BLOCKS = ROOT / "block"
ITEMS = ROOT / "item"


def rgb(value):
    return tuple(int(value[index:index + 2], 16) for index in (0, 2, 4)) + (255,)


class Canvas:
    def __init__(self, size=32):
        self.size = size
        self.pixels = [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]

    def rect(self, x0, y0, x1, y1, color):
        color = rgb(color)
        for y in range(max(0, y0), min(self.size, y1)):
            for x in range(max(0, x0), min(self.size, x1)):
                self.pixels[y][x] = color

    def dot(self, x, y, color):
        self.rect(x, y, x + 1, y + 1, color)

    def frame(self, x0, y0, x1, y1, edge, fill, highlight=None):
        self.rect(x0, y0, x1, y1, edge)
        self.rect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill)
        if highlight:
            self.rect(x0 + 1, y0 + 1, x1 - 1, y0 + 2, highlight)
            self.rect(x0 + 1, y0 + 1, x0 + 2, y1 - 1, highlight)

    def label(self, text, x, y, color):
        glyphs = {
            "A": ("010", "101", "111", "101", "101"),
            "B": ("110", "101", "110", "101", "110"),
            "D": ("110", "101", "101", "101", "110"),
            "F": ("111", "100", "110", "100", "100"),
            "M": ("101", "111", "111", "101", "101"),
            "U": ("101", "101", "101", "101", "111"),
            "X": ("101", "101", "010", "101", "101"),
        }
        for char in text:
            for row, bits in enumerate(glyphs[char]):
                for column, bit in enumerate(bits):
                    if bit == "1":
                        self.dot(x + column, y + row, color)
            x += 4

    def save(self, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        raw = b"".join(b"\x00" + b"".join(bytes(pixel) for pixel in row) for row in self.pixels)

        def chunk(kind, data):
            return (struct.pack(">I", len(data)) + kind + data
                    + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))

        data = (b"\x89PNG\r\n\x1a\n"
                + chunk(b"IHDR", struct.pack(">2I5B", self.size, self.size, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
        path.write_bytes(data)


FM = {"edge": "333D47", "case": "9CA9B1", "light": "C9D2D4", "shadow": "687780",
      "panel": "7D8D96", "dark": "263840", "screen": "12313B", "accent": "6BE2DF",
      "lamp": "D94643"}
DAB = {"edge": "1D303C", "case": "4A6170", "light": "74909A", "shadow": "304550",
       "panel": "395563", "dark": "162F3A", "screen": "102F39", "accent": "63E0CF",
       "lamp": "4AC7A9"}
SERVER = {"edge": "17242E", "case": "3C4C56", "light": "657987", "shadow": "293943",
          "panel": "30424D", "dark": "112732", "screen": "0D2A33", "accent": "63CDE8",
          "lamp": "60D8AE"}
RECEIVER = {"edge": "172833", "case": "405864", "light": "7595A0", "shadow": "2B414C",
            "panel": "344B58", "dark": "132933", "screen": "103540", "accent": "69D8D2",
            "lamp": "75E9AE"}


def cabinet_base(c, palette):
    c.frame(0, 0, 32, 32, palette["edge"], palette["case"], palette["light"])
    c.rect(2, 2, 30, 3, palette["light"])
    c.rect(2, 29, 30, 30, palette["shadow"])
    for x in (2, 29):
        for y in (2, 29):
            c.dot(x, y, palette["light"])


def vent(c, x0, y0, x1, y1, palette, horizontal=True):
    c.frame(x0, y0, x1, y1, palette["shadow"], palette["panel"])
    if horizontal:
        for y in range(y0 + 2, y1 - 1, 3):
            c.rect(x0 + 2, y, x1 - 2, y + 1, palette["dark"])
            c.rect(x0 + 2, y + 1, x1 - 2, y + 2, palette["light"])
    else:
        for x in range(x0 + 2, x1 - 1, 3):
            c.rect(x, y0 + 2, x + 1, y1 - 2, palette["dark"])
            c.rect(x + 1, y0 + 2, x + 2, y1 - 2, palette["light"])


def fm_front():
    c = Canvas(); cabinet_base(c, FM)
    vent(c, 3, 4, 29, 10, FM)
    c.frame(3, 12, 18, 22, FM["dark"], FM["screen"], FM["shadow"])
    c.label("FM", 6, 14, FM["accent"])
    for x, height in ((13, 2), (15, 4)):
        c.rect(x, 19 - height, x + 1, 19, FM["accent"])
    c.frame(20, 12, 28, 22, FM["shadow"], FM["panel"], FM["light"])
    c.rect(22, 15, 26, 19, FM["dark"])
    c.rect(23, 14, 25, 15, FM["light"])
    c.rect(25, 24, 28, 27, FM["dark"]); c.dot(26, 25, FM["lamp"])
    vent(c, 3, 23, 22, 28, FM)
    return c


def dab_front():
    c = Canvas(); cabinet_base(c, DAB)
    c.frame(3, 4, 29, 14, DAB["dark"], DAB["screen"], DAB["light"])
    c.label("DAB", 6, 6, DAB["accent"])
    for x, height in ((20, 2), (22, 4), (24, 6)):
        c.rect(x, 12 - height, x + 2, 12, DAB["accent"])
    vent(c, 3, 17, 29, 26, DAB)
    c.rect(4, 28, 23, 29, DAB["shadow"])
    for x in (25, 27): c.dot(x, 28, DAB["lamp"])
    return c


def server_front():
    c = Canvas(); cabinet_base(c, SERVER)
    c.frame(3, 4, 29, 11, SERVER["dark"], SERVER["screen"], SERVER["light"])
    c.label("MUX", 6, 5, SERVER["accent"])
    for y in (13, 18, 23):
        c.frame(3, y, 29, y + 4, SERVER["dark"], SERVER["panel"], SERVER["light"])
        c.rect(6, y + 1, 19, y + 2, SERVER["shadow"])
        c.rect(6, y + 2, 19, y + 3, SERVER["dark"])
        c.dot(24, y + 1, SERVER["lamp"])
    vent(c, 3, 28, 29, 31, SERVER)
    return c


def receiver_front():
    c = Canvas(); cabinet_base(c, RECEIVER)
    # Left speaker and right tuning module read as one receiver/speaker cabinet.
    c.frame(3, 4, 17, 27, RECEIVER["dark"], RECEIVER["panel"], RECEIVER["light"])
    for y in range(7, 25, 2):
        for x in range(5, 15, 2):
            c.dot(x, y, RECEIVER["dark"])
    c.frame(19, 5, 29, 13, RECEIVER["dark"], RECEIVER["screen"], RECEIVER["light"])
    c.label("DAB", 20, 6, RECEIVER["accent"])
    c.rect(20, 15, 28, 17, RECEIVER["dark"])
    c.rect(20, 18, 28, 20, RECEIVER["shadow"])
    c.frame(21, 22, 27, 28, RECEIVER["dark"], RECEIVER["shadow"], RECEIVER["light"])
    c.rect(23, 23, 25, 25, RECEIVER["accent"])
    c.dot(28, 23, RECEIVER["lamp"])
    return c


def side(palette, kind):
    c = Canvas(); cabinet_base(c, palette)
    c.rect(4, 5, 28, 6, palette["shadow"])
    c.rect(4, 25, 28, 26, palette["light"])
    vent(c, 5, 8, 27, 23, palette, horizontal=kind != "server")
    for y in (4, 27):
        c.dot(4, y, palette["light"]); c.dot(27, y, palette["light"])
    return c


def top(palette):
    c = Canvas(); cabinet_base(c, palette)
    vent(c, 5, 7, 27, 25, palette, horizontal=False)
    c.rect(5, 4, 27, 5, palette["shadow"])
    c.rect(5, 27, 27, 28, palette["light"])
    return c


def simple_radio():
    c = Canvas()
    # A clear portable silhouette with a tall telescopic antenna.
    c.rect(9, 1, 10, 8, "AAB8BD")
    c.rect(10, 0, 11, 3, "D5E2E1")
    c.rect(5, 7, 27, 29, "25343C")
    c.rect(6, 8, 26, 28, "899BA4")
    c.rect(7, 9, 25, 10, "D5DFDF")
    c.frame(8, 11, 24, 17, "263D46", "1C454D", "A7D5D2")
    c.label("FM", 10, 11, "8DE5DA")
    for y in range(19, 25, 2):
        for x in range(9, 21, 2): c.dot(x, y, "30464F")
    c.rect(22, 20, 25, 23, "263D46")
    c.dot(23, 21, "E54E4A")
    c.rect(9, 27, 23, 28, "5C727B")
    c.rect(5, 28, 27, 30, "25343C")
    return c


def linking_tool():
    c = Canvas()
    # A handheld linking probe, with a bright contact and two connected nodes.
    c.rect(5, 21, 12, 29, "1D303C")
    c.rect(6, 22, 11, 28, "3E6576")
    c.rect(7, 22, 9, 27, "78B3BC")
    for x, y in ((10, 19), (12, 17), (14, 15), (16, 13), (18, 11)):
        c.rect(x - 1, y - 1, x + 4, y + 4, "1D303C")
        c.rect(x, y, x + 3, y + 3, "8298A0")
        c.dot(x, y, "D5E2E1")
    c.rect(20, 7, 24, 11, "193944")
    c.rect(21, 8, 23, 10, "7BE6DA")
    c.rect(22, 4, 25, 7, "1D303C")
    c.rect(23, 5, 25, 7, "A1F4E7")
    c.rect(25, 11, 28, 14, "1D303C")
    c.rect(26, 12, 28, 14, "A1F4E7")
    c.rect(24, 8, 26, 10, "4DBBAD")
    c.rect(24, 10, 26, 12, "4DBBAD")
    return c


for name, palette, front in (("fm_transmitter", FM, fm_front),
                             ("dab_transmitter", DAB, dab_front),
                             ("dab_server", SERVER, server_front),
                             ("dx3_receiver", RECEIVER, receiver_front)):
    front().save(BLOCKS / f"{name}_front.png")
    side(palette, "server" if name == "dab_server" else "transmitter").save(BLOCKS / f"{name}_side.png")
    top(palette).save(BLOCKS / f"{name}_top.png")

simple_radio().save(ITEMS / "radio.png")
linking_tool().save(ITEMS / "linking_tool.png")
