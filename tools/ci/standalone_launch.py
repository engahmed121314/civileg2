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
def tap_text(words, name, exact=False):
    for _ in range(3):
        x = dump(name)
        ns = nodes(x)
        if exact: ns = sorted(ns, key=lambda nb: -nb[1][1])  # bottom-most first (tab bar)
        for t, b in ns:
            lab = t.split("|")[0].strip()
            if (lab in words) if exact else any(w in t for w in words):
                adb("shell", "input", "tap", str((b[0]+b[2])//2), str((b[1]+b[3])//2))
                time.sleep(2); return True
        time.sleep(1)
    return False
adb("shell", "pm", "clear", pkg)
adb("shell", "monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1")
t0 = time.time(); i = 0
while time.time() - t0 < 14:
    shot(f"splash-{i:02d}-{time.time()-t0:04.1f}s"); i += 1
time.sleep(4); shot("home"); dump("home")
log = []
log.append(("design_tab", tap_text(["التصميم"], "d-design", exact=True))); shot("design")
log.append(("beam_card", tap_text(["كمر"], "d-beam"))); shot("beam-open"); dump("beam-open")
def scroll_up(n=3):
    for _ in range(n):
        adb("shell", "input", "swipe", "540", "1900", "540", "700", "300"); time.sleep(0.6)
def scroll_top(n=4):
    for _ in range(n):
        adb("shell", "input", "swipe", "540", "700", "540", "1900", "300"); time.sleep(0.4)
def edit_field(current, new, name):
    """Tap the EditText whose text equals `current`, clear it, type `new`."""
    for t, b in nodes(dump(name)):
        if t == current + "|" and b[3] - b[1] > 100:
            adb("shell", "input", "tap", str((b[0]+b[2])//2), str((b[1]+b[3])//2)); time.sleep(0.8)
            adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
            for _ in range(12): adb("shell", "input", "keyevent", "KEYCODE_DEL")
            if new: adb("shell", "input", "text", new)
            if b"mInputShown=true" in adb("shell", "dumpsys", "input_method").stdout:
                adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(0.8)
            return True
    return False
def press_calc(name):
    for _ in range(2):
        if tap_text(["تصميم الكمرة الآن", "تحليل وحساب", "احسب", "Calculate"], name): return True
        scroll_up(2)
    return False
# 1) blank width
log.append(("clear_width", edit_field("250", "", "d-w0"))); shot("beam-width-blank")
scroll_up(4); shot("beam-after-scroll")
log.append(("calc_blank", press_calc("d-calc1"))); time.sleep(2); shot("beam-blank-calc"); dump("beam-blank-calc")
# 2) fill width and solve
scroll_top()
log.append(("type_width", edit_field("", "250", "d-w1")))
scroll_up(4)
log.append(("calc_solved", press_calc("d-calc2"))); time.sleep(3); shot("beam-solved-1"); scroll_up(3); shot("beam-solved-2"); dump("beam-solved-2")
# 3) edit an input after solving: result must be invalidated
scroll_top(6)
log.append(("edit_after_solve", edit_field("600", "650", "d-w2"))); time.sleep(1)
scroll_up(4); shot("beam-after-edit"); dump("beam-after-edit")
scroll_up(6); shot("beam-after-edit-bottom"); dump("beam-after-edit-bottom")

# 4) steel and seismic screens: capture the first screens and a scrolled sweep
def sweep(prefix, n=5):
    for k in range(n):
        shot(f"{prefix}-{k}"); dump(f"{prefix}-{k}")
        scroll_up(2)
def open_from_design(label, prefix):
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)
    tap_text(["التصميم"], f"{prefix}-tab", exact=True); scroll_top(3)
    ok = False
    for _ in range(4):
        if tap_text([label], f"{prefix}-find"): ok = True; break
        scroll_up(2)
    log.append((prefix + "_open", ok)); time.sleep(2)
    sweep(prefix)
open_from_design("المنشآت المعدنية", "steel")
open_from_design("الزلازل", "seismic")
log.append(("seismic_calc", tap_text(["تحليل القص عند القاعدة"], "seismic-calc-find"))); time.sleep(3)
sweep("seismic-res", 6)
open_from_design("المنشآت المعدنية", "steel2")
log.append(("steel_conn_tab", tap_text(["الوصلات المعدنية"], "steel-conn-find"))); time.sleep(2)
sweep("steelconn", 6)
# 5) steel connection required-input scenarios (bolted shear: clear Vu, then slip-critical, then weld)
scroll_top(6); scroll_up(3)
log.append(("bolt_vu_clear", edit_field("100", "", "steel-vu0"))); shot("steel-bolt-vu-blank")
log.append(("bolt_calc_blank", tap_text(["تصميم الوصلة"], "steel-bolt-calc1"))); time.sleep(2)
scroll_up(4); shot("steel-bolt-calc-blank"); dump("steel-bolt-calc-blank")
def calc_connection(prefix):
    # fill the only empty required field (Vu was cleared), calculate, scroll the result fully into view
    log.append((prefix + "_vu_fill", edit_field("", "100", prefix + "-vu"))); time.sleep(0.5)
    scroll_top(6); scroll_up(3)
    log.append((prefix + "_calc", tap_text(["تصميم الوصلة"], prefix + "-calc"))); time.sleep(2)
    for k in range(3):
        scroll_up(3); shot(f"{prefix}-result-{k}"); dump(f"{prefix}-result-{k}")
scroll_top(6)
log.append(("slip_tab", tap_text(["Slip-Critical"], "steel-slip-find"))); time.sleep(1.5)
shot("steel-slip-0"); dump("steel-slip-0")
scroll_up(2); shot("steel-slip-error-gone"); dump("steel-slip-error-gone")
calc_connection("steel-slip")
scroll_top(6)
log.append(("weld_tab", tap_text(["وصلة لحام"], "steel-weld-find"))); time.sleep(1.5)
shot("steel-weld-0"); dump("steel-weld-0")
calc_connection("steel-weld")

# 6) Hordi slab: ECP default, then Saudi and American selectors, each calculated; then real PDF exports and a reopen check
def pdf_try(prefix):
    for _ in range(6):
        if tap_text(["PDF"], prefix + "-pdf-find"): break
        scroll_up(2)
    time.sleep(5); shot(prefix + "-pdf-after"); dump(prefix + "-pdf-after")
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5); shot(prefix + "-pdf-back")
def fill_cover(prefix):
    scroll_top(8)
    for _ in range(8):
        if tap_text(["Actual cover"], prefix + "-cover-find"):
            adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
            for _ in range(6): adb("shell", "input", "keyevent", "KEYCODE_DEL")
            adb("shell", "input", "text", "30")
            if b"mInputShown=true" in adb("shell", "dumpsys", "input_method").stdout:
                adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(0.8)
            shot(prefix + "-cover-filled"); log.append((prefix + "_cover", True)); return
        scroll_up(1)
    log.append((prefix + "_cover", False))
def calc_hordi(prefix):
    fill_cover(prefix)
    scroll_up(12)
    ok = tap_text(["تصميم البلاطة"], prefix + "-calc-find"); log.append((prefix + "_calc", ok)); time.sleep(3)
    sweep(prefix + "-res", 5)
try:
    open_from_design("بلاطة هوردي", "hordi")
    calc_hordi("hordi-ecp")
    scroll_top(8); sweep("hordi-ecp-top", 1)
    pdf_try("hordi-ecp")
    scroll_top(30)
    log.append(("hordi_saudi", tap_text(["SBC 304-CR-2018"], "hordi-saudi-find"))); time.sleep(1)
    shot("hordi-sbc-chip"); calc_hordi("hordi-sbc")
    scroll_top(30)
    log.append(("hordi_american", tap_text(["ACI 318M-14"], "hordi-aci-find"))); time.sleep(1)
    shot("hordi-aci-chip"); calc_hordi("hordi-aci")
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)
    open_from_design("بلاطة هوردي", "hordi-reopen")
except Exception as e:
    log.append(("hordi_exception", repr(e)))
# files the app wrote (debug build: run-as works)
ls = adb("shell", "run-as", pkg, "find", ".", "-name", "*.pdf", "-o", "-name", "*.dxf").stdout.decode("utf8", "ignore")
ls += adb("shell", "find", f"/sdcard/Android/data/{pkg}", "-name", "*.pdf").stdout.decode("utf8", "ignore")
open(f"{out}/pdf-files.txt", "w").write(ls)
for p in [l.strip() for l in ls.splitlines() if l.strip().startswith("./")][:4]:
    d = adb("shell", "run-as", pkg, "cat", p).stdout
    open(f"{out}/exported-" + os.path.basename(p), "wb").write(d)

# 7) 2D tab: open it, capture screens, scroll, try quantities
try:
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)
    log.append(("plan2d_tab", tap_text(["مخطط 2D"], "plan2d-tab", exact=True))); time.sleep(3)
    sweep("plan2d", 6)
except Exception as e:
    log.append(("plan2d_exception", repr(e)))
open(f"{out}/steps.txt", "w").write("\n".join(f"{a}: {b}" for a, b in log))
