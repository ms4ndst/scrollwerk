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
    # …Shojumaru's wedge-cut, Japanese-styled capitals for its headings…
    "shojumaru": ("shojumaru", None, {"regular": "Shojumaru-Regular.ttf"}),
    # …and Yuji Syuku's brush kanji, only the characters the theme writes (see KANJI).
    "kanji_brush": ("yujisyuku", None, {"regular": "YujiSyuku-Regular.ttf"}),
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
}

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
    font.save(dest)


import urllib.parse  # noqa: E402


total = 0
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
        save_subset(font, dest, ONLY.get(prefix, UNICODES))
        total += os.path.getsize(dest)
    print(f"{prefix}: ok")
print(f"{len(FAMILIES)} families, {total // 1024} KB -> {out_dir}")
