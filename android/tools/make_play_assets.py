#!/usr/bin/env python3
"""Render Play listing art from the app's existing speaker mark."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs" / "play" / "assets"
OUT.mkdir(parents=True, exist_ok=True)

BG = (20, 16, 31)
CARD = (38, 31, 54)
MAGENTA = (234, 71, 202)
GREEN = (107, 239, 163)
RED = (255, 91, 104)
WHITE = (250, 247, 255)


def speaker(draw: ImageDraw.ImageDraw, cx: int, cy: int, scale: float) -> None:
    def pt(x: float, y: float) -> tuple[int, int]:
        return round(cx + x * scale), round(cy + y * scale)

    draw.ellipse((*pt(-46, -46), *pt(46, 46)), fill=MAGENTA)
    draw.ellipse((*pt(-39, -39), *pt(39, 39)), fill=BG)
    draw.polygon([pt(-33, -9), pt(-22, -9), pt(-8, -21),
                  pt(-8, 21), pt(-22, 9), pt(-33, 9)], fill=GREEN)
    draw.arc((*pt(-3, -18), *pt(25, 18)), -65, 65,
             fill=RED, width=max(2, round(6 * scale)))
    draw.arc((*pt(5, -31), *pt(40, 31)), -65, 65,
             fill=MAGENTA, width=max(2, round(6 * scale)))


def font(size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", size)


icon = Image.new("RGB", (1024, 1024), BG)
speaker(ImageDraw.Draw(icon), 512, 512, 9.7)
icon.resize((512, 512), Image.LANCZOS).save(OUT / "play-icon-512.png")

w, h = 2048, 1000
feature = Image.new("RGB", (w, h))
pixels = feature.load()
for y in range(h):
    for x in range(w):
        t = (x / w + y / h) / 2
        pixels[x, y] = (round(BG[0] + 17 * t), round(BG[1] + 8 * t),
                        round(BG[2] + 26 * t))
draw = ImageDraw.Draw(feature)
draw.rounded_rectangle((86, 106, 746, 894), radius=100, fill=CARD)
speaker(draw, 416, 500, 5.6)
draw.text((832, 302), "AudioBridge", font=font(132), fill=WHITE)
draw.text((840, 503), "Stereo audio on your Wi-Fi", font=font(51), fill=GREEN)
draw.rounded_rectangle((840, 629, 1435, 642), radius=6, fill=RED)
draw.rounded_rectangle((1455, 629, 1810, 642), radius=6, fill=MAGENTA)
feature.resize((1024, 500), Image.LANCZOS).save(OUT / "feature-graphic-1024x500.png")
print(OUT)
