package com.civileg.app

import android.app.UiAutomation
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Reachability evidence on the real MainActivity, Arabic UI, every Design Hub page except Stair (own sample).
 * For each page, in portrait and then landscape:
 *  - configuration / navigation-type diagnostics (is a bottom bar or a rail shown, and does that match the width);
 *  - the page's primary action (the page's own calculate control, not a generic button) is scrolled into view
 *    and must be fully inside the scrolling content area, displayed, and clear of the navigation controls;
 *  - the vertical scroller is scrolled to its end, and its bounds must not overlap the navigation controls
 *    (content that ends under the bar would be unreachable);
 *  - every Tab is scrolled into view and must then be fully on screen. A tab cut off at the edge of a
 *    scrollable tab row before scrolling is expected and is not a failure; only an unreachable tab is.
 * All routes are checked and failures are collected, then asserted together, so one run reports everything.
 * Output: filesDir/drawing-samples/reachability (report text + one scroll-end screenshot per page/orientation).
 * Initial state only: no calculation is run here.
 */
class ReachabilityUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val out by lazy { File(context.filesDir, "drawing-samples/reachability").apply { mkdirs() } }

    private fun ar(@StringRes id: Int): String {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale("ar"))
        return context.createConfigurationContext(config).getString(id)
    }

    private fun scroll(text: String) {
        rule.waitUntil(20000) {
            runCatching { rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text)) }
            rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitForIdle()
    }

    private fun assertNoBlockingAnr() {
        val root = instrumentation.uiAutomation.rootInActiveWindow
        if (root != null) {
            val launcher = root.findAccessibilityNodeInfosByText("Pixel Launcher isn't responding").isNotEmpty()
            val systemUi = root.findAccessibilityNodeInfosByText("System UI isn't responding").isNotEmpty()
            if (launcher || systemUi) {
                root.findAccessibilityNodeInfosByText(if (systemUi) "Wait" else "Close app").firstOrNull()
                    ?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                android.os.SystemClock.sleep(1000)
            }
        }
        val active = instrumentation.uiAutomation.rootInActiveWindow
        assertTrue("An ANR obstructs the sample", active?.findAccessibilityNodeInfosByText("isn't responding").isNullOrEmpty())
    }

    private fun shot(name: String) {
        assertNoBlockingAnr()
        val bmp = checkNotNull(instrumentation.uiAutomation.takeScreenshot()) { "no screenshot for $name" }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Independent truth for orientation: the compose root and a real screenshot, not Activity resources. */
    private fun actualIsLandscape(): Pair<Boolean, String> {
        val root = rootBounds()
        val bmp = instrumentation.uiAutomation.takeScreenshot()
        val shotLandscape = bmp != null && bmp.width > bmp.height
        val desc = "root=${root.width.toInt()}x${root.height.toInt()}px screenshot=${bmp?.width}x${bmp?.height}"
        bmp?.recycle()
        return (root.width > root.height && shotLandscape) to desc
    }

    /** Rotates and waits until BOTH the compose root and a screenshot confirm the orientation; throws if never reached. */
    private fun rotate(landscape: Boolean) {
        instrumentation.uiAutomation.setRotation(
            if (landscape) UiAutomation.ROTATION_FREEZE_90 else UiAutomation.ROTATION_FREEZE_0)
        var last = ""
        val deadline = android.os.SystemClock.uptimeMillis() + 15000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            rule.waitForIdle()
            val root = rootBounds()
            val bmp = instrumentation.uiAutomation.takeScreenshot()
            val confirmed = bmp != null && (if(landscape) root.width > root.height && bmp.width > bmp.height else root.height > root.width && bmp.height > bmp.width)
            last = "root=${root.width}x${root.height}; screenshot=${bmp?.width}x${bmp?.height}"
            bmp?.recycle()
            if (confirmed) {
                android.os.SystemClock.sleep(500)
                rule.waitForIdle()
                return
            }
            android.os.SystemClock.sleep(300)
        }
        throw AssertionError("requested landscape=$landscape but never achieved: $last")
    }

    @Before fun dismissPrompt() {
        rule.waitForIdle()
        if (rule.onAllNodesWithText(ar(R.string.donation_decline)).fetchSemanticsNodes().isNotEmpty())
            rule.onNodeWithText(ar(R.string.donation_decline)).performClick()
    }

    // ---------------------------------------------------------------- geometry helpers

    private val verticalScroller = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun SemanticsNode.inside(outer: Rect, tol: Float = 1f) =
        boundsInRoot.left >= outer.left - tol && boundsInRoot.right <= outer.right + tol &&
            boundsInRoot.top >= outer.top - tol && boundsInRoot.bottom <= outer.bottom + tol

    private fun rootBounds(): Rect = rule.onRoot().fetchSemanticsNode().boundsInRoot

    /** Bottom nav bar, or navigation rail. Located from the two nav labels that no page title reuses. */
    private data class Nav(val kind: String, val rects: List<Rect>)

    private fun nav(): Nav {
        val labels = listOf(ar(R.string.nav_tools), ar(R.string.nav_more))
        val rects = labels.mapNotNull { l ->
            rule.onAllNodesWithText(l, useUnmergedTree = true).fetchSemanticsNodes().maxByOrNull { it.boundsInRoot.top }?.boundsInRoot
        }
        if (rects.size < 2) return Nav("not-found", rects)
        val sameRow = kotlin.math.abs(rects[0].top - rects[1].top) < 4f
        return Nav(if (sameRow) "bar" else "rail", rects)
    }

    private fun overlapsNav(r: Rect, nav: Nav) = nav.rects.any { it.overlaps(r) }

    private fun firstVerticalScroller(): SemanticsNode? {
        val nodes = rule.onAllNodes(verticalScroller).fetchSemanticsNodes()
        // largest area is the page's own list; small ones are dropdowns / nested panels
        return nodes.maxByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }
    }

    private fun scrollerFinished(node: SemanticsNode): Boolean {
        val range = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return true
        return range.value() >= range.maxValue() - 0.5f
    }

    private fun swipeUpOnPageScroller() {
        val before = firstVerticalScroller() ?: return
        val h = before.boundsInRoot.height
        rule.onAllNodes(verticalScroller).filter(SemanticsMatcher("largest scroller") {
            it.boundsInRoot == before.boundsInRoot
        }).onFirst().performTouchInput {
            swipe(start = androidx.compose.ui.geometry.Offset(centerX, h * 0.8f), end = androidx.compose.ui.geometry.Offset(centerX, h * 0.2f), durationMillis = 250)
        }
        rule.waitForIdle()
    }

    private fun scrollPageToEnd(): Int {
        var swipes = 0
        while (swipes < 60) {
            val node = firstVerticalScroller() ?: return swipes
            if (scrollerFinished(node)) return swipes
            swipeUpOnPageScroller()
            swipes++
        }
        return swipes
    }

    private fun primaryNodeOrNull(label: String): SemanticsNode? =
        rule.onAllNodes(hasText(label) and hasClickAction()).fetchSemanticsNodes().firstOrNull()

    /** Brings the primary action into the scroller: semantic scroll first, then swipes if that did not do it. */
    private fun bringPrimaryIntoView(label: String): SemanticsNode? {
        val matcher = hasText(label) and hasClickAction()
        rule.onAllNodes(verticalScroller).fetchSemanticsNodes().indices.forEach { i ->
            runCatching { rule.onAllNodes(verticalScroller)[i].performScrollToNode(matcher) }
        }
        rule.waitForIdle()
        var tries = 0
        while (tries < 60) {
            val n = primaryNodeOrNull(label)
            val sc = firstVerticalScroller()
            if (n != null && sc != null && n.inside(sc.boundsInRoot)) return n
            if (sc != null && scrollerFinished(sc)) return n
            swipeUpOnPageScroller()
            tries++
        }
        return primaryNodeOrNull(label)
    }

    // ---------------------------------------------------------------- spec

    private class Page(val slug: String, @StringRes val title: Int, val primary: (() -> String)?, val note: String)

    private val pages: List<Page> by lazy {
        fun r(id: Int) = { ar(id) }
        listOf(
            Page("beam", R.string.home_beam, r(R.string.beam_design_now), "form + design button"),
            Page("column", R.string.home_column, r(R.string.column_design_now), "form + design button"),
            Page("slab", R.string.home_slab, r(R.string.slab_design_now), "form + design button"),
            Page("footing", R.string.home_footing, r(R.string.footing_design_now), "form + design button"),
            Page("tank", R.string.home_tank, r(R.string.tank_start_design), "form + design button"),
            Page("retaining_wall", R.string.home_retaining_wall, r(R.string.rw_analyze), "form + analyze button"),
            Page("seismic", R.string.home_seismic, r(R.string.seismic_calc_button), "form + calc button"),
            Page("frame", R.string.home_frame, null, "tabbed canvas editor: tabs only, no single compute button"),
            Page("column_biaxial", R.string.home_column_biaxial, r(R.string.column_design_now), "hub card opens the Column route"),
            Page("pile", R.string.home_pile, r(R.string.pile_design_button), "form + design button"),
            Page("strap_footing", R.string.home_strap_footing, r(R.string.calculate), "form + calculate button"),
            Page("flat_slab", R.string.home_flat_slab, r(R.string.fs_design_button), "form + design button"),
            Page("waffle_slab", R.string.home_waffle_slab, r(R.string.waffle_slab_design_button), "form + design button"),
            Page("hordi_slab", R.string.home_hordi_slab, r(R.string.hordi_slab_design_button), "form + design button"),
            Page("shear_wall", R.string.home_shear_wall, r(R.string.sw_design_button), "form + design button"),
            Page("wall_opening", R.string.home_wall_opening, r(R.string.wo_calculate), "form + calculate button"),
            Page("steel", R.string.home_steel, r(R.string.steel_design_warehouse), "tabs; default tab is the warehouse design"),
            Page("quality_engineer", R.string.home_quality_engineer, null, "two tab rows, first module's own controls vary: tabs only"),
            Page("post_tensioned", R.string.home_post_tensioned, { "Calculate / احسب" }, "form + literal bilingual calculate button"),
            Page("advanced_design", R.string.home_advanced_design, { "Calculate / احسب" }, "module tabs + literal bilingual calculate button")
        )
    }

    private fun diagnostics(tag: String, nav: Nav, out: StringBuilder): Boolean {
        val res = rule.activity.resources
        val cfg = res.configuration
        val dm = res.displayMetrics
        val root = rootBounds()
        val realDensity = dm.density
        val rootWidthDp = root.width / realDensity
        val (realLandscape, desc) = actualIsLandscape()
        val expectRail = rootWidthDp >= 600 // WindowSize.COMPACT_BREAKPOINT_DP, from the real compose root width
        val actualRail = nav.kind == "rail"
        val mismatch = nav.kind != "not-found" && expectRail != actualRail
        val resourcesStale = (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) != realLandscape
        out.append("  [$tag] real: $desc landscape=$realLandscape rootWidthDp=${"%.0f".format(rootWidthDp)} | ")
            .append("activityResources: orientation=${cfg.orientation} screenWidthDp=${cfg.screenWidthDp} screenHeightDp=${cfg.screenHeightDp} pxWidth=${dm.widthPixels} resourcesOrientationDisagreesWithReal=$resourcesStale | ")
            .append("nav=${nav.kind} expectedRailFromRealWidth=$expectRail navMismatch=$mismatch\n")
        return mismatch
    }

    private fun checkOrientation(page: Page, landscape: Boolean, report: StringBuilder, failures: MutableList<String>) {
        val tag = "${page.slug}-${if (landscape) "landscape" else "portrait"}"
        val nav = nav()
        diagnostics(tag, nav, report)
        if (nav.kind == "not-found") failures += "$tag: navigation controls not found, cannot judge overlap"

        // Tabs: unscrolled clipping at a scrollable row's edge is expected; unreachable tabs are not.
        val tabCount = rule.onAllNodes(isTab).fetchSemanticsNodes().size
        val root = rootBounds()
        var unreachableTabs = 0
        for (i in 0 until tabCount) {
            runCatching { rule.onAllNodes(isTab)[i].performScrollTo() }
            rule.waitForIdle()
            val n = rule.onAllNodes(isTab).fetchSemanticsNodes().getOrNull(i) ?: continue
            if (!n.inside(root)) { unreachableTabs++; failures += "$tag: tab #$i is not fully on screen after scrolling its row ${n.boundsInRoot}" }
        }
        report.append("  [$tag] tabs=$tabCount unreachable=$unreachableTabs\n")

        // Primary action.
        val label = page.primary?.invoke()
        if (label != null) {
            val n = bringPrimaryIntoView(label)
            val sc = firstVerticalScroller()
            when {
                n == null -> {
                    failures += "$tag: primary action '$label' not found"
                    report.append("  [$tag] primary='$label' NOT FOUND\n")
                }
                else -> {
                    val inScroller = sc == null || n.inside(sc.boundsInRoot)
                    val clearOfNav = !overlapsNav(n.boundsInRoot, nav)
                    val inRoot = n.inside(root)
                    report.append("  [$tag] primary='$label' bounds=${n.boundsInRoot} scroller=${sc?.boundsInRoot} inScroller=$inScroller inRoot=$inRoot clearOfNav=$clearOfNav\n")
                    if (!inScroller || !inRoot) failures += "$tag: primary '$label' is clipped (inScroller=$inScroller inRoot=$inRoot)"
                    if (!clearOfNav) failures += "$tag: primary '$label' overlaps navigation controls"
                }
            }
        } else {
            report.append("  [$tag] primary: none by design (${page.note})\n")
        }

        // End of content must be above the navigation, not underneath it.
        val swipes = scrollPageToEnd()
        val sc = firstVerticalScroller()
        if (sc == null) {
            report.append("  [$tag] no vertical scroller (${page.note})\n")
        } else {
            val finished = scrollerFinished(sc)
            val scBounds = sc.boundsInRoot
            val under = nav.kind != "not-found" && overlapsNav(scBounds, nav)
            report.append("  [$tag] scrollEnd swipes=$swipes finished=$finished scroller=$scBounds overlapsNav=$under\n")
            if (!finished) failures += "$tag: could not reach the end of the page in $swipes swipes"
            if (under) failures += "$tag: scrolling area overlaps navigation controls $scBounds"
        }
        shot("$tag-scroll-end")
    }

    @Test fun primaryActionsAndTabsAreReachableInPortraitAndLandscape() {
        val report = StringBuilder("language=ar; scope=Design Hub pages except Stair, initial state, no calculation run\n")
        val failures = mutableListOf<String>()
        try {
            for (page in pages) {
                val title = ar(page.title)
                report.append("${page.slug}: ${page.note}\n")
                runCatching {
                    rule.onNodeWithText(ar(R.string.nav_design_hub)).performClick()
                    scroll(title)
                    rule.onNodeWithText(title).performClick()
                    rule.waitForIdle()
                    android.os.SystemClock.sleep(600)
                    checkOrientation(page, false, report, failures)
                    rotate(true)
                    checkOrientation(page, true, report, failures)
                    rotate(false)
                }.onFailure {
                    failures += "${page.slug}: test step failed: ${it::class.simpleName}: ${it.message?.take(200)}"
                    runCatching { rotate(false) }
                }
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                rule.waitForIdle()
            }
        } finally {
            runCatching { rotate(false) }
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            report.append("\nFAILURES (${failures.size}):\n").append(failures.joinToString("\n")).append('\n')
            File(out, "reachability-report.txt").writeText(report.toString())
        }
        assertTrue("Reachability failures, see reachability-report.txt:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Reproduction of the hypothesis that the navigation type does not follow the window width after rotation.
     * screenWidthDp >= 600 should show the rail (MainActivity: useNavigationRail = widthClass != COMPACT).
     * Failing here is the reproduction, with the diagnostic line in the message.
     */
    @Test fun navigationTypeFollowsWindowWidthAfterRotation() {
        val report = StringBuilder()
        var mismatches = 0
        try {
            rule.waitUntil(20000) { rule.onAllNodesWithText(ar(R.string.nav_more)).fetchSemanticsNodes().isNotEmpty() }
            for (landscape in listOf(false, true, false)) {
                rotate(landscape)
                val n = nav()
                if (diagnostics(if (landscape) "home-landscape" else "home-portrait", n, report)) mismatches++
                shot(if (landscape) "nav-home-landscape" else "nav-home-portrait")
            }
        } finally {
            instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
            File(out, "nav-type-report.txt").writeText(report.toString())
        }
        assertEquals("Navigation type does not match the window width after rotation:\n$report", 0, mismatches)
    }
}
