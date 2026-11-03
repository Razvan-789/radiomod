"""Generate the handheld spectrum analyser's 32x32 pixel item texture."""
from pathlib import Path
import struct
import zlib

SIZE = 32
pixels = [[(0, 0, 0, 0) for _ in range(SIZE)] for _ in range(SIZE)]


def rect(x0, y0, x1, y1, color):
    rgba = tuple(bytes.fromhex(color)) + (255,)
    for y in range(y0, y1):
        for x in range(x0, x1):
            if 0 <= x < SIZE and 0 <= y < SIZE:
                pixels[y][x] = rgba


rect(3, 2, 29, 31, "202229")
rect(4, 3, 28, 30, "44464C")
rect(6, 5, 26, 20, "080C18")
rect(7, 6, 25, 12, "0C2551")
rect(7, 13, 25, 19, "07172D")
for x, top in enumerate((11, 11, 10, 8, 9, 11, 7, 6, 8, 10, 9, 6, 7, 9, 11, 10, 11, 11), 7):
    rect(x, top, x + 1, 12, "83D9FF")
    rect(x, top, x + 1, top + 1, "F2FAFF")
    rect(x, 13, x + 1, 19, "EC4716" if top <= 8 else "185DCE")
rect(15, 5, 16, 20, "E5BC31")
rect(7, 22, 13, 25, "15181F")
rect(14, 22, 20, 25, "15181F")
rect(21, 22, 26, 25, "15181F")
rect(7, 27, 25, 29, "12151B")
rect(22, 3, 26, 4, "B2B5B7")


def chunk(kind, data):
    return (struct.pack(">I", len(data)) + kind + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF))


raw = b"".join(b"\0" + b"".join(bytes(pixel) for pixel in row) for row in pixels)
png = (b"\x89PNG\r\n\x1a\n"
       + chunk(b"IHDR", struct.pack(">2I5B", SIZE, SIZE, 8, 6, 0, 0, 0))
       + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
target = (Path(__file__).resolve().parents[1]
          / "src/main/resources/assets/radio/textures/item/spectrum_analyser.png")
target.write_bytes(png)
