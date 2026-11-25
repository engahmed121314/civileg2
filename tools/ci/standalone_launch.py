"""Launch the installed APK from the launcher (no instrumentation) and capture screens.
Usage: standalone_launch.py PACKAGE OUTDIR"""
import subprocess, sys, time, re, os
import xml.etree.ElementTree as ET
pkg, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
def adb(*a, **k):
    return subprocess.run(["adb", *a], capture_output=True, **k)
def shot(name):
    open(f"{out}/{name}.png", "wb").write(adb("exec-out", "screencap", "-p").stdout)
def dump(name):
    adb("shell", "uiautomator", "dump", "/sdcard/w.xml")
    x = adb("shell", "cat", "/sdcard/w.xml").stdout.decode("utf8", "ignore")
    open(f"{out}/{name}.xml", "w").write(x)
    return x
def nodes(x):
    try: r = ET.fromstring(x)
    except Exception: return []
    res = []
    for n in r.iter("node"):
        t = (n.get("text") or "") + "|" + (n.get("content-desc") or "")
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
        if m: res.append((t, [int(v) for v in m.groups()]))
    return res
def tap_text(words, name):
    for _ in range(3):
        x = dump(name)
        for t, b in nodes(x):
            if any(w in t for w in words):
                adb("shell", "input", "tap", str((b[0]+b[2])//2), str((b[1]+b[3])//2))
                time.sleep(2); return True
        time.sleep(1)
    return False
adb("shell", "pm", "clear", pkg)
adb("shell", "monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1")
t0 = time.time(); i = 0
while time.time() - t0 < 9:
    shot(f"splash-{i:02d}-{time.time()-t0:04.1f}s"); i += 1
time.sleep(4); shot("home"); dump("home")
log = []
log.append(("design_tab", tap_text(["التصميم"], "d-design"))); shot("design")
log.append(("beam_card", tap_text(["كمر"], "d-beam"))); shot("beam-open"); dump("beam-open")
log.append(("calc", tap_text(["احسب", "حساب", "Calculate"], "d-calc"))); time.sleep(2)
shot("beam-blank-calc"); dump("beam-blank-calc")
open(f"{out}/steps.txt", "w").write("\n".join(f"{a}: {b}" for a, b in log))
