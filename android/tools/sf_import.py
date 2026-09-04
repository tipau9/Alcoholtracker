"""Imports SF Symbol PNGs into android/app/src/main/res/drawable-nodpi.

The glyph dump renders every symbol at one point size and crops to ink bounds,
so heights differ (person.fill is 34px, house.fill is 39px) and widths differ a
lot more (person.3.fill is 71px). iOS does not squeeze a symbol into a square:
it fixes the height and lets the width run. To reproduce that, every glyph is
centred on one canvas shared by the whole set, so a single Icon box drawn at
CANVAS_W:CANVAS_H keeps every symbol's relative scale and its real aspect.

Usage: python tools/sf_import.py house.fill calendar person.3.fill
"""

import sys
from pathlib import Path

from PIL import Image

GLYPHS = Path(r"Z:\AlcoholtrackerApple\sf-symbols-online-master\sf-symbols-online-master\glyphs")
OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"

# The widest glyph in use is person.3.fill at 71px and the tallest is ~44px. The
# canvas is fixed rather than measured per run so importing one more symbol
# cannot silently rescale every icon already on screen. Keep SF_ICON_ASPECT in
# PromilleNavigation.kt in step with CANVAS_W / CANVAS_H.
CANVAS_W = 72
CANVAS_H = 48


def resource_name(symbol: str) -> str:
    return "sf_" + symbol.replace(".", "_")


def convert(symbol: str) -> str:
    src = GLYPHS / f"{symbol}.png"
    if not src.is_file():
        raise SystemExit(f"no glyph for {symbol}")

    glyph = Image.open(src).convert("RGBA")
    if glyph.width > CANVAS_W or glyph.height > CANVAS_H:
        raise SystemExit(f"{symbol} is {glyph.width}x{glyph.height}, over the canvas")

    canvas = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    canvas.paste(glyph, ((CANVAS_W - glyph.width) // 2, (CANVAS_H - glyph.height) // 2))

    OUT.mkdir(parents=True, exist_ok=True)
    name = resource_name(symbol)
    canvas.save(OUT / f"{name}.png")
    return name


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    for arg in sys.argv[1:]:
        print(convert(arg))
