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

    /** Writes to the instrumentation package's external files dir (host pulls it). */
    private void capture(String stage) {
        try {
            Context ic = InstrumentationRegistry.getInstrumentation().getContext();
            File dir = ic.getExternalFilesDir(null);
            if (dir == null) return;
            File out = new File(dir, "companion");
            out.mkdirs();
            String base = testName + "-" + stage;
            device.takeScreenshot(new File(out, base + ".png"));
            device.dumpWindowHierarchy(new File(out, base + ".xml"));
        } catch (Throwable t) {
            android.util.Log.w("QA-COMPANION", "capture failed for " + stage, t);
        }
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
    private UiObject2 scanForModuleCard(String title, String sub) {
        List<UiObject2> nodes = device.findObjects(By.pkg(pkg).clickable(true));
        UiObject2 found = null;
        for (UiObject2 o : nodes) {
            StringBuilder sb = new StringBuilder();
            collect(o, sb);
            String t = sb.toString();
            if (found == null && t.contains(title) && t.contains(sub)) found = o;
        }
        return found;
    }

    /** Up to 3 whole fresh scans, repeating ONLY on StaleObjectException, no swipe between them. */
    private UiObject2 findModuleCard() {
        String title = s("home_frame");
        String sub = s("home_frame_sub");
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return scanForModuleCard(title, sub);
            } catch (androidx.test.uiautomator.StaleObjectException stale) {
                if (attempt == 3) throw stale; // fails explicitly, never reported as "not found"
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private UiObject2 waitText(String text, long timeoutMs) {
        return device.wait(Until.findObject(By.pkg(pkg).textContains(text)), timeoutMs);
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
        assertNotNull("Frame title never appeared", waitText(s("frame_title"), 30000));
    }

    private void assertFrameScreenVisible() {
        assertVisible(By.pkg(pkg).textContains(s("frame_title")), "frame_title");
        assertVisible(By.pkg(pkg).text(s("frame_tab_nodes")), "frame_tab_nodes");
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

        String nodes = s("frame_tab_nodes");
        String members = s("frame_tab_members");
        assertNotNull("empty baseline Nodes: 0 missing", waitPattern(exact(nodes, 0), 15000));
        assertVisible(By.pkg(pkg).text(exact(nodes, 0)), nodes + ": 0");
        assertVisible(By.pkg(pkg).text(exact(members, 0)), members + ": 0");

        UiObject2 actions = device.wait(Until.findObject(By.pkg(pkg).desc("Actions")), 15000);
        assertNotNull("Actions button not found", actions);
        actions.click();
        UiObject2 open = device.wait(Until.findObject(By.pkg(pkg).text(s("frame_open_design"))), 15000);
        assertNotNull("Open Saved Model item not found", open);
        open.click();
        UiObject2 rec = device.wait(Until.findObject(By.pkg(pkg).text("QA_SENTINEL_FRAME_RECORD")), 15000);
        assertNotNull("seeded QA_SENTINEL_FRAME_RECORD not listed", rec);
        rec.click();

        assertNotNull("loaded Nodes: 4 never appeared", waitPattern(exact(nodes, 4), 15000));
        assertVisible(By.pkg(pkg).text(exact(nodes, 4)), nodes + ": 4");
        assertVisible(By.pkg(pkg).text(exact(members, 3)), members + ": 3");
        capture("loaded-4-3");
    }
}
