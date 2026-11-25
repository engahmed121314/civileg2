"""Launch the installed APK from the launcher (no instrumentation) and capture screens.
Usage: standalone_launch.py PACKAGE OUTDIR"""
import subprocess, sys, time, re, os
import xml.etree.ElementTree as ET
pkg, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
def adb(*a, **k):
    return subprocess.run(["adb", *a], capture_output=True, **k)
# Pre-confirm the system "Viewing full screen" notice (it is not in the UI dump, so it cannot be tapped away).
adb("shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed")
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

# Stair: open from the Design tab, design with the defaults, open the drawing's reinforcement tab
def stair_block():
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)
    ok = False
    log.append(("stair_design_tab", tap_text(["التصميم"], "stair-d-design", exact=True)))
    scroll_top(3)
    for _ in range(5):
        if tap_text(["السلالم"], "stair-find"): ok = True; break
        scroll_up(2)
    log.append(("stair_open", ok)); time.sleep(2); shot("stair-open")
    for _ in range(6):
        if tap_text(["تصميم السلم الآن"], "stair-calc-find"): log.append(("stair_calc", True)); break
        scroll_up(2)
    else: log.append(("stair_calc", False))
    time.sleep(3)
    for k in range(4):
        scroll_up(2); shot(f"stair-res-{k}"); dump(f"stair-res-{k}")
    log.append(("stair_reinf_tab", tap_text(["تسليح"], "stair-reinf-tab", exact=True))); time.sleep(2)
    shot("stair-reinf-0"); dump("stair-reinf-0")
    scroll_up(1); shot("stair-reinf-1")
    for t in ("واجهة", "قطاع"):
        log.append(("stair_tab_" + t, tap_text([t], "stair-tab-" + t, exact=True))); time.sleep(2); shot("stair-tab-" + t)
def stair_unsupported():
    """Re-open Stair, pick the cantilever type (not implemented) and design; capture what is offered."""
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)
    tap_text(["التصميم"], "su-design", exact=True); scroll_top(3)
    ok = False
    for _ in range(5):
        if tap_text(["السلالم"], "su-find"): ok = True; break
        scroll_up(2)
    log.append(("su_open", ok)); time.sleep(2)
    log.append(("su_type_menu", tap_text(["قلبة واحدة", "Single Flight"], "su-type"))); time.sleep(1); shot("su-menu")
    log.append(("su_pick_cantilever", tap_text(["درج كابولي", "Cantilever"], "su-pick"))); time.sleep(1); shot("su-picked")
    for _ in range(6):
        if tap_text(["تصميم السلم الآن"], "su-calc-find"): log.append(("su_calc", True)); break
        scroll_up(2)
    else: log.append(("su_calc", False))
    time.sleep(3)
    shot("su-after-calc")
    open(f"{out}/su-crash-log.txt", "wb").write(adb("logcat", "-b", "crash", "-d").stdout)
    open(f"{out}/su-app-log.txt", "wb").write(adb("logcat", "-d", "-t", "4000", "-s", "AndroidRuntime:E", "ActivityManager:I", "DEBUG:I").stdout)
    for k in range(3):
        scroll_up(2); shot(f"su-res-{k}"); dump(f"su-res-{k}")
def stair_codes():
    """Open Stair again and design the default flight under the American and then the Saudi code."""
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)
    tap_text(["التصميم"], "sc-design", exact=True); scroll_top(3)
    ok = False
    for _ in range(5):
        if tap_text(["السلالم"], "sc-find"): ok = True; break
        scroll_up(2)
    log.append(("sc_open", ok)); time.sleep(2)
    for label, tag in (("American Code", "ACI"), ("Saudi Code", "SBC")):
        scroll_top(8)
        picked = False
        for _ in range(6):
            if tap_text([label], "sc-pick-" + tag): picked = True; break
            if _ % 2 == 0: scroll_up(1)
            else: scroll_top(8)
        log.append(("sc_pick_" + tag, picked)); time.sleep(1); shot("sc-" + tag + "-picked")
        for _ in range(6):
            if tap_text(["تصميم السلم الآن"], "sc-calc-" + tag): log.append(("sc_calc_" + tag, True)); break
            scroll_up(2)
        else: log.append(("sc_calc_" + tag, False))
        time.sleep(3)
        for k in range(4):
            scroll_up(2); shot(f"sc-{tag}-res-{k}"); dump(f"sc-{tag}-res-{k}")
        # Stair PDF export request for this lane (evidence of the export path, then dismiss)
        if tag == "SBC":
            pdf_ok = False
            for _ in range(8):
                if tap_text(["تقرير PDF", "PDF"], "sc-pdf-find"): pdf_ok = True; break
                scroll_top(2)
            log.append(("sc_pdf_tap_" + tag, pdf_ok)); time.sleep(4)
            # dismiss the system "Viewing full screen" notice so the viewer pages are visible
            tap_text(["Got it", "حسنًا", "حسناً"], "sc-pdf-gotit")
            shot("sc-" + tag + "-pdf"); dump("sc-" + tag + "-pdf")
            for k in range(1, 6):
                adb("shell", "input", "swipe", "540", "1900", "540", "500", "400"); time.sleep(1)
                shot("sc-" + tag + "-pdf-" + str(k))
            # Best effort: pull the generated PDF itself for text extraction
            fl = adb("shell", "find", "/sdcard", "/data/user/0/" + pkg, "-name", "Stair_Report*.pdf").stdout.decode(errors="ignore").split()
            for k2, fp in enumerate(fl[:2]):
                open(f"{out}/stair-report-{k2}.pdf", "wb").write(adb("exec-out", "run-as", pkg, "cat", fp).stdout if fp.startswith("/data") else adb("exec-out", "cat", fp).stdout)
            open(f"{out}/stair-report-paths.txt", "w").write("\n".join(fl))
            adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)  # leave the PDF viewer
            shot("sc-" + tag + "-pdf-closed")
    open(f"{out}/sc-crash-log.txt", "wb").write(adb("logcat", "-b", "crash", "-d").stdout)
def stair_invalid():
    """Re-open Stair, set the flight width to 0 (invalid) and press design; capture the field error and whether a result appears."""
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)
    tap_text(["التصميم"], "inv-design", exact=True); scroll_top(3)
    ok = False
    for _ in range(5):
        if tap_text(["السلالم"], "inv-find"): ok = True; break
        scroll_up(2)
    log.append(("inv_open", ok)); time.sleep(2); scroll_top(4)
    log.append(("inv_edit_width_0", edit_field("1200", "0", "inv-edit"))); time.sleep(1); shot("inv-field")
    for _ in range(6):
        if tap_text(["تصميم السلم الآن"], "inv-calc"): log.append(("inv_calc", True)); break
        scroll_up(2)
    else: log.append(("inv_calc", False))
    time.sleep(3); shot("inv-after-calc"); dump("inv-after-calc")
    for k in range(2):
        scroll_up(2); shot(f"inv-res-{k}"); dump(f"inv-res-{k}")
    scroll_top(6); shot("inv-top"); dump("inv-top")
def frame_block():
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)
    tap_text(["التصميم"], "fr-d-design", exact=True); scroll_top(3)
    ok = False
    for _ in range(10):
        if tap_text(["تحليل الإطارات"], "fr-find"): ok = True; break
        scroll_up(1)
    log.append(("frame_open", ok)); time.sleep(3); shot("frame-open"); dump("frame-open")
    log.append(("frame_hint_expand", tap_text(["إظهار تلميح الأداة"], "fr-hint"))); time.sleep(1); shot("frame-hint-open"); dump("frame-hint-open")
    log.append(("frame_hint_collapse", tap_text(["اضغط للإخفاء"], "fr-hint2"))); time.sleep(1); shot("frame-hint-closed")
    # Explicit small model (NOT the user's frame): built-in simple portal template, 6 m span, 4 m height, 20 kN/m
    log.append(("frame_menu", tap_text(["Actions"], "fr-actions"))); time.sleep(1)
    log.append(("frame_templates", tap_text(["Templates"], "fr-templates"))); time.sleep(1)
    log.append(("frame_portal", tap_text(["إطار بوابة بسيط"], "fr-portal"))); time.sleep(2); shot("frame-portal"); dump("frame-portal")
    log.append(("frame_solve", tap_text(["تحليل"], "fr-solve", exact=True))); time.sleep(5); shot("frame-solved"); dump("frame-solved")
    time.sleep(5); shot("frame-solved-late"); dump("frame-solved-late")
    log.append(("frame_results_tab", tap_text(["النتائج"], "fr-results", exact=True))); time.sleep(3); shot("frame-results"); dump("frame-results")
    log.append(("frame_back_draw", tap_text(["الرسم"], "fr-draw", exact=True))); time.sleep(2)
    for t in ("SFD", "AFD", "BMD"):
        log.append(("frame_diag_" + t, tap_text([t], "fr-diag-" + t, exact=True))); time.sleep(2); shot("frame-diag-" + t)
    log.append(("frame_hint_expand2", tap_text(["إظهار تلميح الأداة"], "fr-hint3"))); time.sleep(1); shot("frame-portal-hint")
    log.append(("frame_view_tab", tap_text(["العرض والنتائج"], "fr-viewtab"))); time.sleep(3); shot("frame-view-BMD"); dump("frame-view-BMD")
    for t in ("SFD", "AFD"):
        log.append(("frame_view_" + t, tap_text([t], "fr-view-" + t, exact=True))); time.sleep(2); shot("frame-view-" + t)
if os.path.exists("tools/ci/STAIR_ONLY"):
    try:
        stair_block()
        stair_unsupported()
        stair_codes()
        frame_block()
        stair_invalid()
    except Exception as e:
        log.append(("stair_exception", repr(e)))
    open(f"{out}/steps.txt", "w").write("\n".join(f"{a}: {b}" for a, b in log)); sys.exit(0)
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
def calc_hordi(prefix):
    scroll_top(8)
    ok = tap_text(["تصميم البلاطة"], prefix + "-calc-find"); log.append((prefix + "_calc", ok)); time.sleep(3)
    sweep(prefix + "-res", 5)
try:
    open_from_design("بلاطة هوردي", "hordi")
    calc_hordi("hordi-ecp")
    pdf_try("hordi-ecp")
    scroll_top(8)
    log.append(("hordi_saudi", tap_text(["Saudi Code", "الكود السعودي"], "hordi-saudi-find"))); time.sleep(1)
    calc_hordi("hordi-sbc")
    scroll_top(8)
    log.append(("hordi_american", tap_text(["American Code", "الكود الأمريكي"], "hordi-aci-find"))); time.sleep(1)
    calc_hordi("hordi-aci")
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
open(f"{out}/steps.txt", "w").write("\n".join(f"{a}: {b}" for a, b in log))
