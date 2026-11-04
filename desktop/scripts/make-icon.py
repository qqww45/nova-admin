#!/usr/bin/env python3
"""从 resources/icon.svg 生成 icon.png（512）与多尺寸 icon.ico。"""

from __future__ import annotations

import io
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "resources"
SVG = ROOT / "icon.svg"

# 与 icon.svg 同步，供无 cairosvg 时的回退绘制
NAVY = (0x0F, 0x20, 0x3C, 255)
GOLD = (0xDD, 0xA9, 0x44, 255)
WHITE = (255, 255, 255, 255)
RX = 224
CX = CY = 512
RING_R = 340
RING_W = 32
CHECK = ((337, 520), (453, 627), (693, 381))
CHECK_W = 80


def render_via_cairosvg(size: int):
    import cairosvg
    from PIL import Image

    raw = cairosvg.svg2png(url=str(SVG), output_width=size, output_height=size)
    return Image.open(io.BytesIO(raw)).convert("RGBA")


def paint_mark(size: int):
    """按 SVG 几何用 PIL 回退绘制（圆角方 + 金圈 + 白勾）。"""
    from PIL import Image, ImageDraw

    s = size / 1024
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=max(1, RX * s), fill=NAVY)

    cx, cy, r = CX * s, CY * s, RING_R * s
    ring_w = max(1, round(RING_W * s))
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=GOLD, width=ring_w)

    pts = [(x * s, y * s) for x, y in CHECK]
    chk_w = max(1, round(CHECK_W * s))
    draw.line(pts, fill=WHITE, width=chk_w, joint="curve")
    rad = chk_w / 2
    for x, y in pts:
        draw.ellipse((x - rad, y - rad, x + rad, y + rad), fill=WHITE)
    return img


def render_master(size: int = 1024):
    try:
        return render_via_cairosvg(size)
    except Exception as exc:
        print(f"cairosvg 不可用，改用 PIL 回退：{exc}")
        return paint_mark(size)


def main() -> None:
    from PIL import Image

    ROOT.mkdir(parents=True, exist_ok=True)
    if not SVG.is_file():
        raise SystemExit(f"缺少 {SVG}")

    master = render_master(1024)
    png = ROOT / "icon.png"
    master.resize((512, 512), Image.Resampling.LANCZOS).save(png, "PNG")
    sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    master.save(ROOT / "icon.ico", format="ICO", sizes=sizes)
    ico = Image.open(ROOT / "icon.ico")
    print(f"wrote {png} and {ROOT / 'icon.ico'} sizes={ico.info.get('sizes')}")


if __name__ == "__main__":
    main()
