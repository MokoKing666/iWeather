"""
把源图标转成 Android 的启动图标资源。

## 源图与要处理的问题
源图是一张 iOS 风格的应用图标：蓝色圆角方块 + 白云 + 太阳，400×400 的 webp。
直接丢给 Android 会有两个问题：

1. **圆角是烘焙在图里的**。Android 12 起启动器会自己套蒙版（圆形/方形/水滴…），
   如果图标自带圆角，就会出现"双层圆角"，四个角露出底色。
   所以先裁掉圆角区域、放大成满幅（full-bleed），让蓝色铺满整个画布。
2. **自适应图标分前景/背景两层**。前景层会被蒙版裁掉外圈约 18%，
   云和太阳必须落在中间的安全区里。

## 产出
    mipmap-{mdpi..xxxhdpi}/ic_launcher.png          传统图标（48~192）
    mipmap-{mdpi..xxxhdpi}/ic_launcher_round.png    圆形图标
    mipmap-{mdpi..xxxhdpi}/ic_launcher_foreground.png  自适应图标前景（108~432）
    values/colors.xml 里的 ic_launcher_background   取自源图的蓝色
    mipmap-anydpi-v26/ic_launcher{,_round}.xml      自适应图标声明

## 用法
    python tools/make_icon.py <源图路径>
"""

from __future__ import annotations

import pathlib
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

# 传统图标尺寸（每个密度）
LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# 自适应图标前景：108dp 画布，各密度对应的像素尺寸
ADAPTIVE_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}

# 源图圆角占边长的比例（iOS 图标大约 22%），
# 对应的"要裁掉多少" = R * (1 - 1/√2) ≈ 0.293R ≈ 6.5%
CORNER_INSET_RATIO = 0.065


def is_blue(pixel: tuple[int, int, int, int]) -> bool:
    r, g, b, a = pixel
    if a < 40:
        return False
    # 蓝色：蓝通道明显高于红通道，且整体偏亮
    return b > 120 and b - r > 40 and g > r


def blue_square_bounds(image: Image.Image) -> tuple[int, int, int, int]:
    """扫出蓝色方块的范围：中间行与中间列上蓝色像素的起止位置。"""
    width, height = image.size
    pixels = image.load()

    mid_y = height // 2
    left = next((x for x in range(width) if is_blue(pixels[x, mid_y])), 0)
    right = next((x for x in range(width - 1, -1, -1) if is_blue(pixels[x, mid_y])), width - 1)

    mid_x = width // 2
    top = next((y for y in range(height) if is_blue(pixels[mid_x, y])), 0)
    bottom = next((y for y in range(height - 1, -1, -1) if is_blue(pixels[mid_x, y])), height - 1)

    return left, top, right, bottom


def to_full_bleed(image: Image.Image) -> Image.Image:
    """裁掉烘焙的圆角，放大成满幅，让蓝色铺满整个画布。"""
    left, top, right, bottom = blue_square_bounds(image)
    side = min(right - left, bottom - top)
    inset = int(side * CORNER_INSET_RATIO)

    box = (
        left + inset,
        top + inset,
        left + side - inset,
        top + side - inset,
    )
    cropped = image.crop(box).convert("RGBA")
    return cropped.resize((side, side), Image.LANCZOS)


def heavy_blue(image: Image.Image) -> tuple[int, int, int]:
    """取源图里最具代表性的蓝色，用来做自适应图标的背景色。"""
    width, height = image.size
    samples = [
        image.getpixel((width // 2, max(2, height // 20))),          # 顶部中间
        image.getpixel((max(2, width // 20), height // 2)),          # 左侧中间
        image.getpixel((width - max(3, width // 20), height // 2)),  # 右侧中间
    ]
    blues = [p for p in samples if is_blue(p)]
    if not blues:
        return (47, 123, 246)
    red = sum(p[0] for p in blues) // len(blues)
    green = sum(p[1] for p in blues) // len(blues)
    blue = sum(p[2] for p in blues) // len(blues)
    return (red, green, blue)


def white_text_color(background: tuple[int, int, int]) -> str:
    return "#%02X%02X%02X" % background


def write(path: pathlib.Path, image: Image.Image) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)
    return path.stat().st_size


def circle_mask(size: int) -> Image.Image:
    mask = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    return mask.resize((size, size), Image.LANCZOS)


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print("用法: python tools/make_icon.py <源图路径>")
        return 2

    source = pathlib.Path(argv[1])
    if not source.exists():
        print(f"源图不存在: {source}")
        return 2

    original = Image.open(source).convert("RGBA")
    print(f"源图: {source.name}  {original.size[0]}×{original.size[1]}")

    bounds = blue_square_bounds(original)
    print(f"蓝色方块范围: {bounds}")

    # 源图自带透明圆角，不裁切。自适应图标里把它缩到安全区，
    # 四角的透明处由同色蓝背景补上，视觉上完全接缝看不出来。
    full = original
    background = heavy_blue(full)
    print(f"背景蓝: #{white_text_color(background)}")

    total = 0
    for density, size in LEGACY_SIZES.items():
        square = full.resize((size, size), Image.LANCZOS)
        total += write(RES / f"mipmap-{density}" / "ic_launcher.png", square)

        # 圆形版：把满幅图裁成圆
        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_icon.paste(square, (0, 0), circle_mask(size))
        total += write(RES / f"mipmap-{density}" / "ic_launcher_round.png", round_icon)

    for density, size in ADAPTIVE_SIZES.items():
        # 前景只占安全区（108dp 里 72dp 是保证可见的，取 74% 留一点余量），
        # 背景铺同色蓝，圆角透明处自然融合。
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        inner = int(size * 0.74)
        offset = (size - inner) // 2
        canvas.paste(full.resize((inner, inner), Image.LANCZOS), (offset, offset))
        total += write(RES / f"mipmap-{density}" / "ic_launcher_foreground.png", canvas)

    # 自适应图标声明
    adaptive = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
        "</adaptive-icon>\n"
    )
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        target = RES / "mipmap-anydpi-v26" / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(adaptive, encoding="utf-8")

    # 背景色写进 colors.xml（保留已有条目）
    colors = RES / "values" / "colors.xml"
    colors.parent.mkdir(parents=True, exist_ok=True)
    entry = f'    <color name="ic_launcher_background">{white_text_color(background)}</color>'
    if colors.exists():
        text = colors.read_text(encoding="utf-8")
        if "ic_launcher_background" in text:
            lines = [
                entry if "ic_launcher_background" in line else line
                for line in text.splitlines()
            ]
            text = "\n".join(lines) + "\n"
        else:
            text = text.replace("</resources>", entry + "\n</resources>")
    else:
        text = (
            '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
            + entry
            + "\n</resources>\n"
        )
    colors.write_text(text, encoding="utf-8")

    print(f"\n图标总大小: {total/1024:.1f} KB")
    print(f"自适应图标: {RES / 'mipmap-anydpi-v26'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
