package com.civileg.app.qa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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
 *  baseline "Nodes: 0"/"Members: 0"        -> NOT identical: the screen opens on the Drawing tab, where the only count text is
 *    the single toolbar summary "النموذج: N عقدة / M عضو / K بلاطة" | "Model: N joints / M members / K slabs"
 *    (FrameModelerTab.kt). Companion asserts that summary 0/0/0 as exactly one full-string node.
 *  Actions desc -> frame_open_design -> QA_SENTINEL_FRAME_RECORD -> identical
 *  loaded "Nodes: 4"/"Members: 3"          -> summary 4/3/0 (seeded slabs = []), exactly one node, fail loudly otherwise
 *  Home reset: bounded DOM-resolved scroll-to-top (scrollHomeToTop) runs once before the original forward loop.
 * State: launches a NEW task with CLEAR_TASK; only the activity/task state is reset.
 * The seeded DB and process are untouched. The 0/0/0 baseline assertion is what proves
 * no stale in-memory model leaks into the loaded 4/3/0 check (nothing is cleared to hide it).
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
            logRawXml(base, xml);
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

    private static final Pattern BOUNDS = Pattern.compile("\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]");
    private static int dumpSeq = 0;

    /** One clickable DOM element that satisfies the ORIGINAL contract: its own subtree (self + descendants) holds both strings. */
    private static final class Card {
        org.w3c.dom.Element el;
        org.w3c.dom.Element scope; // the card's own subtree: its own text may sit above the click point, nothing else may
        String identity; // child-index path + raw bounds + subtree text: must be equal in both snapshots
        int l, t, r, b;
        int cx() { return (l + r) / 2; }
        int cy() { return (t + b) / 2; }
    }

    /** ONE dumpWindowHierarchy string, parsed with the JDK DOM parser (nothing live, nothing can go stale). */
    private org.w3c.dom.Document snapshot(String rawStage) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        device.dumpWindowHierarchy(bo);
        String xml = bo.toString("UTF-8");
        if (rawStage != null) logRawXml(safe(testName) + "-" + rawStage, xml);
        return javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(xml.getBytes("UTF-8")));
    }

    private static void subtreeText(org.w3c.dom.Element e, StringBuilder sb) {
        String tx = e.getAttribute("text"), ds = e.getAttribute("content-desc");
        if (!tx.isEmpty()) sb.append(tx).append('\n');
        if (!ds.isEmpty()) sb.append(ds).append('\n');
        for (org.w3c.dom.Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof org.w3c.dom.Element && "node".equals(c.getNodeName())) subtreeText((org.w3c.dom.Element) c, sb);
        }
    }

    private static String pathOf(org.w3c.dom.Element e) {
        StringBuilder p = new StringBuilder();
        for (org.w3c.dom.Node n = e; n instanceof org.w3c.dom.Element; n = n.getParentNode()) {
            int i = 0;
            for (org.w3c.dom.Node s = n.getPreviousSibling(); s != null; s = s.getPreviousSibling()) if (s instanceof org.w3c.dom.Element) i++;
            p.insert(0, "/" + i);
        }
        return p.toString();
    }

    /**
     * ORIGINAL contract, unchanged in meaning: a clickable node (this app's package, enabled, positive bounds)
     * whose OWN SUBTREE holds both the title and the subtitle of the SAME language (EN+EN or AR+AR). Each
     * element counts once. No geometry is used to pick or rank candidates; the caller fails on more than one.
     */
    private List<Card> cards(org.w3c.dom.Document doc, String[] title, String[] sub) {
        List<Card> out = new java.util.ArrayList<>();
        org.w3c.dom.NodeList all = doc.getElementsByTagName("node");
        for (int i = 0; i < all.getLength(); i++) {
            org.w3c.dom.Element e = (org.w3c.dom.Element) all.item(i);
            if (!"true".equals(e.getAttribute("clickable")) || !"true".equals(e.getAttribute("enabled"))) continue;
            if (!pkg.equals(e.getAttribute("package"))) continue;
            java.util.regex.Matcher m = BOUNDS.matcher(e.getAttribute("bounds"));
            if (!m.matches()) continue;
            Card c = new Card();
            c.l = Integer.parseInt(m.group(1)); c.t = Integer.parseInt(m.group(2));
            c.r = Integer.parseInt(m.group(3)); c.b = Integer.parseInt(m.group(4));
            if (c.r <= c.l || c.b <= c.t) continue;
            StringBuilder sb = new StringBuilder();
            subtreeText(e, sb);
            String t = sb.toString();
            boolean en = t.contains(title[0]) && t.contains(sub[0]);
            boolean ar = t.contains(title[1]) && t.contains(sub[1]);
            if (!en && !ar) continue;
            c.el = e;
            c.scope = e;
            c.identity = "SUBTREE:" + pathOf(e) + "|" + e.getAttribute("bounds") + "|" + t;
            out.add(c);
        }
        // commonParentCards(...) fallback intentionally NOT called this run: the real DOM shape is unknown; if no clickable
        // node holds both strings in its own subtree the test fails closed and the retained raw FAILURE XML settles it.
        return out;
    }

    /**
     * Used ONLY when no clickable node holds both strings in its own subtree (title and subtitle are then
     * siblings of the clickable node, not descendants). Equivalent unique card identity, no geometry: the
     * lowest common ancestor of one title element and one subtitle element of the SAME language, whose own
     * subtree holds exactly one title element, exactly one subtitle element and exactly one enabled clickable
     * node of this package with positive bounds. That clickable is the card action. Any other shape yields no
     * candidate (it is never guessed).
     */
    private List<Card> commonParentCards(org.w3c.dom.Document doc, String[] title, String[] sub) {
        List<Card> out = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        org.w3c.dom.NodeList all = doc.getElementsByTagName("node");
        for (int lang = 0; lang < 2; lang++) {
            for (int i = 0; i < all.getLength(); i++) {
                org.w3c.dom.Element tn = (org.w3c.dom.Element) all.item(i);
                if (!ownText(tn).contains(title[lang])) continue;
                for (int j = 0; j < all.getLength(); j++) {
                    org.w3c.dom.Element sn = (org.w3c.dom.Element) all.item(j);
                    if (sn == tn || !ownText(sn).contains(sub[lang])) continue;
                    org.w3c.dom.Element lca = lowestCommonAncestor(tn, sn);
                    if (lca == null || lca == tn || lca == sn || !"node".equals(lca.getNodeName())) continue;
                    if (countOwnText(lca, title[lang]) != 1 || countOwnText(lca, sub[lang]) != 1) continue;
                    List<org.w3c.dom.Element> clicks = new java.util.ArrayList<>();
                    collectClickables(lca, clicks);
                    if (clicks.size() != 1) continue;
                    org.w3c.dom.Element ce = clicks.get(0);
                    java.util.regex.Matcher m = BOUNDS.matcher(ce.getAttribute("bounds"));
                    if (!m.matches()) continue;
                    Card c = new Card();
                    c.l = Integer.parseInt(m.group(1)); c.t = Integer.parseInt(m.group(2));
                    c.r = Integer.parseInt(m.group(3)); c.b = Integer.parseInt(m.group(4));
                    if (c.r <= c.l || c.b <= c.t) continue;
                    StringBuilder sb = new StringBuilder();
                    subtreeText(lca, sb);
                    c.el = ce;
                    c.scope = lca;
                    c.identity = "COMMONPARENT:" + pathOf(lca) + "|" + ce.getAttribute("bounds") + "|" + sb;
                    if (seen.add(c.identity)) out.add(c);
                }
            }
        }
        return out;
    }

    private static String ownText(org.w3c.dom.Element e) {
        return e.getAttribute("text") + "\n" + e.getAttribute("content-desc");
    }

    private static int countOwnText(org.w3c.dom.Element root, String needle) {
        int n = ownText(root).contains(needle) ? 1 : 0;
        for (org.w3c.dom.Node c = root.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof org.w3c.dom.Element && "node".equals(c.getNodeName())) n += countOwnText((org.w3c.dom.Element) c, needle);
        }
        return n;
    }

    private void collectClickables(org.w3c.dom.Element e, List<org.w3c.dom.Element> out) {
        if ("true".equals(e.getAttribute("clickable")) && "true".equals(e.getAttribute("enabled")) && pkg.equals(e.getAttribute("package"))) out.add(e);
        for (org.w3c.dom.Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) {
            if (c instanceof org.w3c.dom.Element && "node".equals(c.getNodeName())) collectClickables((org.w3c.dom.Element) c, out);
        }
    }

    private static org.w3c.dom.Element lowestCommonAncestor(org.w3c.dom.Element a, org.w3c.dom.Element b) {
        java.util.Set<org.w3c.dom.Node> anc = new java.util.HashSet<>();
        for (org.w3c.dom.Node n = a; n != null; n = n.getParentNode()) anc.add(n);
        for (org.w3c.dom.Node n = b; n != null; n = n.getParentNode()) {
            if (anc.contains(n)) return n instanceof org.w3c.dom.Element ? (org.w3c.dom.Element) n : null;
        }
        return null;
    }

    /** Conservative FAIL-only heuristic: the last dumped node containing the point must be the card or inside it, else fail. Dump order is NOT claimed to be draw order, so this cannot prove the hit is unobscured; it only refuses an obviously suspicious click.*/
    private static String coveredBy(org.w3c.dom.Document doc, Card c) {
        org.w3c.dom.NodeList all = doc.getElementsByTagName("node");
        org.w3c.dom.Element top = null;
        for (int i = 0; i < all.getLength(); i++) {
            org.w3c.dom.Element e = (org.w3c.dom.Element) all.item(i);
            java.util.regex.Matcher m = BOUNDS.matcher(e.getAttribute("bounds"));
            if (!m.matches()) continue;
            int l = Integer.parseInt(m.group(1)), t = Integer.parseInt(m.group(2)), r = Integer.parseInt(m.group(3)), b = Integer.parseInt(m.group(4));
            if (c.cx() >= l && c.cx() < r && c.cy() >= t && c.cy() < b) top = e;
        }
        for (org.w3c.dom.Node n = top; n != null; n = n.getParentNode()) if (n == c.el || n == c.scope) return null;
        return top == null ? "no node at point" : top.getAttribute("class") + " " + top.getAttribute("bounds");
    }

    /**
     * Finds and clicks the unique Home card. Snapshot A selects it; snapshot B, taken right before the click,
     * must select the SAME single card identity (path, bounds, subtree text); otherwise the attempt is discarded
     * (3 attempts, then a loud AssertionError, never "not found"). More than one candidate fails at once. The click
     * point (full X/Y center) must be on screen and not obviously covered (conservative heuristic, not proof). B is the dump retained as
     * raw XML. Returns false when a stable snapshot shows no card (the caller scrolls).
     */
    /**
     * Diagnostic-only label accounting for one retained search snapshot; it selects and changes NOTHING.
     * Counts nodes whose own text carries either installed label, then CLASSIFIES every node whose subtree
     * holds BOTH labels of one language with the same checks the unchanged selector applies (clickable,
     * enabled, package, bounds parse, positive area): a node passing all checks is reported as
     * "eligible-candidate", never as a rejection; only an actual failing check is named
     * (not-clickable / not-enabled / foreign-package / no-bounds / nonpositive-bounds). Nodes are ordered
     * nearest-first (deepest DOM depth) before the four-entry cap so broad ancestors of the card cannot
     * consume the cap and hide the near-card classification.
     */
    private String labelDiag(org.w3c.dom.Document doc, String[] title, String[] sub) {
        org.w3c.dom.NodeList all = doc.getElementsByTagName("node");
        int t = 0, s = 0;
        java.util.List<org.w3c.dom.Element> holders = new java.util.ArrayList<>();
        for (int i = 0; i < all.getLength(); i++) {
            org.w3c.dom.Element e = (org.w3c.dom.Element) all.item(i);
            String ot = ownText(e);
            if (ot.contains(title[0]) || ot.contains(title[1])) t++;
            if (ot.contains(sub[0]) || ot.contains(sub[1])) s++;
            StringBuilder sb = new StringBuilder();
            subtreeText(e, sb);
            String x = sb.toString();
            if ((x.contains(title[0]) && x.contains(sub[0])) || (x.contains(title[1]) && x.contains(sub[1]))) holders.add(e);
        }
        holders.sort((a, b) -> Integer.compare(depthOf(b), depthOf(a)));
        java.util.List<String> classes = new java.util.ArrayList<>();
        for (org.w3c.dom.Element e : holders) {
            if (classes.size() >= 4) break;
            String cls;
            if (!"true".equals(e.getAttribute("clickable"))) cls = "check-failed:not-clickable";
            else if (!"true".equals(e.getAttribute("enabled"))) cls = "check-failed:not-enabled";
            else if (!pkg.equals(e.getAttribute("package"))) cls = "check-failed:foreign-package";
            else {
                java.util.regex.Matcher m = BOUNDS.matcher(e.getAttribute("bounds"));
                if (!m.matches()) cls = "check-failed:no-bounds";
                else {
                    int l = Integer.parseInt(m.group(1)), tt = Integer.parseInt(m.group(2));
                    int r = Integer.parseInt(m.group(3)), b2 = Integer.parseInt(m.group(4));
                    cls = (r <= l || b2 <= tt) ? "check-failed:nonpositive-bounds" : "eligible-candidate";
                }
            }
            classes.add(cls + "@" + pathOf(e));
        }
        return "titleNodes=" + t + " subNodes=" + s + " bothLabelSubtreeClasses(nearestFirst)=" + classes;
    }

    private static int depthOf(org.w3c.dom.Element e) {
        int d = 0;
        for (org.w3c.dom.Node p = e.getParentNode(); p instanceof org.w3c.dom.Element; p = p.getParentNode()) d++;
        return d;
    }

    private boolean clickModuleCardFromSnapshots(String tag) throws Exception {
        String[] title = pair("home_frame");
        String[] sub = pair("home_frame_sub");
        // Diagnostic retention only: the literal installed EN/AR label pairs used by the unchanged selector.
        android.util.Log.w("QA-COMPANION", "card-search " + tag + " test=" + safe(testName)
                + " labels title=['" + title[0] + "','" + title[1] + "'] sub=['" + sub[0] + "','" + sub[1] + "']");
        for (int attempt = 1; attempt <= 3; attempt++) {
            org.w3c.dom.Document docA = snapshot(tag + "-A" + attempt);
            List<Card> a = cards(docA, title, sub);
            android.util.Log.w("QA-COMPANION", "card-search " + tag + " A" + attempt + " candidates=" + a.size() + " " + labelDiag(docA, title, sub));
            assertTrue("ambiguous Frame module card: " + a.size() + " clickable candidates", a.size() <= 1);
            if (a.isEmpty()) return false;
            org.w3c.dom.Document docB = snapshot(tag + "-B");
            List<Card> b = cards(docB, title, sub);
            android.util.Log.w("QA-COMPANION", "card-search " + tag + " B candidates=" + b.size() + " " + labelDiag(docB, title, sub));
            assertTrue("ambiguous Frame module card at click: " + b.size() + " clickable candidates", b.size() <= 1);
            if (b.size() == 1 && a.get(0).identity.equals(b.get(0).identity)) {
                Card c = b.get(0);
                android.util.Log.w("QA-COMPANION", "card rule=" + c.identity.substring(0, c.identity.indexOf(':')) + " bounds=" + c.l + "," + c.t + "," + c.r + "," + c.b);
                assertTrue("card click point is off screen", c.cx() >= 0 && c.cx() < device.getDisplayWidth() && c.cy() >= 0 && c.cy() < device.getDisplayHeight());
                String cover = coveredBy(docB, c);
                assertTrue("card click point is covered by " + cover, cover == null);
                assertTrue("click failed", device.click(c.cx(), c.cy()));
                return true;
            }
            if (attempt == 3) throw new AssertionError("Frame card changed between snapshots: " + a.size() + " vs " + b.size() + " candidates");
        }
        throw new IllegalStateException("unreachable");
    }

    /**
     * Raw XML retained as gzip+base64 chunks in logcat under a UNIQUE dump id (d<seq>, never reused in this process).
     * The host reassembles, verifies sha256 and root, and only then writes the file.
     */
    private void logRawXml(String base, String xml) {
        try {
            String nonce = safe(String.valueOf(InstrumentationRegistry.getArguments().getString("qaNonce", "nononce")));
            int id;
            synchronized (FrameEntryCompanionTest.class) { id = ++dumpSeq; }
            String tag = "[" + nonce + ":" + base + ":d" + id + "] ";
            byte[] raw = xml.getBytes("UTF-8");
            String sha = bytesToHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw));
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bo)) { gz.write(raw); }
            String b64 = android.util.Base64.encodeToString(bo.toByteArray(), android.util.Base64.NO_WRAP);
            int n = (b64.length() + 2999) / 3000;
            android.util.Log.w("QA-COMPANION-XML", tag + "meta bytes=" + raw.length + " sha256=" + sha + " chunks=" + n);
            for (int i = 0; i < n; i++) {
                android.util.Log.w("QA-COMPANION-XML", tag + (i + 1) + "/" + n + " " + b64.substring(i * 3000, Math.min(b64.length(), (i + 1) * 3000)));
            }
        } catch (Throwable t) {
            android.util.Log.w("QA-COMPANION", "raw xml log failed for " + base, t);
        }
    }

    private static String bytesToHex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (byte x : d) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private UiObject2 waitText(String[] text, long timeoutMs) {
        return device.wait(Until.findObject(By.pkg(pkg).text(containsAny(text))), timeoutMs);
    }

    /**
     * The real Drawing-tab model summary (FrameModelerTab.kt, hard-coded t(ar,en) strings, not resources):
     * "النموذج: N عقدة / M عضو / K بلاطة" or "Model: N joints / M members / K slabs". Full-string match only
     * (the separate status-bar text without the prefix does not match), digits ASCII or Arabic-Indic.
     */
    private static Pattern summaryExact(int nodes, int members, int slabs) {
        String n = dig(nodes), m = dig(members), k = dig(slabs);
        return Pattern.compile("(?s)(\u0627\u0644\u0646\u0645\u0648\u0630\u062c: " + n + " \u0639\u0642\u062f\u0629 / " + m
                + " \u0639\u0636\u0648 / " + k + " \u0628\u0644\u0627\u0637\u0629|Model: " + n + " joints / " + m + " members / " + k + " slabs)");
    }

    private static String dig(int v) {
        String ascii = String.valueOf(v);
        StringBuilder ar = new StringBuilder();
        for (char ch : ascii.toCharArray()) ar.append((char) ('\u0660' + (ch - '0')));
        return "(?:" + Pattern.quote(ascii) + "|" + Pattern.quote(ar.toString()) + ")";
    }

    /** Waits for the summary, then requires exactly ONE visible matching node (more than one fails, no first-match pick). */
    private void assertSingleSummary(int nodes, int members, int slabs, String label) {
        Pattern p = summaryExact(nodes, members, slabs);
        assertNotNull(label + " summary never appeared", waitPattern(p, 15000));
        java.util.List<UiObject2> found = device.findObjects(By.pkg(pkg).text(p));
        assertEquals(label + " summary candidates", 1, found.size());
        Rect b = found.get(0).getVisibleBounds();
        assertTrue(label + " summary not visible", b.width() > 0 && b.height() > 0);
    }

    /**
     * Bounded Home reset (prep before the original forward search, which is unchanged). Each round dumps the DOM and
     * re-resolves the ONE enabled scrollable MAIN vertical container of this package from that snapshot (no live node
     * kept): positive on-screen bounds, taller than wide, at least 40% of the display height; short horizontal scrollers
     * (HorizontalScrollView, tools rows) are excluded. Zero or more than one such container fails. The gesture is a
     * content-down swipe in the container's middle-lower band at an x that avoids every other scrollable's bounds.
     * Stops on two identical consecutive dumps (NOCHANGE) or after 10 swipes (BUDGET: top is NOT verified). A fresh dump taken after the
     * loop is retained as raw XML (home-prep-end) either way.
     */
    private void scrollHomeToTop() throws Exception {
        String prev = null;
        String cur = null;
        String outcome = "BUDGET";
        for (int i = 0; i < 10; i++) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            device.dumpWindowHierarchy(bo);
            cur = bo.toString("UTF-8");
            if (cur.equals(prev)) { outcome = "NOCHANGE"; break; }
            prev = cur;
            org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(cur.getBytes("UTF-8")));
            int[] c = homeVerticalContainer(doc, device.getDisplayWidth(), device.getDisplayHeight());
            java.util.List<int[]> others = otherScrollables(doc, c);
            int top = c[1] + (int) ((c[3] - c[1]) * 0.45), bottom = c[1] + (int) ((c[3] - c[1]) * 0.85);
            int w = c[2] - c[0];
            int x = -1;
            for (double f : new double[]{0.5, 0.25, 0.75}) {
                int cx = c[0] + (int) (w * f);
                boolean blocked = false;
                for (int[] o : others) {
                    if (cx >= o[0] && cx < o[2] && bottom > o[1] && top < o[3]) { blocked = true; break; }
                }
                if (!blocked) { x = cx; break; }
            }
            assertTrue("no swipe x clear of nested scrollables in Home container " + java.util.Arrays.toString(c), x >= 0);
            android.util.Log.w("QA-COMPANION", "home-prep swipe " + (i + 1) + " container=" + java.util.Arrays.toString(c) + " x=" + x);
            device.swipe(x, top, x, bottom, 20);
            device.waitForIdle();
        }
        android.util.Log.w("QA-COMPANION", "home-prep outcome=" + outcome + (outcome.equals("BUDGET") ? " (top NOT verified)" : ""));
        // Fresh dump after the loop: the retained prep-end XML is the actual state, not the pre-last-swipe dump.
        java.io.ByteArrayOutputStream fin = new java.io.ByteArrayOutputStream();
        device.dumpWindowHierarchy(fin);
        logRawXml(safe(testName) + "-home-prep-end", fin.toString("UTF-8"));
    }

    /** The single enabled scrollable vertical main container of this package; fails when absent or ambiguous. */
    private int[] homeVerticalContainer(org.w3c.dom.Document doc, int displayWidth, int displayHeight) {
        java.util.List<int[]> found = new java.util.ArrayList<>();
        org.w3c.dom.NodeList all = doc.getElementsByTagName("node");
        for (int i = 0; i < all.getLength(); i++) {
            org.w3c.dom.Element e = (org.w3c.dom.Element) all.item(i);
            if (!"true".equals(e.getAttribute("scrollable")) || !"true".equals(e.getAttribute("enabled"))) continue;
            if (!pkg.equals(e.getAttribute("package"))) continue;
            if (e.getAttribute("class").contains("HorizontalScrollView")) continue;
            java.util.regex.Matcher m = BOUNDS.matcher(e.getAttribute("bounds"));
            if (!m.matches()) continue;
            int l = Integer.parseInt(m.group(1)), t = Integer.parseInt(m.group(2)), r = Integer.parseInt(m.group(3)), b = Integer.parseInt(m.group(4));
            if (l < 0 || t < 0 || r <= l || b <= t || r > displayWidth || b > displayHeight) continue;
            int w = r - l, h = b - t;
            if (h > w && h * 10 >= displayHeight * 4) found.add(new int[]{l, t, r, b});
        }
        assertEquals("Home vertical scroll containers (need exactly 1)", 1, found.size());
        return found.get(0);
    }

    private java.util.List<int[]> otherScrollables(org.w3c.dom.Document doc, int[] main) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        org.w3c.dom.NodeList all = doc.getElementsByTagName("node");
        for (int i = 0; i < all.getLength(); i++) {
            org.w3c.dom.Element e = (org.w3c.dom.Element) all.item(i);
            if (!"true".equals(e.getAttribute("scrollable")) || !pkg.equals(e.getAttribute("package"))) continue;
            java.util.regex.Matcher m = BOUNDS.matcher(e.getAttribute("bounds"));
            if (!m.matches()) continue;
            int[] r = {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))};
            if (java.util.Arrays.equals(r, main)) continue;
            out.add(r);
        }
        return out;
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


    /**
     * Bounded guard for the app's own OPTIONAL donation dialog (110 QA experiment; evidence run
     * 45169098235 PNG/XML): when that dialog covers Home, recognize it exactly and decline it once so
     * the unchanged Home prep below can run.
     *
     * Resolution runs TWICE through the same resolver over two independent fresh DOM dumps (nothing
     * live kept): A, then the before-capture, then B immediately before the click. A
     * and B must agree on the dialog / decline / confirm DOM index-paths, their bounds, and the four
     * recognized tuple texts; a changed, absent or ambiguous B FAILS with evidence and nothing is
     * clicked (no stale coordinates, no retry). Only B's decline bounds center is clicked, exactly
     * once per test, via device.click (no UiObject2 kept across the transition); the click call must
     * report execution.
     *
     * Recognition within one resolution: exactly ONE language carries the FULL installed tuple
     * donation_title + donation_message + donation_decline + donation_confirm, each label EXACTLY
     * once, and the OTHER language has ZERO markers; all four labels inside a UNIQUE app-package
     * android:id/content subtree with positive on-screen bounds. NO markers in either language means
     * the dialog is absent: the Home path is unchanged. Partial markers, any marker in the other
     * language, a duplicated label, or more than one dialog subtree: INVALID, caller captures + FAILs.
     * The same bounds/path/text predicates run identically in the fresh and historical cases.
     *
     * Decline: the UNIQUE nearest app-package enabled clickable ancestor of the decline label,
     * walking up ONLY while still a DOM descendant of the dialog element (never past it), carrying
     * the decline label and NOT the confirm label, with its rectangle wholly within the dialog
     * bounds; BOTH DOM ancestry and bounds containment are required. The confirm label's matching
     * ancestor must exist and be a DIFFERENT node (never clicked).
     *
     * After the click: bounded wait (10 x 500ms) until a fresh dump has ZERO donation markers in BOTH
     * languages; otherwise capture + FAIL (a repeated or non-dismissing modal is a failure, not a
     * retry). Raw XML + pixels are captured before the click and after the dismissal. No Back, no
     * retry loop, no prefs seed/reset, confirm never clicked.
     * NOTE: this guard is NOT state-neutral, by design: a normal visible decline persists
     * answered=true in the app's own donation_prompt prefs on this disposable emulator, exactly as
     * a user tapping "Not now" would. No other app data is touched.
     */
    private void dismissDonationDialogIfPresent() throws Exception {
        DonationResolution a = resolveDonationDialog();
        if (a.invalidReason != null) {
            capture("donation-unrecognized");
            fail("donation guard: " + a.invalidReason);
        }
        if (a.absent) {
            android.util.Log.w("QA-COMPANION", "donation guard: no donation markers; Home path unchanged");
            return;
        }
        capture("donation-before");
        // Fresh second resolution immediately before the click; only B's coordinates are clicked.
        DonationResolution b = resolveDonationDialog();
        if (b.invalidReason != null || b.absent) {
            capture("donation-unrecognized");
            fail("donation guard: dialog changed, absent or ambiguous on re-resolution: "
                    + (b.invalidReason != null ? b.invalidReason : "markers gone before click"));
        }
        if (!a.dialogPath.equals(b.dialogPath) || !a.declinePath.equals(b.declinePath) || !a.confirmPath.equals(b.confirmPath)
                || !java.util.Arrays.equals(a.dialogBounds, b.dialogBounds) || !java.util.Arrays.equals(a.declineBounds, b.declineBounds)
                || !java.util.Arrays.equals(a.confirmBounds, b.confirmBounds) || !java.util.Arrays.equals(a.tupleTexts, b.tupleTexts)) {
            capture("donation-unrecognized");
            fail("donation guard: dialog/decline/confirm path, bounds or tuple texts changed between resolutions");
        }
        int cx = b.declineBounds[0] + (b.declineBounds[2] - b.declineBounds[0]) / 2;
        int cy = b.declineBounds[1] + (b.declineBounds[3] - b.declineBounds[1]) / 2;
        android.util.Log.w("QA-COMPANION", "donation guard: declining optional donation dialog at (" + cx + "," + cy + ") lang=" + b.lang);
        assertTrue("donation guard: decline click not executed", device.click(cx, cy));
        device.waitForIdle();

        // Bounded wait: ZERO donation markers in BOTH languages on a fresh dump; a repeated or
        // non-dismissing modal FAILS. No retry loop, no Back fallback.
        boolean gone = false;
        for (int i = 0; i < 10 && !gone; i++) {
            java.io.ByteArrayOutputStream p = new java.io.ByteArrayOutputStream();
            device.dumpWindowHierarchy(p);
            org.w3c.dom.Document d2 = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(p.toString("UTF-8").getBytes("UTF-8")));
            gone = donationMarkerCount(d2.getElementsByTagName("node")) == 0;
            if (!gone) try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        if (!gone) {
            capture("donation-still-present");
            fail("donation guard: donation modal still present after one decline click");
        }
        capture("donation-after");
        android.util.Log.w("QA-COMPANION", "donation guard: modal absent (both languages) after one decline click");
    }

    /** Plain result record of one donation-dialog resolution pass: absent, invalid (reason), or resolved. */
    private static final class DonationResolution {
        boolean absent;
        String invalidReason;
        int lang;
        String[] tupleTexts;
        String dialogPath, declinePath, confirmPath;
        int[] dialogBounds, declineBounds, confirmBounds;
    }

    /**
     * Single resolver used for BOTH the A and B passes, so they run exactly the same code. One pass
     * performs one fresh dumpWindowHierarchy read plus installed-string lookups (pair() fails loudly
     * if a string is missing, as everywhere in this class) and returns a DonationResolution. It
     * performs no clicks, captures, logs, assertions or UI mutations of its own.
     */
    private DonationResolution resolveDonationDialog() throws Exception {
        String[] title = pair("donation_title");
        String[] message = pair("donation_message");
        String[] decline = pair("donation_decline");
        String[] confirm = pair("donation_confirm");
        String[][] names = {title, message, decline, confirm};

        DonationResolution r = new DonationResolution();
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        device.dumpWindowHierarchy(bo);
        org.w3c.dom.Document doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new java.io.ByteArrayInputStream(bo.toString("UTF-8").getBytes("UTF-8")));
        org.w3c.dom.NodeList all = doc.getElementsByTagName("node");
        int dw = device.getDisplayWidth(), dh = device.getDisplayHeight();

        // Per language: each tuple label EXACTLY once. Exactly one full-tuple language, zero markers
        // in the other language; everything else is partial or ambiguous.
        org.w3c.dom.Element[] els = null;
        int lang = -1;
        int[] langMarkers = new int[2];
        for (int i = 0; i < 2; i++) {
            java.util.List<org.w3c.dom.Element> hits = new java.util.ArrayList<>();
            boolean clean = true;
            for (int j = 0; j < 4; j++) {
                java.util.List<org.w3c.dom.Element> h = exactTextNodes(all, names[j][i]);
                langMarkers[i] += h.size();
                if (h.size() != 1) clean = false;
                hits.add(h.isEmpty() ? null : h.get(0));
            }
            if (clean) {
                if (lang >= 0) { r.invalidReason = "both languages carry the full donation tuple"; return r; }
                lang = i;
                els = hits.toArray(new org.w3c.dom.Element[0]);
            }
        }
        if (langMarkers[0] == 0 && langMarkers[1] == 0) { r.absent = true; return r; }
        if (lang < 0 || langMarkers[1 - lang] != 0) {
            r.invalidReason = "partial/ambiguous/other-language donation markers (en=" + langMarkers[0] + " ar=" + langMarkers[1] + ")";
            return r;
        }
        r.lang = lang;
        r.tupleTexts = new String[]{title[lang], message[lang], decline[lang], confirm[lang]};

        // Unique app android:id/content dialog subtree containing all four tuple nodes.
        org.w3c.dom.Element dialog = null;
        for (int i = 0; i < all.getLength(); i++) {
            org.w3c.dom.Element e = (org.w3c.dom.Element) all.item(i);
            if (!pkg.equals(e.getAttribute("package")) || !"android:id/content".equals(e.getAttribute("resource-id"))) continue;
            boolean inside = true;
            for (org.w3c.dom.Element t : els) if (!isDescendant(e, t)) { inside = false; break; }
            if (!inside) continue;
            if (dialog != null) { r.invalidReason = "more than one app android:id/content subtree carries the donation tuple"; return r; }
            dialog = e;
        }
        if (dialog == null) { r.invalidReason = "donation tuple not inside one app android:id/content subtree"; return r; }
        int[] db = boundsOf(dialog);
        if (db == null || db[0] < 0 || db[1] < 0 || db[2] <= db[0] || db[3] <= db[1] || db[2] > dw || db[3] > dh) {
            r.invalidReason = "dialog bounds invalid " + java.util.Arrays.toString(db);
            return r;
        }

        // Decline/confirm clickable ancestors: DOM descendants of the dialog only; distinct; confirm
        // is resolved (it must exist) but never clicked.
        org.w3c.dom.Element declineBtn = nearestClickable(dialog, db, els[2], r.tupleTexts[2], r.tupleTexts[3], dw, dh);
        org.w3c.dom.Element confirmBtn = nearestClickable(dialog, db, els[3], r.tupleTexts[3], r.tupleTexts[2], dw, dh);
        if (declineBtn == null || confirmBtn == null || declineBtn == confirmBtn) {
            r.invalidReason = "decline/confirm clickable ancestors missing or not distinct";
            return r;
        }
        r.dialogPath = pathOf(dialog);
        r.declinePath = pathOf(declineBtn);
        r.confirmPath = pathOf(confirmBtn);
        r.dialogBounds = db;
        r.declineBounds = boundsOf(declineBtn);
        r.confirmBounds = boundsOf(confirmBtn);
        return r;
    }

    /** Total exact-text donation markers (all four labels, BOTH languages) in one dump. */
    private int donationMarkerCount(org.w3c.dom.NodeList all) {
        String[] names = {"donation_title", "donation_message", "donation_decline", "donation_confirm"};
        int n = 0;
        for (String name : names) {
            String[] texts = pair(name);
            n += exactTextNodes(all, texts[0]).size() + exactTextNodes(all, texts[1]).size();
        }
        return n;
    }

    /** Nodes whose text attribute EXACTLY equals s (no substring, no regex). */
    private static java.util.List<org.w3c.dom.Element> exactTextNodes(org.w3c.dom.NodeList all, String s) {
        java.util.List<org.w3c.dom.Element> out = new java.util.ArrayList<>();
        for (int i = 0; i < all.getLength(); i++) {
            org.w3c.dom.Element e = (org.w3c.dom.Element) all.item(i);
            if (s.equals(e.getAttribute("text"))) out.add(e);
        }
        return out;
    }

    private static boolean isDescendant(org.w3c.dom.Element ancestor, org.w3c.dom.Node n) {
        for (org.w3c.dom.Node p = n == null ? null : n.getParentNode(); p != null; p = p.getParentNode()) {
            if (p == ancestor) return true;
        }
        return false;
    }

    private static boolean subtreeHasText(org.w3c.dom.Element e, String s) {
        if (s.equals(e.getAttribute("text"))) return true;
        org.w3c.dom.NodeList kids = e.getElementsByTagName("node");
        for (int i = 0; i < kids.getLength(); i++) {
            if (s.equals(((org.w3c.dom.Element) kids.item(i)).getAttribute("text"))) return true;
        }
        return false;
    }

    /**
     * Nearest app-package enabled clickable ancestor of start, walking up ONLY while still a DOM
     * descendant of dialog (stops at the dialog element itself, never goes past it), whose subtree
     * carries the include text and NOT the exclude text; null if none. BOTH membership checks are
     * required together: DOM ancestry inside the dialog subtree AND the candidate's rectangle wholly
     * within the dialog bounds, plus positive on-screen bounds.
     */
    private org.w3c.dom.Element nearestClickable(org.w3c.dom.Element dialog, int[] dlg, org.w3c.dom.Element start, String include, String exclude, int dw, int dh) {
        for (org.w3c.dom.Node p = start.getParentNode(); p instanceof org.w3c.dom.Element; p = p.getParentNode()) {
            org.w3c.dom.Element e = (org.w3c.dom.Element) p;
            if (!isDescendant(dialog, e)) return null;
            if (!pkg.equals(e.getAttribute("package"))) continue;
            if (!"true".equals(e.getAttribute("clickable")) || !"true".equals(e.getAttribute("enabled"))) continue;
            if (!subtreeHasText(e, include) || subtreeHasText(e, exclude)) continue;
            int[] b = boundsOf(e);
            if (b == null || b[0] < 0 || b[1] < 0 || b[2] <= b[0] || b[3] <= b[1] || b[2] > dw || b[3] > dh) continue;
            if (b[0] < dlg[0] || b[1] < dlg[1] || b[2] > dlg[2] || b[3] > dlg[3]) continue;
            return e;
        }
        return null;
    }

    private static int[] boundsOf(org.w3c.dom.Element e) {
        java.util.regex.Matcher m = BOUNDS.matcher(e.getAttribute("bounds"));
        if (!m.matches()) return null;
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))};
    }

    private void openFrameScreenFromHome() throws Exception {
        dismissDonationDialogIfPresent();
        device.wait(Until.hasObject(By.pkg(pkg).scrollable(true)), 30000);
        scrollHomeToTop();
        boolean clicked = false;
        for (int i = 1; i <= 10 && !clicked; i++) {
            clicked = clickModuleCardFromSnapshots("search-" + i);
            if (clicked) break;
            int w = device.getDisplayWidth();
            int h = device.getDisplayHeight();
            boolean swiped = device.swipe(w / 2, (int) (h * 0.75), w / 2, (int) (h * 0.35), 20);
            // Diagnostic retention only: the swipe itself, its arguments and result are logged; the
            // gesture, count, order and the 10-iteration budget are unchanged.
            android.util.Log.w("QA-COMPANION", "card-search swipe test=" + safe(testName) + " iteration=" + i
                    + " display=" + w + "x" + h + " from=" + (w / 2) + "," + (int) (h * 0.75)
                    + " to=" + (w / 2) + "," + (int) (h * 0.35) + " steps=20 swiped=" + swiped);
            device.waitForIdle();
        }
        if (!clicked) {
            // Retained diagnostic ONLY: the state after the final swipe. NOT an 11th scan and no click.
            snapshot("search-postfinalswipe-diagnostic");
        }
        assertTrue("Frame module card not found on Home after scrolling", clicked);
        assertNotNull("Frame title never appeared", waitText(pair("frame_title"), 30000));
    }

    private void assertFrameScreenVisible() {
        assertVisible(By.pkg(pkg).text(containsAny(pair("frame_title"))), "frame_title");
        assertVisible(By.pkg(pkg).text(equalsAny(pair("frame_tab_nodes"))), "frame_tab_nodes");
    }

    @Test
    public void frameAnalysisEntryShowsRealFrameScreen() throws Exception {
        openFrameScreenFromHome();
        assertFrameScreenVisible();
        capture("entry");
    }

    @Test
    public void savedFrameModelOpensFromRealSeededRecord() throws Exception {
        openFrameScreenFromHome();
        assertFrameScreenVisible();
        capture("entry");

        assertSingleSummary(0, 0, 0, "empty baseline");

        UiObject2 actions = device.wait(Until.findObject(By.pkg(pkg).desc("Actions")), 15000);
        assertNotNull("Actions button not found", actions);
        actions.click();
        UiObject2 open = device.wait(Until.findObject(By.pkg(pkg).text(equalsAny(pair("frame_open_design")))), 15000);
        assertNotNull("Open Saved Model item not found", open);
        open.click();
        UiObject2 rec = device.wait(Until.findObject(By.pkg(pkg).text("QA_SENTINEL_FRAME_RECORD")), 15000);
        assertNotNull("seeded QA_SENTINEL_FRAME_RECORD not listed", rec);
        rec.click();

        assertSingleSummary(4, 3, 0, "loaded");
        capture("loaded-4-3");
    }
}
