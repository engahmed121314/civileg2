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
time.sleep(4)
for _ in range(4):
    if not tap_text(["Wait"], "anr-wait0", exact=True): break
    time.sleep(2)
shot("home"); dump("home")
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

def open_stair_from_hub(tag):
    """Reach the Design hub (retry BACK until its title is on screen, no blind single BACK), then open the stair card.
    The card title is matched exactly (the subtitle also contains the word) so the tap lands on the card."""
    hub = False
    for _ in range(4):
        # a "Viewing full screen" tip left over by the PDF viewer hides the hub until it is dismissed
        tap_text(["Got it", "حسنًا", "حسناً"], tag + "-gotit")
        adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)
        tap_text(["Got it", "حسنًا", "حسناً"], tag + "-gotit2")
        tap_text(["التصميم"], tag + "-design", exact=True)
        if any("اختر وحدة التصميم" in t for t, b in nodes(dump(tag + "-hubcheck"))): hub = True; break
    log.append((tag + "_hub", hub))
    scroll_top(3)
    ok = False
    for _ in range(8):
        if tap_text(["السلالم"], tag + "-find", exact=True): ok = True; break
        scroll_up(1)
    return ok

# Stair: open from the Design tab, design with the defaults, open the drawing's reinforcement tab
def stair_block():
    ok = open_stair_from_hub("stair")
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
    ok = open_stair_from_hub("su")
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
    ok = open_stair_from_hub("sc")
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
            for _ in range(14):
                if tap_text(["تقرير PDF", "PDF"], "sc-pdf-find"): pdf_ok = True; break
                scroll_top(1)  # one swipe per try: two at a time skipped past the button
            log.append(("sc_pdf_tap_" + tag, pdf_ok)); time.sleep(4)
            # dismiss the system "Viewing full screen" notice so the viewer pages are visible
            if not tap_text(["Got it", "حسنًا", "حسناً"], "sc-pdf-gotit"):
                # the notice is a system overlay missing from the dump: tap where its "Got it" sits (79% x, 27% y)
                wm = adb("shell", "wm", "size").stdout.decode(errors="ignore")
                mm = re.search(r"(\d+)x(\d+)", wm)
                if mm:
                    adb("shell", "input", "tap", str(int(int(mm.group(1)) * 0.79)), str(int(int(mm.group(2)) * 0.27))); time.sleep(1.5)
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
    ok = open_stair_from_hub("inv")
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
def stale_block(label, prefix, calc_words, calc_exact, field, newval, badval=None):
    """Open a page, solve with default inputs, edit one input, and prove the old result disappears."""
    try:
        restart_app()
        tap_text(["التصميم"], prefix + "-tab", exact=True); scroll_top(3)
        found = False
        for _ in range(6):
            if tap_text([label], prefix + "-find"): found = True; break
            scroll_up(2)
        log.append((prefix + "_open", found)); time.sleep(2)
        shot(prefix + "-open")
        scroll_top(4)
        ok = False
        for _ in range(8):
            if tap_text(calc_words, prefix + "-calc-find", exact=calc_exact): ok = True; break
            scroll_up(2)
        log.append((prefix + "_calc", ok)); time.sleep(5)
        shot(prefix + "-solved"); dump(prefix + "-solved")
        for k in (1, 2, 3):
            scroll_up(3); time.sleep(1); shot(prefix + f"-solved-r{k}"); dump(prefix + f"-solved-r{k}")
        scroll_top(8)
        log.append((prefix + "_edit", edit_field(field, newval, prefix + "-edit"))); time.sleep(1.5)
        shot(prefix + "-edited-top")
        scroll_up(4); time.sleep(1)
        shot(prefix + "-edited"); dump(prefix + "-edited")
        scroll_up(4); shot(prefix + "-edited-2"); dump(prefix + "-edited-2")
        if badval is not None:
            scroll_top(8)
            log.append((prefix + "_bad_edit", edit_field(newval, badval, prefix + "-bad"))); time.sleep(1)
            for _ in range(8):
                if tap_text(calc_words, prefix + "-bad-calc", exact=calc_exact): break
                scroll_up(2)
            time.sleep(3); shot(prefix + "-invalid"); dump(prefix + "-invalid")
            scroll_top(8); shot(prefix + "-invalid-top")
    except Exception as e:
        log.append((prefix + "_stale_exception", repr(e)))
def dismiss_not_responding():
    """The emulator launcher sometimes shows an 'isn't responding' dialog; choose Wait so it cannot cover the app."""
    for _ in range(2):
        if not tap_text(["Wait"], "anr-wait", exact=True): break
        time.sleep(1.5)
def restart_app():
    adb("shell", "am", "force-stop", pkg); time.sleep(2)
    adb("shell", "monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1"); time.sleep(8)
    dismiss_not_responding()
def pile_block():
    """Pile screen: default inputs, design, status card sweep, PDF request; exported PDFs are pulled afterwards."""
    try:
        restart_app()
        tap_text(["التصميم"], "pile-tab", exact=True); scroll_top(3)
        ok = False
        for _ in range(6):
            if tap_text(["الخوازيق"], "pile-find"): ok = True; break
            scroll_up(2)
        log.append(("pile_open", ok)); time.sleep(2)
        shot("pile-open"); dump("pile-open")
        for _ in range(8):
            if tap_text(["تصميم الأساسات العميقة"], "pile-calc-find"): log.append(("pile_calc", True)); break
            scroll_up(2)
        else: log.append(("pile_calc", False))
        time.sleep(4)
        for k in range(5):
            shot(f"pile-res-{k}"); dump(f"pile-res-{k}"); scroll_up(2)
        pdf_ok = False
        for _ in range(10):
            if tap_text(["PDF"], "pile-pdf-find"): pdf_ok = True; break
            scroll_top(1)
        log.append(("pile_pdf_tap", pdf_ok)); time.sleep(6)
        shot("pile-pdf-after"); dump("pile-pdf-after")
        adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)
    except Exception as e:
        log.append(("pile_exception", repr(e)))
def pull_exported_pdfs():
    ls = adb("shell", "run-as", pkg, "find", ".", "-name", "*.pdf", "-o", "-name", "*.dxf").stdout.decode("utf8", "ignore")
    ls += adb("shell", "find", f"/sdcard/Android/data/{pkg}", "-name", "*.pdf").stdout.decode("utf8", "ignore")
    open(f"{out}/pdf-files.txt", "w").write(ls)
    for pth in [l.strip() for l in ls.splitlines() if l.strip().startswith("/sdcard/")][:4]:
        subprocess.run(["adb", "pull", pth, f"{out}/exported-" + os.path.basename(pth)], capture_output=True)
    for pth in [l.strip() for l in ls.splitlines() if l.strip().startswith("./")][:4]:
        d = adb("shell", "run-as", pkg, "cat", pth).stdout
        open(f"{out}/exported-" + os.path.basename(pth), "wb").write(d)
def footing_block():
    """Footing only: open the isolated footing from the Design tab, design with defaults, sweep the result, export the PDF."""
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)  # leave the Beam screen opened by the common prologue
    tap_text(["التصميم"], "ft-design", exact=True); scroll_top(3)
    ok = False
    for _ in range(6):
        if tap_text(["القواعد"], "ft-find", exact=True): ok = True; break
        scroll_up(2)
    log.append(("footing_open", ok)); time.sleep(2); shot("footing-open"); dump("footing-open")
    # Ps is a required, blank project input. First press with it blank must show the field error; then a demo value (800 kN) is typed.
    for _ in range(8):
        if tap_text(["تصميم القاعدة الآن"], "ft-calc-blank"): log.append(("footing_calc_blank_ps", True)); break
        scroll_up(2)
    else: log.append(("footing_calc_blank_ps", False))
    time.sleep(2); shot("footing-blank-ps"); dump("footing-blank-ps")
    log.append(("footing_type_ps_demo_800", edit_field("", "800", "ft-ps"))); time.sleep(1); shot("footing-ps-typed"); dump("footing-ps-typed")
    for _ in range(8):
        if tap_text(["تصميم القاعدة الآن"], "ft-calc-find"): log.append(("footing_calc", True)); break
        scroll_up(2)
    else: log.append(("footing_calc", False))
    time.sleep(4)
    for k in range(8):
        scroll_up(2); shot(f"footing-res-{k}"); dump(f"footing-res-{k}")
    pdf_ok = False
    for _ in range(14):
        if tap_text(["تقرير PDF"], "ft-pdf-find"): pdf_ok = True; break
        scroll_top(1)
    log.append(("footing_pdf_tap", pdf_ok)); time.sleep(6)
    if not tap_text(["Got it", "حسنًا", "حسناً"], "ft-pdf-gotit"):
        wm = adb("shell", "wm", "size").stdout.decode(errors="ignore")
        mm = re.search(r"(\d+)x(\d+)", wm)
        if mm:
            adb("shell", "input", "tap", str(int(int(mm.group(1)) * 0.79)), str(int(int(mm.group(2)) * 0.27))); time.sleep(1.5)
    shot("footing-pdf"); dump("footing-pdf")
    for k in range(1, 5):
        adb("shell", "input", "swipe", "540", "1900", "540", "500", "400"); time.sleep(1)
        shot("footing-pdf-" + str(k))
    fl = adb("shell", "find", "/sdcard", "/data/user/0/" + pkg, "-name", "Footing_Report*.pdf").stdout.decode(errors="ignore").split()
    for k2, fp in enumerate(fl[:2]):
        open(f"{out}/footing-report-{k2}.pdf", "wb").write(adb("exec-out", "run-as", pkg, "cat", fp).stdout if fp.startswith("/data") else adb("exec-out", "cat", fp).stdout)
    open(f"{out}/footing-report-paths.txt", "w").write("\n".join(fl))
    open(f"{out}/footing-crash-log.txt", "wb").write(adb("logcat", "-b", "crash", "-d").stdout)
def edge_up(n=2):
    # Swipe at the left screen edge: the drawing canvas in the middle consumes vertical drags.
    for _ in range(n):
        adb("shell", "input", "swipe", "30", "1900", "30", "700", "300"); time.sleep(0.6)
def edge_top(n=4):
    for _ in range(n):
        adb("shell", "input", "swipe", "30", "700", "30", "1900", "300"); time.sleep(0.4)
def steel_up(n=2):
    # Steel page: swipe near the right edge (the left edge is not scrolling this page, the middle can be taken by the drawing).
    for _ in range(n):
        adb("shell", "input", "swipe", "1000", "1900", "1000", "700", "300"); time.sleep(0.6)
def steel_down(n=1):
    for _ in range(n):
        adb("shell", "input", "swipe", "1000", "700", "1000", "1900", "300"); time.sleep(0.4)
def steel_block():
    """Steel page: open it from the Design tab, sweep the warehouse inputs, analyse with the defaults, sweep results, export the PDF, then sweep the member design tab."""
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)
    tap_text(["التصميم"], "st-design", exact=True); edge_top(3)
    ok = False
    for _ in range(6):
        if tap_text(["المنشآت المعدنية"], "st-find"): ok = True; break
        scroll_up(2)
    log.append(("steel_open", ok)); time.sleep(3); shot("steel-open"); dump("steel-open")
    import os as _os
    _marker = "tools/ci/steel_code.txt"
    _steel_code = open(_marker).read().strip() if _os.path.exists(_marker) else "ACI"
    log.append(("steel_code_selected", _steel_code))
    if _steel_code == "ACI":
        ac = tap_text(["American Code"], "st-code-aci"); time.sleep(1.0)   # chip is at the top of the first screen
        log.append(("steel_code_american", ac)); shot("steel-code-american")
    # ECP: the Egyptian Code is the default selection, no chip tap
    for k in range(3):
        steel_up(2); shot(f"steel-in-{k}"); dump(f"steel-in-{k}")
    # Required footing input: allowable soil bearing 150 kN/m2 typed by the evidence run (a stated test value, not an app default).
    # Bracing inputs typed as stated test values (braced bays 1,5,10 and H = 40 kN per braced panel); the app has no defaults for H.
    # American Code selected so the AISC 360-10 (14th ed. Manual) D2 tension inputs appear; Fy 250, Fu 400, Ae 1500 are stated test values.
    for lab, val, nm in (("Brace Fy", "250", "st-fy"), ("Brace Fu", "400", "st-fu"), ("Brace net area", "1500", "st-ae")):
        if tap_text([lab], nm):
            adb("shell", "input", "text", val); time.sleep(0.8)
            adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.0)
        else: log.append(("steel_" + nm + "_missing", True))
    bb = tap_text(["Braced bays"], "st-bb")
    if bb:
        adb("shell", "input", "text", "1,5,10"); time.sleep(0.8)
        adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.0)
    hl = tap_text(["Lateral load H"], "st-hl")
    if hl:
        adb("shell", "input", "text", "40"); time.sleep(0.8)
        adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.0)
    shot("steel-bracing-typed"); dump("steel-bracing-typed")
    log.append(("steel_bracing_typed", bool(bb) and bool(hl)))
    soil = tap_text(["Allowable soil bearing"], "st-soil")
    if soil:
        adb("shell", "input", "text", "150"); time.sleep(0.8)
        adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.0)
        shot("steel-soil-typed"); dump("steel-soil-typed")
    log.append(("steel_soil_typed", soil))
    for _ in range(8):
        if tap_text(["تحليل الهنجر"], "st-calc-find"): log.append(("steel_calc", True)); break
        steel_up(2)
    else: log.append(("steel_calc", False))
    time.sleep(8)
    for k in range(26):
        shot(f"steel-res-{k}"); dump(f"steel-res-{k}")
        adb("shell", "input", "swipe", "1000", "1700", "1000", "1000", "300"); time.sleep(0.7)
    pdf_ok = False
    for _ in range(14):
        if tap_text(["إنشاء تقرير PDF"], "st-pdf-find"): pdf_ok = True; break
        steel_down(1)
    log.append(("steel_pdf_tap", pdf_ok)); time.sleep(2); shot("steel-pdf-dialog"); dump("steel-pdf-dialog")
    log.append(("steel_export_now", tap_text(["تصدير الآن"], "st-export-now"))); time.sleep(8)
    if not tap_text(["Got it", "حسنًا", "حسناً"], "st-pdf-gotit"):
        wm = adb("shell", "wm", "size").stdout.decode(errors="ignore")
        mm = re.search(r"(\d+)x(\d+)", wm)
        if mm:
            adb("shell", "input", "tap", str(int(int(mm.group(1)) * 0.79)), str(int(int(mm.group(2)) * 0.27))); time.sleep(1.5)
    shot("steel-pdf"); dump("steel-pdf")
    for k in range(1, 6):
        adb("shell", "input", "swipe", "540", "1900", "540", "500", "400"); time.sleep(1)
        shot("steel-pdf-" + str(k))
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(2)
    # Member design tab
    edge_top(8)
    log.append(("steel_member_tab", tap_text(["تصميم القطاعات"], "st-member-tab"))); time.sleep(3)
    for k in range(6):
        shot(f"steel-member-{k}"); dump(f"steel-member-{k}"); steel_up(2)
    open(f"{out}/steel-crash-log.txt", "wb").write(adb("logcat", "-b", "crash", "-d").stdout)
if os.path.exists("tools/ci/STEEL_ONLY"):
    try:
        steel_block()
    except Exception as e:
        log.append(("steel_exception", repr(e)))
    pull_exported_pdfs()
    open(f"{out}/steps.txt", "w").write("\n".join(f"{a}: {b}" for a, b in log)); sys.exit(0)
def sweep(prefix, n=5):
    for k in range(n):
        shot(f"{prefix}-{k}"); dump(f"{prefix}-{k}")
        scroll_up(2)
def tap_checkable(name):
    """Tap the first Switch-like (checkable) node on screen; used for the flat slab drop panel toggle."""
    for t, b in nodes_attr(dump(name), "checkable"):
        adb("shell", "input", "tap", str((b[0]+b[2])//2), str((b[1]+b[3])//2)); time.sleep(1.5); return True
    return False
def nodes_attr(x, attr):
    try: r = ET.fromstring(x)
    except Exception: return []
    res = []
    for n in r.iter("node"):
        if n.get(attr) == "true":
            m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds") or "")
            if m: res.append((n.get("text") or "", [int(v) for v in m.groups()]))
    return res
def flatslab_drop_block():
    """Flat slab with a drop panel (100 mm below the slab, 2500 x 2500 mm): sweep the result and export the PDF."""
    dismiss_not_responding()
    adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5); dismiss_not_responding()
    tap_text(["التصميم"], "fd-tab", exact=True); scroll_top(3)
    ok = False
    for _ in range(5):
        if tap_text(["البلاطات المسطحة"], "fd-find"): ok = True; break
        scroll_up(2)
    log.append(("fsdrop_open", ok)); time.sleep(2); dismiss_not_responding()
    scroll_top(2)
    log.append(("fsdrop_switch", tap_checkable("fd-switch")))
    time.sleep(1); dismiss_not_responding(); shot("fsdrop-form"); dump("fsdrop-form")
    scroll_up(1)
    got = [edit_field("0", "100", "fd-thk"), edit_field("0", "2500", "fd-x"), edit_field("0", "2500", "fd-y")]
    log.append(("fsdrop_fields", got)); time.sleep(1); shot("fsdrop-typed"); dump("fsdrop-typed")
    calc = False
    for _ in range(5):
        if tap_text(["تصميم بلاطة مسطحة"], "fd-calc"): calc = True; break
        scroll_up(2)
    log.append(("fsdrop_calc", calc)); time.sleep(4); dismiss_not_responding()
    sweep("fsdrop-res", 9)
    pdf_ok = False
    for _ in range(8):
        if tap_text(["PDF", "تقرير"], "fd-pdf-find"): pdf_ok = True; break
        scroll_up(2)
    log.append(("fsdrop_pdf_tap", pdf_ok)); time.sleep(6)
    if not tap_text(["Got it", "حسنًا", "حسناً"], "fd-pdf-gotit"):
        wm = adb("shell", "wm", "size").stdout.decode(errors="ignore")
        mm = re.search(r"(\d+)x(\d+)", wm)
        if mm:
            adb("shell", "input", "tap", str(int(int(mm.group(1)) * 0.79)), str(int(int(mm.group(2)) * 0.27))); time.sleep(1.5)
    shot("fsdrop-pdf"); dump("fsdrop-pdf")
    for k in range(1, 5):
        adb("shell", "input", "swipe", "540", "1900", "540", "500", "400"); time.sleep(1)
        shot("fsdrop-pdf-" + str(k))
def flatslab_vary_block():
    """Same flat slab screen, different inputs: the check cards must change values and PASS/FAIL with the inputs."""
    cases = [
        ("fsv1", [("6.0", "9.0", "fv-lx"), ("7.5", "9.0", "fv-ly")]),
        ("fsv2", [("250", "160", "fv-thk")]),
        ("fsv3", [("3.0", "8", "fv-ll"), ("400", "500", "fv-fy"), ("30", "40", "fv-fcu")]),
    ]
    for tag, edits in cases:
        try:
            dismiss_not_responding()
            adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5); dismiss_not_responding()
            tap_text(["التصميم"], tag + "-tab", exact=True); scroll_top(3)
            ok = False
            for _ in range(5):
                if tap_text(["البلاطات المسطحة"], tag + "-find"): ok = True; break
                scroll_up(2)
            log.append((tag + "_open", ok)); time.sleep(2); scroll_top(2)
            for cur, new, nm in edits:
                done = False
                for _ in range(6):
                    if edit_field(cur, new, nm): done = True; break
                    scroll_up(1)
                log.append((tag + "_" + nm, done))
            calc = False
            for _ in range(6):
                if tap_text(["تصميم بلاطة مسطحة"], tag + "-calc"): calc = True; break
                scroll_up(2)
            log.append((tag + "_calc", calc)); time.sleep(4); dismiss_not_responding()
            sweep(tag + "-res", 9)
        except Exception as e:
            log.append((tag + "_exception", repr(e)))
def font_extra_block():
    """Open Steel (design hub), Wind and Concrete mix (tools hub), try the calculate button, sweep (font pixel proof)."""
    items = [("steel", "التصميم", "المنشآت المعدنية", ["تحليل وحساب", "تصميم", "احسب"]),
             ("wind", "صندوق الأدوات", "أحمال الرياح", ["احسب", "حساب", "تحليل"]),
             ("mix", "صندوق الأدوات", "خلطة الخرسانة", ["احسب", "تصميم الخلطة", "حساب"])]
    for tag, tab, label, calc in items:
        try:
            dismiss_not_responding()
            adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5); dismiss_not_responding()
            tap_text([tab], "fx-" + tag + "-tab", exact=True); scroll_top(3)
            ok = False
            for _ in range(6):
                if tap_text([label], "fx-" + tag + "-find"): ok = True; break
                scroll_up(2)
            log.append(("fx_" + tag + "_open", ok)); time.sleep(2); shot("fx-" + tag + "-form"); dump("fx-" + tag + "-form")
            got = False
            for _ in range(5):
                if tap_text(calc, "fx-" + tag + "-calc"): got = True; break
                scroll_up(2)
            log.append(("fx_" + tag + "_calc", got)); time.sleep(4); dismiss_not_responding()
            sweep("fx-" + tag + "-res", 6)
        except Exception as e:
            log.append(("fx_" + tag + "_exception", repr(e)))
if os.path.exists("tools/ci/CALCPAGES_ONLY"):
    # Default-input calculation of the calculation pages whose standalone result lines were changed (alignment evidence).
    pages = [
        ("الكمرات", "cp-beam", ["تصميم الكمرة الآن", "تحليل وحساب", "احسب"]),
        ("الأعمدة", "cp-col", ["تصميم العمود الآن", "تصميم العمود", "تحليل وحساب", "احسب"]),
        ("البلاطات المسطحة", "cp-fs", ["تصميم بلاطة مسطحة"]),
        ("الخزانات", "cp-tank", ["تصميم الخزان الآن"]),
        ("حوائط السند", "cp-rw", ["تحليل"]),
        ("القواعد الشداد", "cp-strap", ["تحليل وحساب"]),
        ("الزلازل", "cp-seis", ["تحليل القص عند القاعدة"]),
        ("المصمتة", "cp-slab", ["تصميم البلاطة", "تصميم البلاطات", "تحليل وحساب", "احسب"]),
    ]
    for label, prefix, calc in pages:
        try:
            adb("shell", "input", "keyevent", "KEYCODE_BACK"); time.sleep(1.5)
            tap_text(["التصميم"], prefix + "-tab", exact=True); scroll_top(3)
            ok = False
            for _ in range(5):
                if tap_text([label], prefix + "-find"): ok = True; break
                scroll_up(2)
            log.append((prefix + "_open", ok)); time.sleep(2)
            got = False
            for _ in range(5):
                if tap_text(calc, prefix + "-calc"): got = True; break
                scroll_up(2)
            log.append((prefix + "_calc", got)); time.sleep(3)
            sweep(prefix + "-res", 9 if prefix in ("cp-fs", "cp-strap") else 4)
        except Exception as e:
            log.append((prefix + "_exception", repr(e)))
    for blk in (flatslab_drop_block, flatslab_vary_block, font_extra_block, footing_block, stair_block, pile_block):
        try:
            blk()
        except Exception as e:
            log.append((blk.__name__ + "_exception", repr(e)))
    pull_exported_pdfs()
    open(f"{out}/steps.txt", "w").write("\n".join(f"{a}: {b}" for a, b in log)); sys.exit(0)
if os.path.exists("tools/ci/FOOTING_ONLY"):
    try:
        footing_block()
    except Exception as e:
        log.append(("footing_exception", repr(e)))
    pull_exported_pdfs()
    open(f"{out}/steps.txt", "w").write("\n".join(f"{a}: {b}" for a, b in log)); sys.exit(0)
if os.path.exists("tools/ci/STAIR_ONLY"):
    try:
        stair_block()
        stair_unsupported()
        stair_codes()
        restart_app()
        frame_block()
        restart_app()
        stair_invalid()
        pile_block()
        stale_block("الخزانات", "tank", ["تصميم الخزان الآن"], False, "50.0", "60.0", "0")
        stale_block("حوائط السند", "rw", ["تحليل"], True, "4.0", "5.0")
        stale_block("القواعد الشداد", "strap", ["تحليل وحساب"], False, "1200", "1300")
        stale_block("البلاطات المسطحة", "fs", ["تصميم بلاطة مسطحة"], False, "6.0", "6.5")
    except Exception as e:
        log.append(("stair_exception", repr(e)))
    pull_exported_pdfs()
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
# 7) Pile foundation step
pile_block()
# files the app wrote (debug build: run-as works)
ls = adb("shell", "run-as", pkg, "find", ".", "-name", "*.pdf", "-o", "-name", "*.dxf").stdout.decode("utf8", "ignore")
ls += adb("shell", "find", f"/sdcard/Android/data/{pkg}", "-name", "*.pdf").stdout.decode("utf8", "ignore")
open(f"{out}/pdf-files.txt", "w").write(ls)
for p in [l.strip() for l in ls.splitlines() if l.strip().startswith("./")][:4]:
    d = adb("shell", "run-as", pkg, "cat", p).stdout
    open(f"{out}/exported-" + os.path.basename(p), "wb").write(d)
open(f"{out}/steps.txt", "w").write("\n".join(f"{a}: {b}" for a, b in log))
