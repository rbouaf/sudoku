#!/usr/bin/env python3
"""Regenerates app/src/main/java/app/materialsudoku/ui/theme/Palettes.kt from the mockups' tokens.

The four seed palettes (Expressive scheme variant) are taken verbatim from
../sudoku-mockups/html/_tokens.css, so the app and the locked mockups share every colour.
Usage: python3 tools/gen_palettes.py
"""
import pathlib, re

ROOT = pathlib.Path(__file__).resolve().parent.parent
CSS = (ROOT.parent / "sudoku-mockups/html/_tokens.css").read_text()
OUT = ROOT / "app/src/main/java/app/materialsudoku/ui/theme/Palettes.kt"

def camel(s): return re.sub(r"-([a-z])", lambda m: m.group(1).upper(), s)

ROLES = ["primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "inversePrimary",
 "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
 "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
 "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant",
 "surfaceTint", "inverseSurface", "inverseOnSurface", "error", "onError", "errorContainer",
 "onErrorContainer", "outline", "outlineVariant", "scrim", "surfaceBright", "surfaceDim",
 "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest", "surfaceContainerLow",
 "surfaceContainerLowest"]

def block(seed, mode):
    m = re.search(r'\[data-seed="%s"\]\[data-mode="%s"\][^{]*\{([^}]*)\}' % (seed, mode), CSS)
    vals = dict((camel(k), v.upper()) for k, v in re.findall(r"--([a-z-]+):\s*#([0-9A-Fa-f]{6})", m.group(1)))
    missing = [r for r in ROLES if r not in vals]
    assert not missing, (seed, mode, missing)
    fn = "lightColorScheme" if mode == "light" else "darkColorScheme"
    args = ",\n".join(f"        {r} = Color(0xFF{vals[r]})" for r in ROLES)
    return f"    {fn}(\n{args},\n    )"

seeds = ["amber", "teal", "violet", "rose"]
body = "\n".join(
    f"    {s.upper()}(\n{block(s, 'light')},\n{block(s, 'dark')},\n    ),".replace("\n    lightColorScheme", "\n        lightColorScheme")
    for s in seeds)
OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(f"""// Written by tools/gen_palettes.py from sudoku-mockups/html/_tokens.css. Do not edit.
package app.materialsudoku.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** The fixed seeds offered beside wallpaper colour. Every one is the Expressive scheme variant. */
enum class Seed(val light: ColorScheme, val dark: ColorScheme) {{
{body}
}}
""")
print("wrote", OUT)
