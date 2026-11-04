#!/usr/bin/env python3
"""从品牌标生成 icon.png / icon.ico（青绿圆角方 + 「衡」）。"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1] / "resources"
START = (0x5E, 0xEA, 0xD4)  # --accent-2
END = (0x2D, 0xD4, 0xBF)  # --accent
INK = (0x04, 0x2F, 0x2E)
FONT = Path("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc")


def lerp(a: int, b: int, t: float) -> int:
    return int(a + (b - a) * t)


def paint_mark(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    radius = int(size * 14 / 44)
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            img.putpixel(
                (x, y),
                (lerp(START[0], END[0], t), lerp(START[1], END[1], t), lerp(START[2], END[2], t), 255),
            )
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    img.putalpha(mask)

    draw = ImageDraw.Draw(img)
    font = ImageFont.truetype(str(FONT), size=int(size * 0.52), index=0)
    text = "衡"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    x = (size - tw) / 2 - bbox[0]
    y = (size - th) / 2 - bbox[1] + size * 0.02
    # 微米黑没有 800 字重，描边加一层让小尺寸仍清楚
    stroke = max(1, size // 48)
    draw.text((x, y), text, font=font, fill=INK, stroke_width=stroke, stroke_fill=INK)
    return img


def main() -> None:
    ROOT.mkdir(parents=True, exist_ok=True)
    master = paint_mark(1024).filter(ImageFilter.UnsharpMask(radius=1.2, percent=80, threshold=2))
    png = ROOT / "icon.png"
    master.resize((512, 512), Image.Resampling.LANCZOS).save(png, "PNG")
    sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    master.save(ROOT / "icon.ico", format="ICO", sizes=sizes)
    ico = Image.open(ROOT / "icon.ico")
    print(f"wrote {png} and {ROOT / 'icon.ico'} sizes={ico.info.get('sizes')}")


if __name__ == "__main__":
    main()
