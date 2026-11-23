package com.civileg.app.qa;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.File;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Disposable QA COMPANION to FrameEntryTest (which is NOT replaced: both classes run
 * and are reported separately). Written in JAVA on purpose: THIS CLASS's authored code has no Kotlin stdlib or
 * kotlinx call sites. This is NOT a claim about the whole instrumentation APK, which
 * still contains AndroidX test runner/junit-ext and the other QA classes (Kotlin) with
 * their own app-side references. No Compose test rule, no kotlinx-coroutines-test, no ActivityScenario.
 * It drives the actual minified, non-debuggable MainActivity through the accessibility
 * tree and asserts the SAME visible strings/state as FrameEntryTest; launch/no-crash
 * alone is not a pass.
 *
 * Mapping original -> companion:
 *  Home Frame module card (clickable node carrying BOTH home_frame and home_frame_sub,
 *    swipe up to 10x, click)               -> findModuleCard(), same loop
 *  wait 30s for frame_title                -> waitText(frame_title)
 *  assertIsDisplayed frame_title/frame_tab_nodes -> assertVisible (node found, non-empty visibleBounds)
 *  baseline "Nodes: 0"/"Members: 0"        -> identical strings
 *  Actions desc -> frame_open_design -> QA_SENTINEL_FRAME_RECORD -> identical
 *  loaded "Nodes: 4"/"Members: 3"          -> identical, fail loudly otherwise
 * State: launches a NEW task with CLEAR_TASK; only the activity/task state is reset.
 * The seeded DB and process are untouched. The Nodes: 0 baseline assertion is what proves
 * no stale in-memory model leaks into the loaded 4/3 check (nothing is cleared to hide it).
 * Disclosed differences: visibility = found + non-empty visibleBounds, not
 * assertIsDisplayed; node text comes from accessibility, unverified until first run.
 */
@RunWith(AndroidJUnit4.class)
public class FrameEntryCompanionTest {

    private UiDevice device;
    private Context ctx;
    private String pkg;
    private String testName = "unknown";

    /** Failure evidence: screenshot + window hierarchy retained when any assertion fails. */
    @Rule
    public final TestWatcher watcher = new TestWatcher() {
        @Override
        protected void starting(Description d) {
            testName = d.getMethodName();
        }

        @Override
        protected void failed(Throwable e, Description d) {
            capture("FAILURE");
        }
    };

    /**
     * Failure/entry evidence by a path the host can actually pull. Observed run 45144868187: the
     * instrumentation external-files route pulled NOTHING. So (1) the screenshot is written by
     * `screencap` through the shell (uid shell, /sdcard root, the same place the host script already
     * pulls qa-<case>.png from), and (2) a compact window-hierarchy summary (class, text,
     * content-desc, clickable, bounds of nodes that have text/desc or are clickable) is logged to
     * logcat under QA-COMPANION-HIER, which the host already captures. The old external-files
     * write is kept as a best-effort extra. Evidence only; no assertion depends on any of it.
     */
    private void capture(String stage) {
        // Only [A-Za-z0-9_-] ever reaches a shell command or a file name; no UI/model text is used.
        String base = safe(testName) + "-" + safe(stage);
        String nonce = safe(String.valueOf(InstrumentationRegistry.getArguments().getString("qaNonce", "nononce")));
        String tag = "[" + nonce + ":" + base + "] ";
        try {
            device.executeShellCommand("screencap -p /sdcard/qa-companion-" + nonce + "-" + base + ".png");
        } catch (Throwable t) {
            android.util.Log.w("QA-COMPANION", "screencap failed for " + base, t);
        }
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            device.dumpWindowHierarchy(bo);
            String xml = bo.toString("UTF-8");
            java.util.regex.Matcher m = Pattern.compile("<node [^>]*>").matcher(xml);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String n = m.group();
                String text = attr(n, "text");
                String desc = attr(n, "content-desc");
                boolean click = "true".equals(attr(n, "clickable"));
                if (text.isEmpty() && desc.isEmpty() && !click) continue;
                sb.append(attr(n, "class")).append(" | t=").append(text).append(" | d=").append(desc)
                  .append(" | click=").append(click).append(" | ").append(attr(n, "bounds")).append('\n');
            }
            String all = "xmlChars=" + xml.length() + "\n" + sb;
            if (all.length() > 60000) all = all.substring(0, 60000) + "\n[truncated]";
            // One log call per node line so every line carries the tag (a multi-line message loses it).
            for (String line : all.split("\n")) {
                if (line.isEmpty()) continue;
                android.util.Log.w("QA-COMPANION-HIER", tag + (line.length() > 900 ? line.substring(0, 900) : line));
            }
        } catch (Throwable t) {
            android.util.Log.w("QA-COMPANION", "hierarchy summary failed for " + base, t);
        }
        try {
            Context ic = InstrumentationRegistry.getInstrumentation().getContext();
            File dir = ic.getExternalFilesDir(null);
            if (dir == null) return;
            File out = new File(dir, "companion");
            out.mkdirs();
            device.takeScreenshot(new File(out, base + ".png"));
            device.dumpWindowHierarchy(new File(out, base + ".xml"));
        } catch (Throwable t) {
            android.util.Log.w("QA-COMPANION", "capture failed for " + stage, t);
        }
    }

    private static String safe(String v) {
        return v.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private static String attr(String node, String name) {
        java.util.regex.Matcher m = Pattern.compile(" " + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(node);
        return m.find() ? m.group(1) : "";
    }

    /** Exact count: "<label>: <n>" not followed by another digit (so 4 never matches 40). */
    private static Pattern exact(String label, int n) {
        return Pattern.compile("(?s).*" + Pattern.quote(label + ": " + n) + "(?!\\d).*");
    }

    @Before
    public void launch() {
        android.app.Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        device = UiDevice.getInstance(inst);
        ctx = inst.getTargetContext();
        pkg = ctx.getPackageName();
        device.pressHome();
        // Same entry the original rule uses: createAndroidComposeRule<MainActivity>() starts
        // com.civileg.app.MainActivity directly (including any splash MainActivity itself shows).
        Intent intent = new Intent();
        intent.setClassName(pkg, "com.civileg.app.MainActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ctx.startActivity(intent);
        assertTrue("app window did not appear", device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 30000));
    }

    // The minified app has no com.civileg.app.R class for the test APK to link against
    // (run 45136912761: NoClassDefFoundError R$string). Resolve by resource name at runtime
    // from the installed app's own resources; fail loudly if the name is absent.
    private String s(String name) {
        int id = ctx.getResources().getIdentifier(name, "string", pkg);
        assertTrue("string resource not found in installed app: " + name, id != 0);
        return ctx.getString(id);
    }

    /**
     * Installed-resource EN/AR pair for one string name. Evidence (run 45149902507 PNG): the app displays
     * the Arabic resources while the target context is English, so a single-locale string can never match
     * the UI text. We do NOT assume the app follows the target context locale, force a language or touch
     * user prefs: both installed resource sets are listed and each assertion must match ONE language
     * consistently (title+subtitle from the same language, never a cross-language mix).
     */
    private String[] pair(String name) {
        String[] out = new String[2];
        String[] langs = {"en", "ar"};
        for (int i = 0; i < 2; i++) {
            android.content.res.Configuration c = new android.content.res.Configuration(ctx.getResources().getConfiguration());
            c.setLocale(new java.util.Locale(langs[i]));
            android.content.res.Resources r = ctx.createConfigurationContext(c).getResources();
            int id = r.getIdentifier(name, "string", pkg);
            assertTrue("string resource not found in installed app: " + name, id != 0);
            out[i] = r.getString(id);
        }
        return out;
    }

    /** Regex alternation of both languages' literal text (deduplicated). */
    private static String anyOf(String[] p) {
        return p[0].equals(p[1]) ? Pattern.quote(p[0]) : Pattern.quote(p[0]) + "|" + Pattern.quote(p[1]);
    }

    private static Pattern containsAny(String[] p) {
        return Pattern.compile("(?s).*(" + anyOf(p) + ").*");
    }

    private static Pattern equalsAny(String[] p) {
        return Pattern.compile("(?s)(" + anyOf(p) + ")");
    }

    /** "<label>: <n>" for either language's label; n as ASCII or Arabic-Indic digits; not followed by another digit. */
    private static Pattern exactAny(String[] label, int n) {
        String ascii = String.valueOf(n);
        StringBuilder ar = new StringBuilder();
        for (char ch : ascii.toCharArray()) ar.append((char) ('\u0660' + (ch - '0')));
        String digits = Pattern.quote(ascii) + "|" + Pattern.quote(ar.toString());
        return Pattern.compile("(?s).*(" + anyOf(label) + ")" + Pattern.quote(": ") + "(" + digits + ")(?![\\d\u0660-\u0669]).*");
    }

    private void collect(UiObject2 n, StringBuilder sb) {
        if (n.getText() != null) sb.append(n.getText()).append('\n');
        if (n.getContentDescription() != null) sb.append(n.getContentDescription()).append('\n');
        for (UiObject2 c : n.getChildren()) collect(c, sb);
    }

    /**
     * One complete, fresh scan. Returns the single clickable card whose own subtree holds both the
     * title and the subtitle, or null when a stable complete scan found none. Any
     * StaleObjectException (from the list or any subtree read) propagates and the whole scan is
     * discarded by the caller.
     */
    // Home module card only: title+subtitle must be the same language (EN+EN or AR+AR) in one
    // clickable subtree; more than one candidate fails. Post-entry label helpers accept either language.
    private UiObject2 scanForModuleCard(String[] title, String[] sub) {
        List<UiObject2> nodes = device.findObjects(By.pkg(pkg).clickable(true));
        UiObject2 found = null;
        int count = 0;
        for (UiObject2 o : nodes) {
            StringBuilder sb = new StringBuilder();
            collect(o, sb);
            String t = sb.toString();
            // One language per match: EN title+EN subtitle, or AR title+AR subtitle. Each node counts once.
            boolean en = t.contains(title[0]) && t.contains(sub[0]);
            boolean ar = t.contains(title[1]) && t.contains(sub[1]);
            if (en || ar) {
                count++;
                if (found == null) found = o;
            }
        }
        // More than one candidate is ambiguous: fail, never click the first.
        assertTrue("ambiguous Frame module card: " + count + " clickable candidates", count <= 1);
        return found;
    }

    /** Up to 3 whole fresh scans, repeating ONLY on StaleObjectException, no swipe between them. */
    private UiObject2 findModuleCard() {
        String[] title = pair("home_frame");
        String[] sub = pair("home_frame_sub");
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return scanForModuleCard(title, sub);
            } catch (androidx.test.uiautomator.StaleObjectException stale) {
                if (attempt == 3) throw stale; // fails explicitly, never reported as "not found"
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private UiObject2 waitText(String[] text, long timeoutMs) {
        return device.wait(Until.findObject(By.pkg(pkg).text(containsAny(text))), timeoutMs);
    }

    private UiObject2 waitPattern(Pattern p, long timeoutMs) {
        return device.wait(Until.findObject(By.pkg(pkg).text(p)), timeoutMs);
    }

    private void assertVisible(BySelector sel, String label) {
        UiObject2 o = device.findObject(sel);
        assertNotNull("not found: " + label, o);
        Rect b = o.getVisibleBounds();
        assertTrue("not visible: " + label, b.width() > 0 && b.height() > 0);
    }

    private void openFrameScreenFromHome() {
        device.wait(Until.hasObject(By.pkg(pkg).scrollable(true)), 30000);
        boolean clicked = false;
        for (int i = 1; i <= 10 && !clicked; i++) {
            // fresh selection + click, repeated 3x only on StaleObjectException; no query after success
            UiObject2 card = null;
            for (int attempt = 1; attempt <= 3 && !clicked; attempt++) {
                card = findModuleCard();
                if (card == null) break;
                try {
                    card.click();
                    clicked = true;
                } catch (androidx.test.uiautomator.StaleObjectException stale) {
                    if (attempt == 3) throw stale;
                }
            }
            if (clicked) break;
            int w = device.getDisplayWidth();
            int h = device.getDisplayHeight();
            device.swipe(w / 2, (int) (h * 0.75), w / 2, (int) (h * 0.35), 20);
            device.waitForIdle();
        }
        assertTrue("Frame module card not found on Home after scrolling", clicked);
        assertNotNull("Frame title never appeared", waitText(pair("frame_title"), 30000));
    }

    private void assertFrameScreenVisible() {
        assertVisible(By.pkg(pkg).text(containsAny(pair("frame_title"))), "frame_title");
        assertVisible(By.pkg(pkg).text(equalsAny(pair("frame_tab_nodes"))), "frame_tab_nodes");
    }

    @Test
    public void frameAnalysisEntryShowsRealFrameScreen() {
        openFrameScreenFromHome();
        assertFrameScreenVisible();
        capture("entry");
    }

    @Test
    public void savedFrameModelOpensFromRealSeededRecord() {
        openFrameScreenFromHome();
        assertFrameScreenVisible();
        capture("entry");

        String[] nodes = pair("frame_tab_nodes");
        String[] members = pair("frame_tab_members");
        assertNotNull("empty baseline Nodes: 0 missing", waitPattern(exactAny(nodes, 0), 15000));
        assertVisible(By.pkg(pkg).text(exactAny(nodes, 0)), nodes[0] + "|" + nodes[1] + ": 0");
        assertVisible(By.pkg(pkg).text(exactAny(members, 0)), members[0] + "|" + members[1] + ": 0");

        UiObject2 actions = device.wait(Until.findObject(By.pkg(pkg).desc("Actions")), 15000);
        assertNotNull("Actions button not found", actions);
        actions.click();
        UiObject2 open = device.wait(Until.findObject(By.pkg(pkg).text(equalsAny(pair("frame_open_design")))), 15000);
        assertNotNull("Open Saved Model item not found", open);
        open.click();
        UiObject2 rec = device.wait(Until.findObject(By.pkg(pkg).text("QA_SENTINEL_FRAME_RECORD")), 15000);
        assertNotNull("seeded QA_SENTINEL_FRAME_RECORD not listed", rec);
        rec.click();

        assertNotNull("loaded Nodes: 4 never appeared", waitPattern(exactAny(nodes, 4), 15000));
        assertVisible(By.pkg(pkg).text(exactAny(nodes, 4)), nodes[0] + "|" + nodes[1] + ": 4");
        assertVisible(By.pkg(pkg).text(exactAny(members, 3)), members[0] + "|" + members[1] + ": 3");
        capture("loaded-4-3");
    }
}
