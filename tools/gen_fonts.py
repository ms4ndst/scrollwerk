#!/usr/bin/env python3
"""Builds the theme fonts in app/src/main/res/font/ from Google Fonts (all SIL OFL 1.1).

Each family is downloaded from github.com/google/fonts, cut to static Regular / Medium /
Bold instances (variable fonts via fontTools' instancer), and subset to Latin
(Latin-1, so å ä ö é ü, plus common punctuation, arrows and €), which keeps
each file to a few tens of KB. Other characters fall back to the phone's own fonts.
Each family's OFL.txt goes to app/src/main/assets/licenses/fonts/<family>.txt.

Usage: tools/gen_fonts.py [CACHE_DIR]   (needs fontTools: pip install fonttools)
"""
import io
import os
import sys
import urllib.request

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

BASE = "https://raw.githubusercontent.com/google/fonts/main/ofl"
# SemiBold (600) text uses Bold, as Android's font matching does without a 600.
WEIGHTS = {"regular": 400, "medium": 500, "bold": 700}

# resource prefix: (google/fonts dir, variable file or None, {weight: static file})
FAMILIES = {
    # Japanese fonts whose Latin letters carry the style; one weight each (bold is synthesised).
    # Kanagawa: a clean Japanese gothic for Latin text…
    "zen_kaku_gothic_new": ("zenkakugothicnew", None, {
        "regular": "ZenKakuGothicNew-Regular.ttf", "medium": "ZenKakuGothicNew-Medium.ttf", "bold": "ZenKakuGothicNew-Bold.ttf"}),
    # …ZCOOL QingKe HuangYou's narrow shop-sign letters for its headings…
    "zcool_qingke_huangyou": ("zcoolqingkehuangyou", None, {"regular": "ZCOOLQingKeHuangYou-Regular.ttf"}),
    # …and Yuji Syuku's brush kanji, only the characters the theme writes (see KANJI).
    "kanji_brush": ("yujisyuku", None, {"regular": "YujiSyuku-Regular.ttf"}),
    "pixelify_sans": ("pixelifysans", "PixelifySans[wght].ttf", None),
    "dotgothic16": ("dotgothic16", None, {"regular": "DotGothic16-Regular.ttf"}),
    "jetbrains_mono": ("jetbrainsmono", "JetBrainsMono[wght].ttf", None),
    "inter": ("inter", "Inter[opsz,wght].ttf", None),
    "nunito": ("nunito", "Nunito[wght].ttf", None),
    "josefin_sans": ("josefinsans", "JosefinSans[wght].ttf", None),
    "lora": ("lora", "Lora[wght].ttf", None),
    "rubik": ("rubik", "Rubik[wght].ttf", None),
    "space_grotesk": ("spacegrotesk", "SpaceGrotesk[wght].ttf", None),
    "quicksand": ("quicksand", "Quicksand[wght].ttf", None),
    "source_sans_3": ("sourcesans3", "SourceSans3[wght].ttf", None),
    "fira_sans": ("firasans", None, {
        "regular": "FiraSans-Regular.ttf", "medium": "FiraSans-Medium.ttf",
        "semibold": "FiraSans-SemiBold.ttf", "bold": "FiraSans-Bold.ttf"}),
    "fira_code": ("firacode", "FiraCode[wght].ttf", None),
    "exo_2": ("exo2", "Exo2[wght].ttf", None),
    "ibm_plex_sans": ("ibmplexsans", "IBMPlexSans[wdth,wght].ttf", None),
    # Display fonts to pick by hand, mostly for headings (see DISPLAY).
    "shojumaru": ("shojumaru", None, {"regular": "Shojumaru-Regular.ttf"}),
    "zcool_kuaile": ("zcoolkuaile", None, {"regular": "ZCOOLKuaiLe-Regular.ttf"}),
    "gugi": ("gugi", None, {"regular": "Gugi-Regular.ttf"}),
    "do_hyeon": ("dohyeon", None, {"regular": "DoHyeon-Regular.ttf"}),
    "black_han_sans": ("blackhansans", None, {"regular": "BlackHanSans-Regular.ttf"}),
    "kdam_thmor_pro": ("kdamthmorpro", None, {"regular": "KdamThmorPro-Regular.ttf"}),
    "tektur": ("tektur", "Tektur[wdth,wght].ttf", None),
    "chakra_petch": ("chakrapetch", None, {
        "regular": "ChakraPetch-Regular.ttf", "medium": "ChakraPetch-Medium.ttf", "bold": "ChakraPetch-Bold.ttf"}),
    "goldman": ("goldman", None, {"regular": "Goldman-Regular.ttf", "bold": "Goldman-Bold.ttf"}),
    "russo_one": ("russoone", None, {"regular": "RussoOne-Regular.ttf"}),
    "black_ops_one": ("blackopsone", None, {"regular": "BlackOpsOne-Regular.ttf"}),
    "sarpanch": ("sarpanch", None, {"regular": "Sarpanch-Regular.ttf", "medium": "Sarpanch-Medium.ttf", "bold": "Sarpanch-Bold.ttf"}),
    "oxanium": ("oxanium", "Oxanium[wght].ttf", None),
    "squada_one": ("squadaone", None, {"regular": "SquadaOne-Regular.ttf"}),
    "turret_road": ("turretroad", None, {
        "regular": "TurretRoad-Regular.ttf", "medium": "TurretRoad-Medium.ttf", "bold": "TurretRoad-Bold.ttf"}),
    "wallpoet": ("wallpoet", None, {"regular": "Wallpoet-Regular.ttf"}),
}

# These are scaled so their capitals stand as tall as the text fonts' (0.70 em, Zen Kaku Gothic New's), so text or
# a heading in any of them sits at the size the app asks for. Narrow ones get a little extra to look as large.
DISPLAY = {"pixelify_sans", "zcool_qingke_huangyou", "shojumaru", "zcool_kuaile", "gugi", "do_hyeon", "black_han_sans", "kdam_thmor_pro",
           "tektur", "chakra_petch", "goldman", "russo_one", "black_ops_one", "sarpanch", "oxanium", "squada_one",
           "turret_road", "wallpoet"}
CAP_HEIGHT = 0.70
NARROW_BOOST = 1.06  # for fonts whose "n" is narrower than 0.47 em

# Kanji the Kanagawa theme writes: its workspace numerals.
KANJI = [ord(ch) for ch in "〇一二三四五六七八九十 "]
ONLY = {"kanji_brush": KANJI}

UNICODES = (
    list(range(0x20, 0x7F)) + list(range(0xA0, 0x100))          # ASCII, Latin-1 (å ä ö é ü ñ …)
    + list(range(0x2010, 0x2028)) + list(range(0x2030, 0x203B))  # dashes, quotes, bullets, …
    + [0x20AC, 0x2122, 0x2190, 0x2191, 0x2192, 0x2193, 0x2212]   # € ™ arrows −
)

cache = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), ".cache", "fonts")
out_dir = "app/src/main/res/font"
lic_dir = "app/src/main/assets/licenses/fonts"
os.makedirs(cache, exist_ok=True)
os.makedirs(out_dir, exist_ok=True)
os.makedirs(lic_dir, exist_ok=True)


def fetch(path):
    local = os.path.join(cache, path.replace("/", "__"))
    if not os.path.exists(local):
        url = f"{BASE}/{urllib.parse.quote(path)}"
        with urllib.request.urlopen(url, timeout=60) as r, open(local, "wb") as f:
            f.write(r.read())
    return local


def save_subset(font, dest, unicodes):
    opts = subset.Options()
    # fontTools' default features (kerning, ligatures, marks); stylistic sets would pull in extra glyphs.
    opts.notdef_outline = True
    opts.hinting = False
    sub = subset.Subsetter(opts)
    sub.populate(unicodes=unicodes)
    sub.subset(font)
    fit_line_metrics(font)
    font.save(dest)


import urllib.parse  # noqa: E402
from fontTools.pens.boundsPen import BoundsPen  # noqa: E402
from fontTools.ttLib.scaleUpem import scale_upem  # noqa: E402


def size_factor(font):
    """How much to scale [font] so its capital H is CAP_HEIGHT em tall (narrow fonts a little more)."""
    upem = font["head"].unitsPerEm
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    pen = BoundsPen(glyphs)
    glyphs[cmap[ord("H")]].draw(pen)
    factor = CAP_HEIGHT / (pen.bounds[3] / upem)
    if font["hmtx"][cmap[ord("n")]][0] / upem < 0.47:
        factor *= NARROW_BOOST
    return round(factor, 3)


def fit_line_metrics(font):
    """Widens the font's line box to its tallest and deepest kept glyph (Å's ring, g's tail), so text never clips."""
    glyf = font["glyf"]
    drawn = []
    for name in glyf.keys():
        glyph = glyf[name]
        glyph.recalcBounds(glyf)  # subsetting and scaling can leave stale bounds
        if glyph.numberOfContours:
            drawn.append(glyph)
    y_max = max((g.yMax for g in drawn), default=0)
    y_min = min((g.yMin for g in drawn), default=0)
    hhea, os2 = font["hhea"], font["OS/2"]
    hhea.ascent = max(hhea.ascent, y_max)
    hhea.descent = min(hhea.descent, y_min)
    os2.usWinAscent = max(os2.usWinAscent, y_max)
    os2.usWinDescent = max(os2.usWinDescent, -y_min)
    if os2.fsSelection & (1 << 7):  # USE_TYPO_METRICS: renderers read these instead
        os2.sTypoAscender = max(os2.sTypoAscender, y_max)
        os2.sTypoDescender = min(os2.sTypoDescender, y_min)


def enlarge(font, factor):
    """Scales every outline and metric by [factor] while keeping units-per-em, so glyphs draw that much larger."""
    if abs(factor - 1) < 0.01:
        return
    upem = font["head"].unitsPerEm
    scale_upem(font, round(upem * factor))
    font["head"].unitsPerEm = upem


total = 0
factors = {}
for prefix, (folder, variable, statics) in FAMILIES.items():
    with open(fetch(f"{folder}/OFL.txt"), encoding="utf-8") as src, open(f"{lic_dir}/{prefix}.txt", "w", encoding="utf-8") as dst:
        dst.write(src.read())
    for wname, wvalue in WEIGHTS.items():
        if statics is not None and wname not in statics:
            continue  # single-weight family
        dest = f"{out_dir}/{prefix}_{wname}.ttf"
        if variable:
            vf = TTFont(fetch(f"{folder}/{variable}"))
            axes = {a.axisTag: a for a in vf["fvar"].axes}
            pin = {"wght": max(axes["wght"].minValue, min(axes["wght"].maxValue, wvalue))}
            for tag, axis in axes.items():
                if tag != "wght":
                    pin[tag] = axis.defaultValue
            font = instancer.instantiateVariableFont(vf, pin, updateFontNames=False)
        else:
            font = TTFont(fetch(f"{folder}/{statics[wname]}"))
        if prefix in DISPLAY:
            if wname == "regular" or prefix not in factors:
                factors[prefix] = size_factor(font)  # one factor per family, from its regular
            enlarge(font, factors[prefix])
        save_subset(font, dest, ONLY.get(prefix, UNICODES))
        total += os.path.getsize(dest)
    print(f"{prefix}: ok")
print(f"{len(FAMILIES)} families, {total // 1024} KB -> {out_dir}")
print("display scaling:", ", ".join(f"{k} ×{v}" for k, v in sorted(factors.items())))
