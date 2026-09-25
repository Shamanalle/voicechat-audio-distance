# Pixel-art mod icon on a 32x32 grid: a speaker, sound waves, a stone-brick wall and faint waves behind it.
# Usage: python3 docs/make-icon.py icon.png common/src/main/resources/assets/vc-audio-distance/icon.png (needs Pillow).
# Writes the 512px store icon and the 128px icon packed into the mod.
import math, sys
from PIL import Image

N = 32
BG, BORDER = (31, 35, 39), (58, 64, 70)
CYAN, CYAN_DARK = (25, 179, 204), (14, 120, 140)
WAVE, FAINT = (240, 244, 246), (92, 108, 116)
BRICK, BRICK_LIGHT, MORTAR = (128, 134, 140), (150, 156, 162), (74, 79, 85)

img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
px = img.load()

# Rounded square with a 1px border
def inside(x, y, inset):
    r = 5 - inset
    lo, hi = inset, N - 1 - inset
    if not (lo <= x <= hi and lo <= y <= hi):
        return False
    cx = min(max(x, lo + r), hi - r)
    cy = min(max(y, lo + r), hi - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 1
for y in range(N):
    for x in range(N):
        if inside(x, y, 0):
            px[x, y] = BORDER + (255,)
        if inside(x, y, 1):
            px[x, y] = BG + (255,)

def put(x, y, c):
    px[x, y] = c + (255,)

# Speaker: magnet and cone
for x in range(4, 7):
    for y in range(13, 19):
        put(x, y, CYAN)
for i, x in enumerate(range(7, 11)):
    for y in range(12 - i, 20 + i):
        put(x, y, CYAN)
for y in range(8, 24):
    put(11, y, CYAN_DARK)

# Sound waves: three hand-placed pixel arcs, each a column with bent ends
def wave(x, top, bottom, bends):
    for y in range(top, bottom + 1):
        put(x, y, WAVE)
    for i, (above, below) in enumerate(bends, start=1):
        for y in above:
            put(x - i, y, WAVE)
        for y in below:
            put(x - i, y, WAVE)
wave(14, 14, 17, [((13,), (18,))])
wave(17, 12, 19, [((10, 11), (20, 21)), ((9,), (22,))])
wave(20, 11, 20, [((8, 9, 10), (21, 22, 23)), ((7,), (24,))])

# Stone-brick wall, rows of 2px bricks and 1px mortar, joints offset every other row
x0, x1, y0, y1 = 23, 27, 5, 26
for y in range(y0, y1 + 1):
    row, sub = divmod(y - y0, 3)
    for x in range(x0, x1 + 1):
        if sub == 2:
            c = MORTAR
        else:
            joint = x0 + (2 if row % 2 == 0 else 4)
            c = MORTAR if x == joint else (BRICK_LIGHT if sub == 0 else BRICK)
        put(x, y, c)

# Muffled wave behind the wall: short and dim
for y in range(13, 19):
    put(29, y, FAINT)

for size, out in ((512, sys.argv[1]), (128, sys.argv[2])):
    img.resize((size, size), Image.NEAREST).save(out, "PNG", optimize=True)
