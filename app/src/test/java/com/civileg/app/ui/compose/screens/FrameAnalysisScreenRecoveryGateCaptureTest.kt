package com.civileg.app.ui.compose.screens

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.core.view.drawToBitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import com.civileg.app.R
import com.civileg.app.db.DesignRepository
import com.civileg.app.viewmodel.FrameAnalysisViewModel
import com.civileg.app.viewmodel.FrameDraftStore
import com.civileg.app.viewmodel.ProjectViewModel
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Test-only pixel capture of the real recovery gate (title, retained text, export and start-new
 * buttons) under the APP resource configuration of each locale (Robolectric qualifiers, not a
 * target-context override). PNGs go to build/reports/tests/compose-shots, the same directory the
 * existing capture convention uses. JVM rendering: not a device and not a minified build.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameAnalysisScreenRecoveryGateCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: Application
    private val badV1 = "{\"a\":[{\"zz\":1}],\"b\":7}"
    private val vms = ArrayList<FrameAnalysisViewModel>()
    private val mounted = mutableStateOf(true)
    private var installed = false
    private var seenDirection: LayoutDirection? = null

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val p = app.getSharedPreferences("frame_modeler_draft", Context.MODE_PRIVATE)
        assertTrue(p.edit().clear().commit())
        assertTrue(p.edit().putString(FrameDraftStore.V1_KEY, badV1).commit())
    }

    @After fun tearDown() {
        if (installed) { rule.runOnIdle { mounted.value = false }; rule.waitForIdle() }
        vms.toList().forEach {
            val m = FrameAnalysisViewModel::class.java.getDeclaredMethod("onCleared"); m.isAccessible = true; m.invoke(it)
        }
    }

    private fun capture(locale: String, expectLang: String, expectRtl: Boolean) {
        // The locale must be the APP configuration the screen actually reads.
        assertEquals(expectLang, app.resources.configuration.locales[0].language)
        val title = app.getString(R.string.frame_draft_recovery_title)
        assertEquals(expectRtl, title.any { it in '\u0600'..'\u06FF' })
        val projectVm = mockk<ProjectViewModel>(relaxed = true) {
            every { allProjects } returns MutableLiveData(emptyList())
            every { allDesigns } returns MutableLiveData(emptyList())
        }
        val vm = FrameAnalysisViewModel(app, mockk<DesignRepository>(relaxed = true)).also { vms.add(it) }
        installed = true
        rule.setContent {
            seenDirection = LocalLayoutDirection.current
            if (mounted.value) MaterialTheme {
                FrameAnalysisScreen(viewModel = vm, projectViewModel = projectVm, onNavigateBack = {})
            }
        }
        rule.waitForIdle()
        assertNotNull(vm.draftRecovery.value)
        assertEquals(if (expectRtl) LayoutDirection.Rtl else LayoutDirection.Ltr, seenDirection)
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.frame_draft_recovery_retained)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.frame_draft_recovery_export)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.frame_draft_recovery_start_new)).assertIsDisplayed()
        // Same full-content capture route as the working DrawingCoerceGuardTest: the activity content view on the UI thread.
        var bmp: Bitmap? = null
        rule.runOnUiThread {
            val content = rule.activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue("content view is not attached", content.isAttachedToWindow)
            assertTrue("content view has no measured size", content.width > 0 && content.height > 0)
            bmp = content.drawToBitmap()
        }
        val shot = bmp!!
        assertTrue("bitmap has no size", shot.width > 0 && shot.height > 0)
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        val out = File(dir, "frame-recovery-gate-$locale.png")
        FileOutputStream(out).use { assertTrue("png compress failed", shot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        assertTrue("png file is empty", out.length() > 0)
        // Diagnostic only, not the correctness gate: the capture must not be one flat colour.
        val seen = HashSet<Int>()
        for (x in 0 until shot.width step 4) for (y in 0 until shot.height step 4) seen.add(shot.getPixel(x, y))
        assertTrue("capture is a single flat colour", seen.size > 3)
    }

    @Config(sdk = [34], qualifiers = "en-rUS-w411dp-h1200dp")
    @Test fun captureGateEnglish() = capture("en", "en", false)

    @Config(sdk = [34], qualifiers = "ar-rEG-w411dp-h1200dp")
    @Test fun captureGateArabicRtl() = capture("ar", "ar", true)
}
