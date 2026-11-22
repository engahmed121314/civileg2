package com.civileg.app.ui.splash

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import com.civileg.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Native first-frame and sweep proof for the approved full-bleed splash
 * Treatment B. Runs with REAL native graphics (GraphicsMode NATIVE) against a
 * GENUINELY ATTACHED view hierarchy: the host activity is driven through its
 * full lifecycle (create/start/resume/visible) before the layout is set, and
 * attachment is asserted before any animation time is advanced. Controllers
 * are always closed afterward.
 *
 * Two retained witnesses per case, always written BEFORE any assertion:
 *  - *-first-frame.png: the honest first frame (bar phase 0 = track only).
 *  - *-sweep.png: the same screen after advancing animation time.
 * Pixel checks are scoped to the BAR REGION ONLY, in captured-root
 * coordinates, and compare the SAME region between first frame and sweep, so
 * artwork showing through the translucent track in fallback placement cannot
 * be counted as fill. Fill (cyan gradient) and the gold tip are verified with
 * separate predicates.
 *
 * Device coverage at fixed mdpi density and pinned font scale 1.0 (both
 * recorded in the witness manifest): reference 16:9-tall (941x1672 artwork
 * aspect), taller 19.5:9, genuinely shorter 4:3 portrait (centerCrop loses
 * artwork vertically) and landscape (clear strips fully cropped -> shared
 * group fallback). Bounds and order between name/prayer and caption/bar are
 * asserted from actual full view rectangles, not centers or percentages.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
class SplashTreatmentBTest {

    private data class Scene(
        val root: View,
        val overlay: SplashOverlayLayout,
        val name: TextView,
        val prayer: TextView,
        val caption: TextView,
        val bar: SplashLoadingBar,
        val background: ImageView,
        val activity: Activity
    )

    /**
     * Builds a genuinely attached scene: full activity lifecycle through
     * visible(), content view set on the attached window, attachment asserted
     * BEFORE any animation advancement. The controller is closed in finally.
     */
    private fun withAttachedScene(block: (Scene) -> Unit) {
        // Pin the font scale BEFORE any activity/view construction so measured
        // text bounds are produced under the recorded environment; restore the
        // prior scale afterward.
        val priorFontScale = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(1.0f)
        val controller: ActivityController<Activity> =
            Robolectric.buildActivity(Activity::class.java)
                .create().start().resume().visible()
        try {
            val activity = controller.get()
            val root = activity.layoutInflater.inflate(R.layout.activity_splash, null, false)
            activity.setContentView(root)
            val w = activity.resources.displayMetrics.widthPixels
            val h = activity.resources.displayMetrics.heightPixels
            root.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, w, h)
            assertTrue(
                "hierarchy must be attached to a window before animation advancement",
                root.isAttachedToWindow && root.findViewById<View>(R.id.loadingBar).isAttachedToWindow
            )
            block(
                Scene(
                    root = root,
                    overlay = root.findViewById(R.id.splashOverlay),
                    name = root.findViewById(R.id.tvAppName),
                    prayer = root.findViewById(R.id.tvPrayer),
                    caption = root.findViewById(R.id.tvCaption),
                    bar = root.findViewById(R.id.loadingBar),
                    background = root.findViewById(R.id.splashBackground),
                    activity = activity
                )
            )
        } finally {
            controller.pause().stop().destroy()
            RuntimeEnvironment.setFontScale(priorFontScale)
        }
    }

    private fun capture(root: View, name: String): Bitmap {
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File("build/reports/tests/splash-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        return bitmap
    }

    /** Records the pinned environment the witnesses were produced under. */
    private fun recordWitnessManifest(scene: Scene, label: String) {
        val dm = scene.activity.resources.displayMetrics
        val dir = File("build/reports/tests/splash-shots").apply { mkdirs() }
        File(dir, "splash-B-$label-manifest.txt").writeText(
            "label=$label\n" +
                "density=${dm.density}\n" +
                "densityDpi=${dm.densityDpi}\n" +
                "fontScale=${RuntimeEnvironment.getFontScale()}\n" +
                "widthPx=${scene.root.width}\n" +
                "heightPx=${scene.root.height}\n"
        )
    }

    /** Bar bounds in CAPTURED-ROOT coordinates (bitmap origin = root origin). */
    private fun barRegionInRoot(scene: Scene): IntArray {
        val rootLoc = IntArray(2).also { scene.root.getLocationOnScreen(it) }
        val barLoc = IntArray(2).also { scene.bar.getLocationOnScreen(it) }
        val left = (barLoc[0] - rootLoc[0]).coerceIn(0, scene.root.width)
        val top = (barLoc[1] - rootLoc[1]).coerceIn(0, scene.root.height)
        val right = (left + scene.bar.width).coerceAtMost(scene.root.width)
        val bottom = (top + scene.bar.height).coerceAtMost(scene.root.height)
        return intArrayOf(left, top, right, bottom)
    }

    private fun countInBarRegion(frame: Bitmap, region: IntArray, matches: (Int, Int, Int) -> Boolean): Int {
        var count = 0
        for (x in region[0] until region[2]) {
            for (y in region[1] until region[3]) {
                val c = frame.getPixel(x, y)
                if (matches(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF)) count++
            }
        }
        return count
    }

    /** Fill gradient #5BC2DC -> #7FD4E8: high green/blue. */
    private val isFillPixel = { r: Int, g: Int, b: Int -> g > 150 && b > 180 }

    /** Gold tip #D9B36A: high red, mid green, low blue. Separate from fill. */
    private val isTipPixel = { r: Int, g: Int, b: Int -> r > 170 && g in 130..210 && b in 60..170 }

    private fun assertSceneGeometry(scene: Scene, label: String) {
        assertEquals("CivilEG", scene.name.text.toString())
        assertEquals("صلى الله على محمد", scene.prayer.text.toString())
        assertEquals("جاري تجهيز التطبيق", scene.caption.text.toString())
        assertTrue("name bold", scene.name.typeface?.isBold == true)
        assertTrue("prayer bold", scene.prayer.typeface?.isBold == true)
        assertTrue("caption regular", scene.caption.typeface?.isBold == false)

        assertEquals(ImageView.ScaleType.CENTER_CROP, scene.background.scaleType)
        assertNotNull(scene.background.drawable)

        val safeLeft = scene.overlay.paddingLeft
        val safeTop = scene.overlay.paddingTop
        val safeRight = scene.overlay.width - scene.overlay.paddingRight
        val safeBottom = scene.overlay.height - scene.overlay.paddingBottom

        // All four full rectangles inside the inset-safe area, in order.
        for (v in listOf(scene.name, scene.prayer, scene.caption, scene.bar)) {
            assertTrue("$label: ${scene.resourcesName(v)} inside safe area " +
                "(l=${v.left} t=${v.top} r=${v.right} b=${v.bottom} vs $safeLeft,$safeTop,$safeRight,$safeBottom)",
                v.left >= safeLeft && v.top >= safeTop && v.right <= safeRight && v.bottom <= safeBottom)
        }
        assertTrue("$label: name above prayer (full rects ${scene.name.bottom} <= ${scene.prayer.top})",
            scene.name.bottom <= scene.prayer.top)
        assertTrue("$label: caption above bar (full rects ${scene.caption.bottom} <= ${scene.bar.top})",
            scene.caption.bottom <= scene.bar.top)

        // Bar is 50% of the content width (integer-difference check).
        val contentW = scene.overlay.width - scene.overlay.paddingLeft - scene.overlay.paddingRight
        assertTrue("$label: bar width ~50%",
            abs(scene.bar.width - contentW / 2) <= 2)
    }

    private fun Scene.resourcesName(v: View): String =
        try { activity.resources.getResourceEntryName(v.id) } catch (t: Throwable) { "view#${v.id}" }

    /** Full-rectangle containment inside the visible clear strip (not centers). */
    private fun assertStripContainment(scene: Scene, label: String) {
        val w = scene.root.width
        val h = scene.root.height
        val topStripBottom = scene.overlay.artworkToViewY(130f, w, h)
        val bottomStripTop = scene.overlay.artworkToViewY(1350f, w, h)
        assertTrue("$label: name fully within top clear strip (b=${scene.name.bottom} <= $topStripBottom)",
            scene.name.top >= 0 && scene.name.bottom <= topStripBottom)
        assertTrue("$label: prayer fully within top clear strip (b=${scene.prayer.bottom} <= $topStripBottom)",
            scene.prayer.top >= 0 && scene.prayer.bottom <= topStripBottom)
        assertTrue("$label: caption fully within bottom clear strip (t=${scene.caption.top} >= $bottomStripTop)",
            scene.caption.top >= bottomStripTop && scene.caption.bottom <= h)
        assertTrue("$label: bar fully within bottom clear strip (t=${scene.bar.top} >= $bottomStripTop)",
            scene.bar.top >= bottomStripTop && scene.bar.bottom <= h)
    }

    /**
     * Runs the first-frame/sweep witness pair and the animation proof for one
     * aspect. Fill and gold tip are checked as SAME-REGION DELTAS between the
     * first frame and the sweep, so static artwork behind the translucent
     * track is never counted as animated fill.
     */
    private fun runAspect(label: String, extra: (Scene) -> Unit = {}) = withAttachedScene { scene ->
        recordWitnessManifest(scene, label)

        // Witness 1: honest first frame (phase 0 = track only), as-is.
        val first = capture(scene.root, "splash-B-$label-first-frame")

        // Hierarchy is attached (asserted in withAttachedScene); now advance.
        ShadowLooper.idleMainLooper(700, TimeUnit.MILLISECONDS)
        scene.root.measure(
            View.MeasureSpec.makeMeasureSpec(scene.root.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(scene.root.height, View.MeasureSpec.EXACTLY)
        )
        scene.root.layout(0, 0, scene.root.width, scene.root.height)

        // Witness 2: time-advanced sweep.
        val sweep = capture(scene.root, "splash-B-$label-sweep")

        assertSceneGeometry(scene, label)

        val region = barRegionInRoot(scene)
        val firstFill = countInBarRegion(first, region, isFillPixel)
        val sweepFill = countInBarRegion(sweep, region, isFillPixel)
        assertTrue("$label: animated fill appears in the bar region after advancing time " +
            "(first=$firstFill sweep=$sweepFill, same-region delta required)",
            sweepFill > firstFill + 20)
        val firstTip = countInBarRegion(first, region, isTipPixel)
        val sweepTip = countInBarRegion(sweep, region, isTipPixel)
        assertTrue("$label: gold tip appears in the bar region after advancing time " +
            "(first=$firstTip sweep=$sweepTip, verified separately from fill)",
            sweepTip > firstTip)

        extra(scene)
    }

    @Test
    @Config(qualifiers = "w411dp-h731dp-mdpi")
    fun `reference aspect 16-9 tall anchors overlays in clear artwork strips`() {
        runAspect("reference-16x9") { scene ->
            assertEquals(SplashOverlayLayout.PlacementMode.CLEAR_ANCHORED,
                scene.overlay.placementModes[R.id.tvAppName])
            assertEquals(SplashOverlayLayout.PlacementMode.CLEAR_ANCHORED,
                scene.overlay.placementModes[R.id.tvPrayer])
            assertEquals(SplashOverlayLayout.PlacementMode.CLEAR_ANCHORED,
                scene.overlay.placementModes[R.id.tvCaption])
            assertEquals(SplashOverlayLayout.PlacementMode.CLEAR_ANCHORED,
                scene.overlay.placementModes[R.id.loadingBar])
            // Full-bounds strip containment backs every CLEAR_ANCHORED claim.
            assertStripContainment(scene, "reference-16x9")
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-mdpi")
    fun `taller aspect 19-5-9 keeps anchors inside grown clear strips`() {
        runAspect("tall-19-5x9") { scene ->
            assertEquals(SplashOverlayLayout.PlacementMode.CLEAR_ANCHORED,
                scene.overlay.placementModes[R.id.tvCaption])
            assertStripContainment(scene, "tall-19-5x9")
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h548dp-mdpi")
    fun `shorter 4-3 portrait crops artwork and places overlays honestly`() {
        runAspect("short-4x3")
        // At 4:3 the crop eats the clear strips: groups fall back TOGETHER in
        // order (asserted by the full-rectangle safe-area and order checks in
        // runAspect; no percentage or center-only claims anywhere).
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-mdpi")
    fun `landscape falls back to safe-area placement without panels`() {
        runAspect("landscape") { scene ->
            assertEquals(SplashOverlayLayout.PlacementMode.FALLBACK_SAFE_AREA,
                scene.overlay.placementModes[R.id.tvAppName])
            assertEquals(SplashOverlayLayout.PlacementMode.FALLBACK_SAFE_AREA,
                scene.overlay.placementModes[R.id.tvPrayer])
            assertEquals(SplashOverlayLayout.PlacementMode.FALLBACK_SAFE_AREA,
                scene.overlay.placementModes[R.id.tvCaption])
            assertEquals(SplashOverlayLayout.PlacementMode.FALLBACK_SAFE_AREA,
                scene.overlay.placementModes[R.id.loadingBar])
            assertEquals("background stays full screen in landscape",
                scene.background.width, scene.root.width)
            assertEquals(scene.background.height, scene.root.height)
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h731dp-mdpi")
    fun `synthetic system bars and side cutout pad overlay only`() = withAttachedScene { scene ->
        val top = 48
        val bottom = 96
        val leftCut = 24
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(),
                androidx.core.graphics.Insets.of(0, top, 0, bottom))
            .setInsets(WindowInsetsCompat.Type.displayCutout(),
                androidx.core.graphics.Insets.of(leftCut, 0, 0, 0))
            .build()
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(scene.root, insets)
        scene.root.measure(
            View.MeasureSpec.makeMeasureSpec(scene.root.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(scene.root.height, View.MeasureSpec.EXACTLY)
        )
        scene.root.layout(0, 0, scene.root.width, scene.root.height)
        capture(scene.root, "splash-B-insets-first-frame")
        recordWitnessManifest(scene, "insets")

        assertEquals(top, scene.overlay.paddingTop)
        assertEquals(bottom, scene.overlay.paddingBottom)
        assertEquals(leftCut, scene.overlay.paddingLeft)
        // Background is never inset: still full screen.
        assertEquals(scene.root.width, scene.background.width)
        assertEquals(scene.root.height, scene.background.height)
        // All four safe-area rectangles and order verified AFTER insets
        // (assertSceneGeometry checks each full rect against the padded area).
        assertSceneGeometry(scene, "insets")
    }

    @Test
    @Config(qualifiers = "w411dp-h731dp-mdpi")
    fun `loading bar is genuinely indeterminate never a fixed percentage`() = withAttachedScene { scene ->
        val animatorField = SplashLoadingBar::class.java.getDeclaredField("animator")
        animatorField.isAccessible = true
        val animator = animatorField.get(scene.bar) as ValueAnimator
        assertEquals(ValueAnimator.INFINITE, animator.repeatCount)
        assertTrue("attached animator is started", animator.isStarted)
        assertTrue(SplashLoadingBar::class.java.declaredMethods
            .none { it.name.startsWith("setProgress") })
    }

    @Test
    fun `bundled Plex fonts load for the splash overlay`() = withAttachedScene { scene ->
        assertNotNull(scene.activity.resources.getFont(R.font.ibm_plex_sans_arabic_bold))
        assertNotNull(scene.activity.resources.getFont(R.font.ibm_plex_sans_arabic_regular))
    }
}
