#!/usr/bin/env python3
"""Builds app/src/main/assets/emoji.tsv for the launcher's emoji picker.

Sources (Fedora packages unicode-ucd and cldr-emoji-annotation, or the same files from
unicode.org / CLDR): emoji-data.txt for which code points are emoji, and CLDR annotations
for names (en) and keywords (en + sv). Skin-tone variants are left out; flags are kept.

Output: one emoji per line, `emoji<TAB>name<TAB>keywords`, faces first.
Usage: tools/gen_emoji.py [UCD_DIR] [CLDR_DIR]
"""
import re
import sys
import xml.etree.ElementTree as ET

ucd = sys.argv[1] if len(sys.argv) > 1 else "/usr/share/unicode/ucd"
cldr = sys.argv[2] if len(sys.argv) > 2 else "/usr/share/unicode/cldr/common"

emoji, presentation = set(), set()
for line in open(f"{ucd}/emoji/emoji-data.txt", encoding="utf-8"):
    m = re.match(r"([0-9A-F]+)(?:\.\.([0-9A-F]+))?\s*;\s*(\w+)", line)
    if not m:
        continue
    lo, hi, prop = int(m[1], 16), int(m[2] or m[1], 16), m[3]
    target = {"Emoji": emoji, "Emoji_Presentation": presentation}.get(prop)
    if target is not None:
        target.update(range(lo, hi + 1))

JOINERS = {0x200D, 0xFE0F, 0x20E3}
SKIN = set(range(0x1F3FB, 0x1F400))
REGIONAL = set(range(0x1F1E6, 0x1F200))


def annotations(path):
    names, keywords = {}, {}
    for a in ET.parse(path).getroot().iter("annotation"):
        cp, text = a.get("cp"), (a.text or "").strip()
        if a.get("type") == "tts":
            names[cp] = text
        else:
            keywords[cp] = [k.strip() for k in text.split("|") if k.strip()]
    return names, keywords


def merge(*parts):
    names, keywords = {}, {}
    for n, k in parts:
        names.update(n)
        for cp, ks in k.items():
            keywords.setdefault(cp, []).extend(ks)
    return names, keywords


en_n, en_k = merge(annotations(f"{cldr}/annotations/en.xml"), annotations(f"{cldr}/annotationsDerived/en.xml"))
_, sv_k = merge(annotations(f"{cldr}/annotations/sv.xml"), annotations(f"{cldr}/annotationsDerived/sv.xml"))
sv_n, _ = merge(annotations(f"{cldr}/annotations/sv.xml"), annotations(f"{cldr}/annotationsDerived/sv.xml"))


def usable(cp):
    points = [ord(c) for c in cp]
    if any(p in SKIN for p in points):
        return False
    core = [p for p in points if p not in JOINERS]
    if not core or not all(p in emoji or p in REGIONAL for p in core):
        return False
    if len(points) == 1 and (points[0] < 0xA9 or chr(points[0]).isdigit()):
        return False  # '#', '*', digits are only emoji as keycaps
    return True


def display(cp):
    # Text-default symbols (e.g. ❤ U+2764) need VS16 to draw as emoji.
    if len(cp) == 1 and ord(cp) not in presentation:
        return cp + "️"
    return cp


def order(cp):
    p = ord(cp[0])
    if 0x1F600 <= p <= 0x1F64F: block = 0    # faces
    elif 0x1F900 <= p <= 0x1F9FF: block = 1  # more faces, people, food
    elif 0x1F300 <= p <= 0x1F5FF: block = 2  # symbols & pictographs
    elif 0x1F680 <= p <= 0x1F6FF: block = 3  # transport
    elif 0x1FA70 <= p <= 0x1FAFF: block = 4
    elif p in REGIONAL: block = 6            # flags last
    else: block = 5
    return block, cp


rows = []
for cp in sorted((cp for cp in en_n if usable(cp)), key=order):
    name = en_n[cp].replace("\t", " ")
    words = []
    for w in en_k.get(cp, []) + ([sv_n[cp]] if cp in sv_n else []) + sv_k.get(cp, []):
        w = w.replace("\t", " ").lower()
        if w not in words and w != name.lower():
            words.append(w)
    rows.append(f"{display(cp)}\t{name}\t{' '.join(words)}")

out = "app/src/main/assets/emoji.tsv"
with open(out, "w", encoding="utf-8") as f:
    f.write("\n".join(rows) + "\n")
print(f"{len(rows)} emoji -> {out}")
