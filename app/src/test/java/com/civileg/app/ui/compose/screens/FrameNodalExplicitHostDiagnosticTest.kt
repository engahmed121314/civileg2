package com.civileg.app.ui.compose.screens

import android.app.Application
import android.graphics.Bitmap
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import java.util.concurrent.atomic.AtomicInteger
import org.robolectric.shadows.ShadowDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.civileg.app.R
import com.civileg.app.domain.entities.*
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import com.civileg.app.utils.LocaleHelper
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Controlled explicit Activity/app-theme fixture comparison against the retained55d7 nodalEN failure.
 * The production NodalLoadDialog and every original nodal assertion/input remain unchanged.
 * This diagnostic does not replace, disable, or establish acceptance for the original six tests.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameNodalExplicitHostDiagnosticTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private var priorLanguage: String? = null
    @Before fun alignSelectedLanguageWithTestResources() {
        val prefs = app.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        priorLanguage = prefs.getString("language", null)
        prefs.edit().putString("language", app.resources.configuration.locales[0].language).commit()
        LocaleHelper.initApplicationContext(app)
    }
    @After fun restoreLanguagePreference() {
        val edit = app.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE).edit()
        if (priorLanguage == null) edit.remove("language") else edit.putString("language", priorLanguage)
        edit.commit()
    }
    private fun field(index: Int) = rule.onAllNodes(hasSetTextAction())[index]
    private fun capture(name: String) {
        val image = rule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "frame-input-$name.png")).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private val compositions = AtomicInteger()
    private val placements = AtomicInteger()
    @androidx.compose.runtime.Composable
    private fun Tracked(content: @androidx.compose.runtime.Composable () -> Unit) {
        SideEffect { compositions.incrementAndGet() }
        Box(Modifier.onGloballyPositioned { placements.incrementAndGet() }) { content() }
    }
    private fun diagnostic(stage: String, block: () -> Unit) {
        println("Frame idle diagnostic: begin $stage")
        try { block(); println("Frame idle diagnostic: passed $stage") }
        finally {
            // No Compose idleness call here: retain diagnostics even when idleness is the failure.
            println("Frame idle diagnostic: $stage compositions=${compositions.get()} placements=${placements.get()}")
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
                fun inspect(name: String, root: android.view.View?) {
                    if (root == null) { println("Frame idle diagnostic: $name absent"); return }
                    var requested=0;var attached=0;var total=0
                    fun visit(v:android.view.View) {
                        total++;if(v.isLayoutRequested)requested++;if(v.isAttachedToWindow)attached++
                        if(v is android.view.ViewGroup) for(i in 0 until v.childCount)visit(v.getChildAt(i))
                    }
                    visit(root)
                    println("Frame idle diagnostic: $name size=${root.width}x${root.height} views=$total layoutRequested=$requested attached=$attached shown=${root.isShown} focus=${root.hasWindowFocus()}")
                }
                inspect("activity",rule.activity.window.decorView)
                inspect("dialog",ShadowDialog.getLatestDialog()?.window?.decorView)
            }
        }
    }
    @Test @Config(sdk=[34],qualifiers="en-rUS-w411dp-h800dp")
    fun inputAloneExplicitHost() = diagnostic("input-alone") {
        rule.setContent { CivilEngineerPremiumTheme(darkTheme=false) { Tracked {
            FrameInputField("NaN",{},"Fx (kN)")
        } } }
        rule.onNodeWithText(app.getString(R.string.frame_input_finite)).assertIsDisplayed()
    }
    @Test @Config(sdk=[34],qualifiers="en-rUS-w411dp-h800dp")
    fun inputInsideMaterialDialogExplicitHost() = diagnostic("input-in-dialog") {
        rule.setContent { CivilEngineerPremiumTheme(darkTheme=false) { Tracked {
            AlertDialog(onDismissRequest={},confirmButton={Text("Confirm")},text={FrameInputField("NaN",{},"Fx (kN)")})
        } } }
        rule.onNodeWithText(app.getString(R.string.frame_input_finite)).assertIsDisplayed()
    }
    private fun nodal(locale: String) {
        assertEquals(locale, app.resources.configuration.locales[0].language)
        var calls = 0
        var result: Triple<Double, Double, Double>? = null
        val opened = mutableStateOf(true)
        println("Frame nodal diagnostic: before explicit Activity/app-theme setContent")
        rule.setContent { CivilEngineerPremiumTheme(darkTheme = false) { Tracked { if(opened.value) NodalLoadDialog(1, onDismiss = {}, onConfirm = { x,y,z,_ -> calls++;result=Triple(x,y,z);opened.value=false }) } } }
        println("Frame nodal diagnostic: content reached idle; entering NaN")
        field(0).performTextReplacement("NaN")
        field(1).performTextReplacement("-12.5")
        field(2).performTextReplacement("2")
        rule.onNodeWithText(if(locale=="ar") "إضافة" else "Add").performClick()
        assertEquals(0,calls)
        rule.onNodeWithText(app.getString(R.string.frame_input_finite)).assertIsDisplayed()
        field(0).assertTextContains("NaN")
        field(1).assertTextContains("-12.5")
        println("Frame nodal diagnostic: invalid error assertions passed; capturing")
        capture("nodal-host-diagnostic-$locale")
        field(0).performTextReplacement(if(locale=="ar") "١٫٥" else "1.5")
        rule.onNodeWithText(if(locale=="ar") "إضافة" else "Add").performClick()
        rule.runOnIdle { assertEquals(1,calls);assertEquals(Triple(1.5,-12.5,2.0),result) }
    }
    @Config(sdk=[34],qualifiers="en-rUS-w411dp-h800dp")
    @Test fun nodalInvalidEnglishRetainsAllTextAndCorrectsWithoutZeroFallback() = diagnostic("production-nodal-original-assertions") { nodal("en") }
}
