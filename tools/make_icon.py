"""Rasterise the original geometric printer mark; no third-party branding."""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1]
image = Image.new("RGB", (1024, 1024), "#0b1112")
draw = ImageDraw.Draw(image)
green = "#9ee6b3"


def line(points):
    scaled = [(x * 2, y * 2) for x, y in points]
    draw.line(scaled, fill=green, width=32, joint="curve")
    for x, y in scaled:
        draw.ellipse((x - 16, y - 16, x + 16, y + 16), fill=green)


line([(120, 372), (120, 126), (392, 126), (392, 372)])
line([(144, 348), (368, 348)])
line([(156, 126), (156, 196), (356, 196), (356, 126)])
line([(256, 196), (256, 244)])
line([(240, 244), (272, 244), (256, 268)])
line([(184, 324), (256, 280), (328, 324), (256, 364), (184, 324)])
draw.ellipse((712, 296, 744, 328), fill="#edf5f1")
image = image.resize((512, 512), Image.Resampling.LANCZOS)
image.save(root / "icon.png", optimize=True)
(root / "res/drawable").mkdir(parents=True, exist_ok=True)
image.save(root / "res/drawable/icon.png", optimize=True)
