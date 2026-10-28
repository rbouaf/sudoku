#!/bin/sh
# Fetches the two glyphs 数独 from Noto Sans JP (OFL) at wght 800 as a TTF subset for the Home title.
# Google serves TTF to a non-browser user agent; the subset is ~2 KB.
set -e
cd "$(dirname "$0")/.."
URL=$(curl -s "https://fonts.googleapis.com/css2?family=Noto+Sans+JP:wght@800&text=%E6%95%B0%E7%8B%AC" | grep -o 'https://[^)]*')
curl -s "$URL" -o app/src/main/res/font/noto_sans_jp_title.ttf
ls -l app/src/main/res/font/noto_sans_jp_title.ttf
