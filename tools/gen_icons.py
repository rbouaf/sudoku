#!/usr/bin/env python3
"""Regenerates ui/kit/Icons.kt: Material Symbols Rounded (Apache-2.0) path data.

Sources: the mockups' _icons.js plus every `ICONS.x = '...'` a board added, so the app draws the
exact glyphs the locked boards show. Anything else is fetched from google/material-design-icons.
Usage: python3 tools/gen_icons.py [extra_name ...]
"""
import json, pathlib, re, sys, urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
H = ROOT.parent / "sudoku-mockups/html"
icons = {}
for k, v in re.findall(r'^"([a-z_0-9]+)":\s*(\{[^\n]*\}|"[^"]*"),?$', (H / "_icons.js").read_text(), re.M):
    if v.startswith("{"):
        continue  # old 24×24 symbols; refetched below if needed
    icons[k] = json.loads(v)
for f in sorted((H / "src").glob("*.html")):
    for k, v in re.findall(r"ICONS\.([a-z_0-9]+) = '([^']*)'", f.read_text()):
        icons.setdefault(k, v)

def fetch(name):
    url = f"https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/{name}/materialsymbolsrounded/{name}_24px.svg"
    svg = urllib.request.urlopen(url).read().decode()
    assert 'viewBox="0 -960 960 960"' in svg, name
    return "".join(re.findall(r' d="([^"]+)"', svg))

WANT = ["undo", "ink_eraser", "lightbulb", "ink_pen", "apps", "arrow_back", "pause", "play_arrow", "play_fill",
        "tune", "visibility", "visibility_off", "restart_alt", "bar_chart", "settings", "add", "timer", "check",
        "close", "lock", "lock_open", "info", "chevron_right", "emoji_events", "edit", "done_all", "download",
        "history", "license", "contrast", "animation", "brightness_auto", "light_mode", "dark_mode", "vibration",
        "grid_on", "shield", "favorite", "touch_app", "palette", "home", "share", "content_copy", "replay",
        "local_fire_department", "bolt", "skip_next", "expand_more",
        "trending_up", "schedule", "flag", "military_tech", "star", "help"] + sys.argv[1:]
out = {}
for n in WANT:
    out[n] = icons.get(n) or fetch(n)
OUT = ROOT / "app/src/main/java/app/materialsudoku/ui/kit/Icons.kt"
body = "\n".join(f'        "{k}" to "{v}",' for k, v in sorted(out.items()))
OUT.write_text(f'''// Written by tools/gen_icons.py. Material Symbols Rounded 24px, Apache-2.0. Do not edit.
package app.materialsudoku.ui.kit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Material Symbols by name. The viewBox is the Symbols convention, 0 -960 960 960. */
object Sym {{
    private val paths = mapOf(
{body}
    )
    private val cache = HashMap<String, ImageVector>()

    operator fun get(name: String): ImageVector = cache.getOrPut(name) {{
        val d = paths[name] ?: error("No symbol $name; add it to tools/gen_icons.py")
        ImageVector.Builder(name, 24.dp, 24.dp, 960f, 960f).apply {{
            addGroup(translationY = 960f)
            addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
            clearGroup()
        }}.build()
    }}
}}
''')
print(len(out), "icons ->", OUT)
