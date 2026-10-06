"""
生成 App 用的字体：把 SF Pro（西文）与苹方（汉字）合成一组字体文件，
放进 app/src/main/assets/fonts/。

## 为什么必须合成一个文件
Compose / Android 的 `FontFamily` 只能按**字重**选字体，**不支持按字符回退**
（那是 CSS `font-family: A, B` 的能力）。SF Pro 里没有汉字，
只把 SF Pro 设成字体族的话，中文会退回系统默认字体，而不是苹方。

唯一能带自定义回退的 `Typeface.CustomFallbackBuilder` 产出的是
`android.graphics.Typeface`，而 Compose 侧包装它的 `AndroidTypeface`
接口是 `internal` 的，App 代码实现不了（试过，编译直接报
"Cannot access 'interface AndroidTypeface': it is internal"）。

所以正确的路只剩一条：**让字体文件自己同时含西文与汉字**。

## 为什么不能直接用 fontTools.merge
苹方是 **CID-keyed CFF**（upem 1000），SF Pro 是 name-keyed CFF（upem 2048），
`fontTools.merge` 明确拒绝合并 CID-keyed CFF：

    NotImplementedError: Merging CID-keyed CFF tables is not supported yet

绕法是双方都转成 **TrueType(glyf) 轮廓**（三次贝塞尔转二次，cu2qu），
把苹方 upem 缩放到 2048，再合并两个 glyf 字体。

## 用法
    python tools/build_fonts.py
"""

from __future__ import annotations

import json
import pathlib
import subprocess
import sys
import tempfile
import time

WIN_FONTS = pathlib.Path(r"C:\Windows\Fonts")
PINGFANG_TTC = WIN_FONTS / "PingFang.ttc"

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "app" / "src" / "main" / "res" / "font"
CITY_DB = ROOT / "app" / "src" / "main" / "assets" / "china_cities.json"
SRC_DIR = ROOT / "app" / "src" / "main"

SF_UPEM = 2048

# 输出名 -> (SF Pro 文件, 苹方 family, 苹方 subfamily, Android 字重)
WEIGHTS = [
    ("app_thin.otf",     "SF-Pro-Display-Thin.otf",   "PingFang SC", "Light",    100),
    ("app_light.otf",    "SF-Pro-Text-Light.otf",     "PingFang SC", "Light",    300),
    ("app_regular.otf",  "SF-Pro-Text-Regular.otf",   "PingFang SC", "Regular",  400),
    ("app_medium.otf",   "SF-Pro-Text-Medium.otf",    "PingFang SC", "Medium",   500),
    ("app_semibold.otf", "SF-Pro-Text-Semibold.otf",  "PingFang SC", "Medium",   600),
    ("app_bold.otf",     "SF-Pro-Text-Bold.otf",      "PingFang SC", "Semibold", 700),
]

LATIN_RANGES = [
    (0x0020, 0x007E), (0x00A0, 0x00FF), (0x0100, 0x017F),
    (0x2000, 0x206F), (0x2070, 0x209F), (0x20A0, 0x20BF),
    (0x2100, 0x214F), (0x2190, 0x21FF), (0x2200, 0x22FF),
    (0x25A0, 0x25FF), (0x2600, 0x26FF), (0xFE50, 0xFE6F),
]


def cjk_text() -> str:
    chars: set[str] = set()
    for high in range(0xA1, 0xFA):
        for low in range(0xA1, 0xFF):
            try:
                chars.add(bytes([high, low]).decode("gb2312"))
            except UnicodeDecodeError:
                pass
    if CITY_DB.exists():
        for row in json.loads(CITY_DB.read_text(encoding="utf-8")):
            chars.update(str(row.get("n", "")))
            chars.update(str(row.get("p", "")))
    for path in SRC_DIR.rglob("*"):
        if path.suffix in {".kt", ".xml", ".json"} and "assets/fonts" not in str(path):
            try:
                chars.update(path.read_text(encoding="utf-8", errors="ignore"))
            except OSError:
                pass
    # 汉字 + 中文标点；拉丁交给 SF Pro
    return "".join(sorted(
        c for c in chars
        if 0x2E80 <= ord(c) <= 0x9FFF or 0xF900 <= ord(c) <= 0xFAFF
        or 0x3000 <= ord(c) <= 0x303F or 0xFF00 <= ord(c) <= 0xFFEF
        or c in "·—…“”‘’、。，．"
    ))


def subset(source: pathlib.Path, target: pathlib.Path,
           unicodes: str | None, text_file: pathlib.Path | None) -> bool:
    cmd = [
        sys.executable, "-m", "fontTools.subset", str(source),
        f"--output-file={target}",
        "--layout-features=*", "--name-IDs=*", "--recalc-bounds",
        "--drop-tables+=DSIG", "--no-desubroutinize",
    ]
    if unicodes:
        cmd.append(f"--unicodes={unicodes}")
    if text_file:
        cmd.append(f"--text-file={text_file}")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"    !! subset 失败: {result.stderr.strip()[:200]}")
        return False
    return True


def extract_face(family: str, subfamily: str, target: pathlib.Path) -> bool:
    from fontTools.ttLib import TTCollection

    target.parent.mkdir(parents=True, exist_ok=True)
    for font in TTCollection(str(PINGFANG_TTC), lazy=False).fonts:
        name = font["name"]
        if name.getDebugName(1) == family and name.getDebugName(2) == subfamily:
            font.save(str(target))
            return True
    print(f"    !! TTC 里没有 {family} {subfamily}")
    return False


def otf_to_ttf(source: pathlib.Path, target: pathlib.Path) -> bool:
    """CFF(三次贝塞尔) → glyf(二次贝塞尔)。otf2ttf 的标准做法。"""
    from fontTools.pens.cu2quPen import Cu2QuPen
    from fontTools.pens.ttGlyphPen import TTGlyphPen
    from fontTools.ttLib import TTFont, newTable

    font = TTFont(str(source))
    glyph_set = font.getGlyphSet()
    order = font.getGlyphOrder()

    glyf = newTable("glyf")
    glyf.glyphOrder = order
    glyf.glyphs = {}
    for name in order:
        pen = TTGlyphPen(glyph_set)
        # reverse_direction: CFF 是逆时针，TrueType 要求顺时针
        glyph_set[name].draw(Cu2QuPen(pen, 1.0, reverse_direction=True))
        glyf.glyphs[name] = pen.glyph()

    # 空字形（.notdef、空格）没有轮廓，也就没有 xMin，后续算表头会炸。
    # 补齐成 0，并顺手统计 maxp 需要的极值。
    max_points = 0
    max_contours = 0
    xs_min, ys_min, xs_max, ys_max = [], [], [], []
    for name, glyph in glyf.glyphs.items():
        if not hasattr(glyph, "numberOfContours"):
            glyph.numberOfContours = 0
        if glyph.numberOfContours == 0:
            glyph.xMin = glyph.yMin = glyph.xMax = glyph.yMax = 0
            continue
        # TTGlyphPen 产的 Glyph 不会立刻算 bbox（要到 compile 时才 recalcBounds），
        # 所以这里直接从轮廓点自己求，别去读 glyph.xMin。
        coordinates, end_points, _ = glyph.getCoordinates(glyf)
        max_points = max(max_points, len(coordinates))
        max_contours = max(max_contours, len(end_points))
        xs_min.append(min(point[0] for point in coordinates))
        ys_min.append(min(point[1] for point in coordinates))
        xs_max.append(max(point[0] for point in coordinates))
        ys_max.append(max(point[1] for point in coordinates))

    font["glyf"] = glyf
    font["loca"] = newTable("loca")

    # CFF 的 maxp 是 0.5 版（只有 numGlyphs）；TrueType 要 1.0 版。
    # 不用 recalc()：它会去比对 hmtx 与每个字形的 lsb，空字形会报 AttributeError。
    maxp = newTable("maxp")
    maxp.tableVersion = 0x00010000
    maxp.numGlyphs = len(order)
    maxp.maxPoints = max_points
    maxp.maxContours = max_contours
    maxp.maxCompositePoints = 0
    maxp.maxCompositeContours = 0
    # 这几个不能一律填 0：解析器会用它们预估复合字形的递归缓冲。
    # 0 会被当成"深度/元素数为零"，有复合字形时是 spec 违规。
    # 这里按规范给最小值；cu2qu 会把轮廓展开，所以本字体其实没有复合字形。
    maxp.maxZones = 2
    maxp.maxTwilightPoints = 0
    maxp.maxStorage = 0
    maxp.maxFunctionDefs = 0
    maxp.maxInstructionDefs = 0
    maxp.maxStackElements = 0
    maxp.maxSizeOfInstructions = 0
    maxp.maxComponentElements = 1
    maxp.maxComponentDepth = 1
    font["maxp"] = maxp

    head = font["head"]
    head.indexToLocFormat = 0
    head.glyphDataFormat = 0
    if xs_min:
        head.xMin, head.yMin = min(xs_min), min(ys_min)
        head.xMax, head.yMax = max(xs_max), max(ys_max)
    # post 在 CFF 里是 3.0（不含字形名），转成 2.0 需要额外的 extraNames/mapping，
    # 而渲染根本用不到字形名，直接删掉最省事。
    for tag in ("CFF ", "CFF2", "VORG", "post"):
        if tag in font:
            del font[tag]

    font.save(str(target))
    return True


def scale_upem(source: pathlib.Path, target: pathlib.Path, upem: int) -> bool:
    from fontTools.ttLib import TTFont
    from fontTools.ttLib.scaleUpem import scale_upem as do_scale

    font = TTFont(str(source))
    if font["head"].unitsPerEm != upem:
        do_scale(font, upem)
    font.save(str(target))
    return True


# 合并前要统一删掉的表。
# fontTools.merge 对那些没实现合并逻辑的表，要求所有输入里的内容**完全一致**，
# 否则报 "Expected all items to be equal"。SF Pro 与苹方的垂直排版表就不一致，
# 而 Android 用它排版横排文本完全不需要这些。
DROP_BEFORE_MERGE = ("vhea", "vmtx", "VORG", "DSIG", "Grotesque")


def merge(paths: list[pathlib.Path], target: pathlib.Path) -> bool:
    from fontTools.merge import Merger
    from fontTools.ttLib import TTFont

    prepared: list[pathlib.Path] = []
    for path in paths:
        font = TTFont(str(path))
        for tag in DROP_BEFORE_MERGE:
            if tag in font:
                del font[tag]
        cleaned = path.with_suffix(".clean.ttf")
        font.save(str(cleaned))
        prepared.append(cleaned)

    try:
        merged: TTFont = Merger().merge([str(p) for p in prepared])
        merged.save(str(target))
        return True
    except Exception as error:  # noqa: BLE001
        print(f"    !! 合并失败: {error}")
        return False


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    work = pathlib.Path(tempfile.mkdtemp(prefix="iweather_fonts_"))

    unicodes = ",".join(f"U+{a:04X}-{b:04X}" for a, b in LATIN_RANGES)
    text_file = work / "cjk.txt"
    text_file.write_text(cjk_text(), encoding="utf-8")
    print(f"西文码位段 {len(LATIN_RANGES)} 个；中文字符 {len(text_file.read_text(encoding='utf-8'))} 个\n")

    # 苹方各 face 只转一次，多个字重共用
    cjk_cache: dict[tuple[str, str], pathlib.Path] = {}
    total = 0

    # 传一个数字可以只跑前 N 个字重，方便先验证流程与耗时
    limit = int(sys.argv[1]) if len(sys.argv) > 1 else len(WEIGHTS)

    for out_name, sf_name, family, subfamily, weight in WEIGHTS[:limit]:
        started = time.time()
        print(f"[{out_name}] ← {sf_name} + {family} {subfamily}")

        # 1) 西文：子集化后转 glyf
        sf_subset = work / f"{out_name}.latin.otf"
        if not subset(WIN_FONTS / sf_name, sf_subset, unicodes, None):
            continue
        sf_ttf = work / f"{out_name}.latin.ttf"
        otf_to_ttf(sf_subset, sf_ttf)

        # 2) 中文：抽 face → 子集化 → 转 glyf → 缩放到 2048
        key = (family, subfamily)
        if key not in cjk_cache:
            raw = work / f"{family.replace(' ', '')}-{subfamily}.otf"
            if not extract_face(family, subfamily, raw):
                continue
            cjk_subset = work / f"cjk-{family.replace(' ', '')}-{subfamily}.otf"
            if not subset(raw, cjk_subset, None, text_file):
                continue
            cjk_ttf = work / f"cjk-{family.replace(' ', '')}-{subfamily}.ttf"
            otf_to_ttf(cjk_subset, cjk_ttf)
            cjk_scaled = work / f"cjk-{family.replace(' ', '')}-{subfamily}-{SF_UPEM}.ttf"
            scale_upem(cjk_ttf, cjk_scaled, SF_UPEM)
            cjk_cache[key] = cjk_scaled
        cjk_ttf = cjk_cache[key]

        # 3) 合并
        target = OUT_DIR / out_name
        if not merge([sf_ttf, cjk_ttf], target):
            continue

        size = target.stat().st_size
        total += size
        print(f"    -> {out_name}  {size/1024/1024:6.2f} MB   ({time.time()-started:.0f}s)")

    print(f"\n输出目录: {OUT_DIR}")
    print(f"字体总计: {total/1024/1024:.2f} MB")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
