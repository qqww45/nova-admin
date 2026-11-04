#!/usr/bin/env python3
"""从 resources/icon.svg 生成 icon.png（512）与多尺寸 icon.ico。"""

from __future__ import annotations

import io
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "resources"
SVG = ROOT / "icon.svg"

START = (0x3E, 0x36, 0xC0, 255)
END = (0x00, 0xB2, 0xB6, 255)
WHITE = (255, 255, 255, 255)
RX = 224


def lerp(a: int, b: int, t: float) -> int:
    return int(a + (b - a) * t)


def render_via_cairosvg(size: int):
    import cairosvg
    from PIL import Image

    raw = cairosvg.svg2png(url=str(SVG), output_width=size, output_height=size)
    return Image.open(io.BytesIO(raw)).convert("RGBA")


def paint_mark(size: int):
    """无 cairosvg 时按同一几何回退绘制（渐变圆角方 + 白天平）。"""
    from PIL import Image, ImageDraw

    s = size / 1024
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            img.putpixel(
                (x, y),
                (
                    lerp(START[0], END[0], t),
                    lerp(START[1], END[1], t),
                    lerp(START[2], END[2], t),
                    255,
                ),
            )
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size - 1, size - 1), radius=max(1, RX * s), fill=255)
    img.putalpha(mask)
    draw = ImageDraw.Draw(img)

    def xy(x, y):
        return (x * s, y * s)

    def box(x, y, w, h):
        return (x * s, y * s, (x + w) * s, (y + h) * s)

    draw.ellipse(box(484, 198, 56, 56), fill=WHITE)
    draw.rounded_rectangle(box(501, 222, 22, 62), radius=max(1, 11 * s), fill=WHITE)
    draw.rounded_rectangle(box(456, 280, 112, 28), radius=max(1, 14 * s), fill=WHITE)
    draw.rounded_rectangle(box(260, 336, 504, 28), radius=max(1, 14 * s), fill=WHITE)
    draw.rounded_rectangle(box(482, 352, 60, 420), radius=max(1, 4 * s), fill=WHITE)
    draw.rounded_rectangle(box(392, 772, 240, 46), radius=max(1, 23 * s), fill=WHITE)
    draw.pieslice(box(174, 475, 216, 226), start=0, end=180, fill=WHITE)
    draw.pieslice(box(634, 475, 216, 226), start=0, end=180, fill=WHITE)
    hook_w = max(1, round(16 * s))
    draw.arc(box(248, 350, 72, 66), start=0, end=180, fill=WHITE, width=hook_w)
    draw.arc(box(704, 350, 72, 66), start=0, end=180, fill=WHITE, width=hook_w)
    str_w = max(1, round(12 * s))
    draw.line([xy(266, 406), xy(190, 610)], fill=WHITE, width=str_w)
    draw.line([xy(302, 406), xy(374, 610)], fill=WHITE, width=str_w)
    draw.line([xy(722, 406), xy(650, 610)], fill=WHITE, width=str_w)
    draw.line([xy(758, 406), xy(834, 610)], fill=WHITE, width=str_w)
    return img


def render_master(size: int = 1024):
    try:
        return render_via_cairosvg(size)
    except Exception as exc:
        print(f"cairosvg unavailable, PIL fallback: {exc}")
        return paint_mark(size)


def main() -> None:
    from PIL import Image

    ROOT.mkdir(parents=True, exist_ok=True)
    if not SVG.is_file():
        raise SystemExit(f"missing {SVG}")

    master = render_master(1024)
    png = ROOT / "icon.png"
    master.resize((512, 512), Image.Resampling.LANCZOS).save(png, "PNG")
    sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    master.save(ROOT / "icon.ico", format="ICO", sizes=sizes)
    ico = Image.open(ROOT / "icon.ico")
    print(f"wrote {png} and {ROOT / 'icon.ico'} sizes={ico.info.get('sizes')}")


if __name__ == "__main__":
    main()
