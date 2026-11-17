#!/usr/bin/env python3
"""
CivilEG2 contrast + theme guard (token level, stdlib only).

    python3 -I tools/guards/contrast_check.py [repo_root] [--baseline FILE] [--write-baseline]

What it checks (WCAG 2.x contrast ratios, computed from the Kotlin sources as text):
  1. Effective M3 ColorScheme pairs from ui/theme/Theme.kt (what MaterialTheme really resolves):
     on/container pairs, and `primary`/`secondary`/`error` used as text on surface/background.
     A mapped container whose `on` role is NOT mapped is a failure (falls back to Material baseline).
  2. Palette text tokens in ui/theme/Color.kt (textMuted, safe, warning, ...) against surfaces.
  3. DrawingColors (light + dark) against the theme surface they are drawn on.
  4. Theme source: isSystemInDarkTheme() outside MainActivity.kt / theme/Theme.kt.
  5. Ratchets: hard-coded colors and `fontSize = N.sp` literals outside ui/theme/ may only go DOWN.

Baseline: known failures are stored in the baseline file. The script exits 1 only for NEW failures
or ratchet increases, so it can be adopted before everything is fixed. Fixed items are listed so the
baseline can be shrunk (`--write-baseline`). Heuristic parser: keep the declaration style of
Color.kt / Theme.kt / DrawingUtils.kt (`val x = Color(0xAARRGGBB)`, `role = PremiumColorsX.token`).
"""
import argparse, json, pathlib, re, sys

UI_REL = "app/src/main/java/com/civileg/app/ui"
TEXT_MIN, GRAPHIC_MIN = 4.5, 3.0

# ---------- color math ----------
def _lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4

def lum(rgb):
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    return 0.2126 * _lin(r) + 0.7152 * _lin(g) + 0.0722 * _lin(b)

def ratio(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)

# ---------- parsing ----------
COLOR_VAL = re.compile(r"val\s+(\w+)\s*(?::\s*Color)?\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)")

def parse_palette(src, obj):
    m = re.search(r"object\s+" + obj + r"\s*\{(.*?)\n\}", src, re.S)
    if not m:
        raise SystemExit(f"cannot find object {obj} in Color.kt")
    return {k: int(v, 16) & 0xFFFFFF for k, v in COLOR_VAL.findall(m.group(1))}

def parse_scheme(src, scheme_val):
    m = re.search(r"val\s+" + scheme_val + r"\s*=\s*\w+ColorScheme\((.*?)\n\)", src, re.S)
    if not m:
        raise SystemExit(f"cannot find {scheme_val} in Theme.kt")
    return re.findall(r"(\w+)\s*=\s*(PremiumColors(?:Light|Dark))\.(\w+)", m.group(1))

def parse_drawing(src):
    cls = re.search(r"data class DrawingColors\((.*?)\n\) \{", src, re.S).group(1)
    defaults = {k: int(v, 16) & 0xFFFFFF for k, v in COLOR_VAL.findall(cls)}
    def override(name):
        m = re.search(r"val\s+" + name + r"\s*=\s*DrawingColors\((.*?)\n        \)", src, re.S)
        o = {k: int(v, 16) & 0xFFFFFF for k, v in re.findall(r"(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{8})\)", m.group(1))}
        d = dict(defaults); d.update(o)
        return d, set(o)
    return override("Light"), override("Dark")

# ---------- rules ----------
SCHEME_PAIRS = [
    ("onPrimary", "primary", TEXT_MIN), ("onPrimaryContainer", "primaryContainer", TEXT_MIN),
    ("onSecondary", "secondary", TEXT_MIN), ("onSecondaryContainer", "secondaryContainer", TEXT_MIN),
    ("onError", "error", TEXT_MIN), ("onBackground", "background", TEXT_MIN),
    ("onSurface", "surface", TEXT_MIN), ("onSurfaceVariant", "surfaceVariant", TEXT_MIN),
    ("primary", "surface", TEXT_MIN), ("primary", "background", TEXT_MIN),
    ("secondary", "surface", TEXT_MIN), ("error", "surface", TEXT_MIN),
    ("outline", "surface", GRAPHIC_MIN),
]
PALETTE_PAIRS = [  # text-capable tokens only; *Bright / tertiary are fill-only and intentionally absent
    ("textPrimary", "background", TEXT_MIN), ("textSecondary", "background", TEXT_MIN),
    ("textMuted", "background", TEXT_MIN), ("textMuted", "surface", TEXT_MIN),
    ("safe", "surface", TEXT_MIN), ("warning", "surface", TEXT_MIN),
    ("unsafe", "surface", TEXT_MIN), ("info", "surface", TEXT_MIN),
    ("textOnAccent", "accent", TEXT_MIN), ("outlineVariant", "surface", GRAPHIC_MIN),
]
DRAW_TEXT = ["textPrimary", "textSecondary", "dimColor", "dimLineColor", "labelGray", "coverColor",
             "sectionLineColor", "shearPerimColor", "tableCellText", "safeGreen", "unsafeRed", "warningOrange"]
DRAW_GRAPHIC = ["rebarBlue", "topRebarBlue", "stirrupPurple", "secondaryRed", "distBarGreen", "concreteStroke",
                "extensionGray", "stressPink", "soilBrown", "waterBlue"]

HARDCODED = re.compile(r"Color\(0x[0-9A-Fa-f]{6,8}\)|Color\.(?:White|Black|Gray|LightGray|DarkGray|Red|Green|Blue|Yellow|Cyan|Magenta)\b")
FONT_LIT = re.compile(r"fontSize\s*=\s*[0-9.]+\.sp")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("root", nargs="?", default=".")
    ap.add_argument("--baseline", default=None)
    ap.add_argument("--write-baseline", action="store_true")
    a = ap.parse_args()
    root = pathlib.Path(a.root)
    ui = root / UI_REL
    baseline_path = pathlib.Path(a.baseline) if a.baseline else root / "tools/guards/contrast_baseline.json"

    color_src = (ui / "theme/Color.kt").read_text(encoding="utf-8")
    theme_src = (ui / "theme/Theme.kt").read_text(encoding="utf-8")
    draw_src = (ui / "compose/components/drawings/DrawingUtils.kt").read_text(encoding="utf-8")
    pal = {"Light": parse_palette(color_src, "PremiumColorsLight"), "Dark": parse_palette(color_src, "PremiumColorsDark")}

    failures = {}  # key -> message

    # 1. effective scheme
    for mode, scheme_val in (("Light", "PremiumLightColorScheme"), ("Dark", "PremiumDarkColorScheme")):
        mapped = {role: pal[mode][tok] for role, _, tok in parse_scheme(theme_src, scheme_val) if tok in pal[mode]}
        for fg, bg, need in SCHEME_PAIRS:
            if bg not in mapped:
                continue
            if fg not in mapped:
                failures[f"scheme:{mode}:{fg}/{bg}"] = f"{fg} is not mapped while {bg} is (Material baseline fallback, contrast not guaranteed)"
                continue
            r = ratio(mapped[fg], mapped[bg])
            if r < need:
                failures[f"scheme:{mode}:{fg}/{bg}"] = f"{r:.2f}:1 < {need}"

    # 2. palette tokens
    for mode in ("Light", "Dark"):
        p = pal[mode]
        for fg, bg, need in PALETTE_PAIRS:
            if fg in p and bg in p:
                r = ratio(p[fg], p[bg])
                if r < need:
                    failures[f"palette:{mode}:{fg}/{bg}"] = f"{r:.2f}:1 < {need}"

    # 3. drawing palette on theme surface
    (light, _), (dark, dark_over) = parse_drawing(draw_src)
    for mode, d in (("Light", light), ("Dark", dark)):
        bg = pal[mode]["surface"]
        for f in DRAW_TEXT:
            r = ratio(d[f], bg)
            if r < TEXT_MIN:
                failures[f"drawing:{mode}:text:{f}"] = f"{r:.2f}:1 < {TEXT_MIN} (on theme surface)"
        for f in DRAW_GRAPHIC:
            r = ratio(d[f], bg)
            if r < GRAPHIC_MIN:
                failures[f"drawing:{mode}:graphic:{f}"] = f"{r:.2f}:1 < {GRAPHIC_MIN} (on theme surface)"

    # 4. theme source
    allowed = {"MainActivity.kt", "Theme.kt"}
    for f in (root / "app/src/main/java").rglob("*.kt"):
        if f.name in allowed:
            continue
        n = f.read_text(encoding="utf-8", errors="replace").count("isSystemInDarkTheme()")
        if n:
            failures[f"theme-source:{f.name}"] = f"{n} direct isSystemInDarkTheme() call(s); use the in-app ThemeMode via the theme provider"

    # 5. ratchets
    hard = fonts = 0
    for f in ui.rglob("*.kt"):
        if "/theme/" in f.as_posix():
            continue
        t = f.read_text(encoding="utf-8", errors="replace")
        hard += len(HARDCODED.findall(t))
        fonts += len(FONT_LIT.findall(t))
    metrics = {"hardcoded_colors": hard, "font_size_literals": fonts}

    base = {"failures": [], "metrics": metrics}
    if baseline_path.exists():
        base = json.loads(baseline_path.read_text())

    if a.write_baseline:
        baseline_path.parent.mkdir(parents=True, exist_ok=True)
        baseline_path.write_text(json.dumps({"failures": sorted(failures), "metrics": metrics}, indent=2))
        print(f"baseline written: {len(failures)} known failures, metrics {metrics}")
        return 0

    known = set(base.get("failures", []))
    new = sorted(k for k in failures if k not in known)
    fixed = sorted(k for k in known if k not in failures)
    print(f"{len(failures)} failing checks ({len(known)} in baseline); metrics: {metrics}")
    for k in sorted(failures):
        print(("  NEW  " if k in new else "  known") + f" {k}: {failures[k]}")
    for k in fixed:
        print(f"  FIXED {k}  (remove from baseline with --write-baseline)")
    bad = bool(new)
    for m, v in metrics.items():
        b = base.get("metrics", {}).get(m)
        if b is not None and v > b:
            print(f"  RATCHET {m}: {v} > baseline {b}")
            bad = True
    return 1 if bad else 0

if __name__ == "__main__":
    sys.exit(main())
