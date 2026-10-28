#!/usr/bin/env python3
"""Fills the *debug* build's game store with a plausible history, for screenshots of Stats and results.

Writes ~140 finished games over the last five months (Easy, Medium, Hard; mostly wins, a few losses;
times falling slowly; evenings; a current streak) into files/games/ through `run-as`, which only a
debuggable build allows. The puzzles are valid grids with plausible clue counts; their event logs are
synthetic but follow the app's own format, so every stat is computed by the app as usual.

Usage: tools/seed_demo_history.py SERIAL [--clear]
"""
import json, os, random, subprocess, sys, tempfile, time

SERIAL = sys.argv[1]
ADB = [os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"), "-s", SERIAL]
PKG = "app.materialsudoku.debug"
rng = random.Random(27)


def solved_grid():
    g = [0] * 81
    def ok(i, d):
        r, c = divmod(i, 9)
        for k in range(9):
            if g[r * 9 + k] == d or g[k * 9 + c] == d: return False
        br, bc = r // 3 * 3, c // 3 * 3
        return all(g[(br + a) * 9 + bc + b] != d for a in range(3) for b in range(3))
    def fill(i):
        if i == 81: return True
        ds = list(range(1, 10)); rng.shuffle(ds)
        for d in ds:
            if ok(i, d):
                g[i] = d
                if fill(i + 1): return True
        g[i] = 0
        return False
    fill(0)
    return g


CLUES = {0: (45, 41, 37), 1: (36, 33, 30), 2: (33, 30, 27)}
# Median seconds per game by (difficulty, length), early in the history; they improve ~25% by now.
BASE = {0: (85, 120, 165), 1: (260, 340, 430), 2: (520, 680, 840)}

now_ms = int(time.time() * 1000)
day = 86_400_000
games = []
for back in range(150, -1, -1):
    # A streak over the last 12 days; before that, most days.
    if back > 12 and rng.random() < 0.38: continue
    for _ in range(rng.choice([1, 1, 1, 2, 2, 3])):
        progress = 1 - back / 150
        d = rng.choices([0, 1, 2], [5, 4, 2])[0]
        ln = rng.choices([0, 1, 2], [2, 5, 2])[0]
        sol = solved_grid()
        clues = CLUES[d][ln]
        keep = set(rng.sample(range(81), clues))
        givens = [sol[i] if i in keep else 0 for i in range(81)]
        empties = [i for i in range(81) if givens[i] == 0]
        target = BASE[d][ln] * (1 - 0.25 * progress) * rng.lognormvariate(0, 0.22)
        lost = rng.random() < (0.03 if d == 0 else 0.07 if d == 1 else 0.12)
        hour = rng.choices(range(24), [1,0,0,0,0,0,1,2,3,2,2,2,3,3,2,2,3,4,6,8,10,9,6,3])[0]
        start = (now_ms // day - back) * day + hour * 3_600_000 + rng.randrange(3_600_000) - 4 * 3_600_000
        if start > now_ms - 600_000: start = now_ms - rng.randrange(3_600_000, 7_200_000)
        order = empties[:]; rng.shuffle(order)
        events, t, values, strikes = [], 0, givens[:], 0
        n_place = len(order) if not lost else rng.randrange(len(order) // 4, len(order) * 3 // 4)
        mistake_at = set(rng.sample(range(n_place), 3 if lost else rng.choice([0, 0, 0, 1, 1, 2])))
        for k, i in enumerate(order[:n_place]):
            gap = target * 1000 / len(order) * rng.lognormvariate(0, 0.6)
            t += int(gap)
            if k in mistake_at:
                wrong = rng.choice([x for x in range(1, 10) if x != sol[i]])
                events.append([t, "X", i, wrong]); strikes += 1
                t += int(gap * 0.4)
                if lost and strikes == 3: break
            events.append([t, "P", i, sol[i]]); values[i] = sol[i]
        status = "LOST" if lost else "WON"
        games.append({
            "v": 1, "id": "d" + format(start, "x"), "givens": "".join(map(str, givens)), "solution": "".join(map(str, sol)),
            "difficulty": d, "length": ln, "seed": rng.getrandbits(62), "generator": 3, "hardest": None, "famous": None,
            "flag": True, "lives": 3, "autorm": True, "band": 0, "startedAt": start, "status": status,
            "elapsed": t, "livesLeft": 3 - strikes if lost else 3 - strikes, "values": "".join(map(str, values)),
            "notes": [0] * 81, "events": events,
        })

sh = lambda *a: subprocess.run(ADB + list(a), capture_output=True, text=True)
if "--clear" in sys.argv:
    sh("shell", "pm", "clear", PKG)
    sh("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1"); time.sleep(4)
    sh("shell", "am", "force-stop", PKG)
with tempfile.TemporaryDirectory() as tmp:
    for g in games:
        p = os.path.join(tmp, g["id"] + ".json")
        open(p, "w").write(json.dumps(g))
    sh("shell", "rm", "-rf", "/data/local/tmp/seed"); sh("shell", "mkdir", "-p", "/data/local/tmp/seed")
    sh("push", tmp + "/.", "/data/local/tmp/seed/")
    sh("shell", f"run-as {PKG} sh -c 'mkdir -p files/games && cp /data/local/tmp/seed/*.json files/games/ && rm -f files/games/current.txt'")
print(len(games), "games,", sum(g["status"] == "WON" for g in games), "won")
