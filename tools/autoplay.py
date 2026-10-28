#!/usr/bin/env python3
"""Plays the current debug game to the end by tapping, for checking the result screen.
Usage: tools/autoplay.py SERIAL [mistakes] [stop_after]  (reads the game file over run-as)."""
import json, subprocess, sys, time
ADB = [__import__("os").path.expanduser("~/Library/Android/sdk/platform-tools/adb"), "-s", sys.argv[1]]
mistakes = int(sys.argv[2]) if len(sys.argv) > 2 else 0
stop = int(sys.argv[3]) if len(sys.argv) > 3 else 999
sh = lambda c: subprocess.run(ADB + ["shell", c], capture_output=True, text=True).stdout
cur = sh("run-as app.materialsudoku.debug cat files/games/current.txt").strip()
g = json.loads(sh(f"run-as app.materialsudoku.debug cat files/games/{cur}.json"))
S = 1.2
cols = [60, 154, 248, 356, 450, 544, 651, 745, 839]
rows = [359, 453, 547, 655, 749, 843, 950, 1044, 1138]
keys = {d: ([247, 450, 653][(d - 1) % 3], [1394, 1597, 1800][(d - 1) // 3]) for d in range(1, 10)}
cmds = []
done = 0
for i, (v, s) in enumerate(zip(g["values"], g["solution"])):
    if v != "0": continue
    if done >= stop: break
    r, c = divmod(i, 9)
    cmds.append(f"input tap {int(cols[c]*S)} {int(rows[r]*S)}")
    if mistakes and done % 9 == 4:
        wrong = (int(s) % 9) + 1
        kx, ky = keys[wrong]; cmds.append(f"input tap {int(kx*S)} {int(ky*S)}"); cmds.append("sleep 0.8"); mistakes -= 1
    kx, ky = keys[int(s)]
    cmds.append(f"input tap {int(kx*S)} {int(ky*S)}")
    cmds.append("sleep 0.35")
    done += 1
sh("; ".join(cmds))
print("placed", done)
