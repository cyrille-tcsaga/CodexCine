#!/usr/bin/env python3
"""Generates the 320x180 Android TV banner (requires Pillow: pip install pillow)."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 320, 180
BACKGROUND = "#0B0D1A"
ACCENT = "#F5A623"
TEXT = "Codex Ciné"
OUTPUT = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-xhdpi/banner.png"
FONT_CANDIDATES = [
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "/System/Library/Fonts/Helvetica.ttc",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
]


def load_font(size: int) -> ImageFont.FreeTypeFont:
    for candidate in FONT_CANDIDATES:
        if Path(candidate).exists():
            return ImageFont.truetype(candidate, size)
    raise SystemExit("No bold font found; add one to FONT_CANDIDATES.")


def main() -> None:
    image = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
    draw = ImageDraw.Draw(image)
    font = load_font(44)

    left, top, right, bottom = draw.textbbox((0, 0), TEXT, font=font)
    text_width, text_height = right - left, bottom - top
    x = (WIDTH - text_width) / 2 - left
    y = (HEIGHT - text_height) / 2 - top - 6
    draw.text((x, y), TEXT, font=font, fill="#FFFFFF")

    line_y = y + top + text_height + 16
    draw.rectangle(
        [(WIDTH - text_width) / 2, line_y, (WIDTH + text_width) / 2, line_y + 2],
        fill=ACCENT,
    )

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT, optimize=True)
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    main()
