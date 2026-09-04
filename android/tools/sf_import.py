"""Imports SF Symbol PNGs into android/app/src/main/res/drawable-nodpi.

The glyph dump renders every symbol at one point size and crops to ink bounds,
so heights differ (person.fill is 34px, house.fill is 39px) and widths differ a
lot more (person.3.fill is 71px). iOS does not squeeze a symbol into a square:
it fixes the height and lets the width run.

Every glyph is centred on a canvas that is CANVAS_H tall, so one Icon height
gives the whole set iOS's shared scale. The canvas is only widened past
CANVAS_H for the handful of glyphs that need it, which keeps every other icon
square: a square canvas drops straight into an existing Modifier.size(N.dp)
with no layout change, while a wide one needs the call site to carry its
aspect or Icon's ContentScale.Fit shrinks the ink to fit the width.

Usage: python tools/sf_import.py house.fill calendar person.3.fill
"""

import sys
from pathlib import Path

from PIL import Image

GLYPHS = Path(r"Z:\AlcoholtrackerApple\sf-symbols-online-master\sf-symbols-online-master\glyphs")
OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"

# Tallest glyph in use is 43px. Fixed rather than measured per run so importing
# one more symbol cannot silently rescale every icon already on screen.
CANVAS_H = 48


def resource_name(symbol: str) -> str:
    return "sf_" + symbol.replace(".", "_")


def convert(symbol: str) -> tuple[str, int]:
    src = GLYPHS / f"{symbol}.png"
    if not src.is_file():
        raise SystemExit(f"no glyph for {symbol}")

    glyph = Image.open(src).convert("RGBA")
    if glyph.height > CANVAS_H:
        raise SystemExit(f"{symbol} is {glyph.height}px tall, over the canvas")

    width = max(CANVAS_H, glyph.width)
    canvas = Image.new("RGBA", (width, CANVAS_H), (0, 0, 0, 0))
    canvas.paste(glyph, ((width - glyph.width) // 2, (CANVAS_H - glyph.height) // 2))

    OUT.mkdir(parents=True, exist_ok=True)
    name = resource_name(symbol)
    canvas.save(OUT / f"{name}.png")
    return name, width


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    for arg in sys.argv[1:]:
        name, width = convert(arg)
        print(f"{name} {width}x{CANVAS_H}")
