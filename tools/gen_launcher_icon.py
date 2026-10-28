#!/usr/bin/env python3
"""Regenerates the adaptive launcher icon's foreground (and an SVG preview in /tmp).

A box of nine tiles, as on the board: givens dark, empty wells faint, and the highlighted digit's
Cookie9Sided (the real MaterialShape path from the mockups) lit in the middle. Everything sits in a
46 dp square centred on the 108 dp canvas, inside the 66 dp safe circle every launcher mask keeps
(a 46 dp square has its corners 32.5 dp from centre, just under the 33 dp radius).
Usage: python3 tools/gen_launcher_icon.py
"""
import pathlib, re
ROOT = pathlib.Path(__file__).resolve().parent.parent
shapes = (ROOT.parent / "sudoku-mockups/html/_shapes.js").read_text()
cookie = re.search(r'"Cookie9Sided": *"([^"]*)"', shapes).group(1)

INK = "#593400"            # onPrimaryContainer of the Amber seed, on the #FFB156 background
TILE, GAP, BOX = 13.0, 3.5, 46.0
O = (108 - BOX) / 2        # 33
# g = given (dark), e = empty well (faint), h = the highlighted digit's Cookie
PATTERN = ["geg", "ehg", "gge"]

def rr(x, y, s, r):
    return (f"M{x+r},{y}h{s-2*r}a{r},{r} 0 0 1 {r},{r}v{s-2*r}a{r},{r} 0 0 1 {-r},{r}"
            f"h{-(s-2*r)}a{r},{r} 0 0 1 {-r},{-r}v{-(s-2*r)}a{r},{r} 0 0 1 {r},{-r}z")

paths, svg = [], []
for r, row in enumerate(PATTERN):
    for c, k in enumerate(row):
        x, y = O + c * (TILE + GAP), O + r * (TILE + GAP)
        if k == "h":
            s = TILE * 1.3                       # the lit tile swells, as on the board
            cx, cy = x + TILE / 2, y + TILE / 2
            sc = s / 100
            paths.append(f'    <group android:translateX="{cx - s/2:.3f}" android:translateY="{cy - s/2:.3f}" android:scaleX="{sc:.4f}" android:scaleY="{sc:.4f}">\n'
                         f'        <path android:fillColor="#FFFFFF" android:pathData="{cookie}"/>\n    </group>')
            svg.append(f'<g transform="translate({cx - s/2:.3f} {cy - s/2:.3f}) scale({sc:.4f})"><path fill="#FFFFFF" d="{cookie}"/></g>')
        else:
            a = "1.0" if k == "g" else "0.32"
            d = rr(x, y, TILE, 3.4)
            paths.append(f'    <path android:fillColor="{INK}" android:fillAlpha="{a}" android:pathData="{d}"/>')
            svg.append(f'<path fill="{INK}" fill-opacity="{a}" d="{d}"/>')

out = ROOT / "app/src/main/res/drawable/ic_launcher_foreground.xml"
out.write_text('<?xml version="1.0" encoding="utf-8"?>\n<!-- Written by tools/gen_launcher_icon.py: a box of nine tiles, inside the 66 dp safe zone. -->\n'
               '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
               '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
               + "\n".join(paths) + "\n</vector>\n")
pathlib.Path("/tmp/sd/icon.svg").write_text(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">'
    '<defs><clipPath id="m"><circle cx="54" cy="54" r="54"/></clipPath></defs>'
    '<g clip-path="url(#m)"><rect width="108" height="108" fill="#FFB156"/>' + "".join(svg) +
    '<circle cx="54" cy="54" r="33" fill="none" stroke="#0005" stroke-dasharray="2 2" stroke-width="0.4"/></g></svg>')
print("wrote", out)
