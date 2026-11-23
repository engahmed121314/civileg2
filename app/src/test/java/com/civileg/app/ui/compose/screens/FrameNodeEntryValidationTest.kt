package com.civileg.app.ui.compose.screens

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.livedata.observeAsState
import androidx.core.view.drawToBitmap
import org.robolectric.shadows.ShadowDialog
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.civileg.app.R
import com.civileg.app.db.DesignRepository
import com.civileg.app.viewmodel.FrameAnalysisViewModel
import io.mockk.mockk
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual NodesTab + real mutation/autosave VM, not a test form. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameNodeEntryValidationTest {
    @get:Rule val rule=createComposeRule()
    private lateinit var vm:FrameAnalysisViewModel
    private val mounted=mutableStateOf(true)
    private lateinit var app:Application
    @Before fun setup() {
        app=ApplicationProvider.getApplicationContext()
        // Default-width JVM fixture only; retained411dp diagnostic remains a separate control.
        val config=app.resources.configuration
        println("Frame default-width fixture: widthDp=${config.screenWidthDp} heightDp=${config.screenHeightDp} pixels=${app.resources.displayMetrics.widthPixels}x${app.resources.displayMetrics.heightPixels} locale=${config.locales[0]}")
        app.getSharedPreferences("frame_modeler_draft",Context.MODE_PRIVATE).edit().clear().commit()
        vm=FrameAnalysisViewModel(app,mockk<DesignRepository>(relaxed=true))
    }
    @After fun cleanup() {
        rule.runOnIdle { mounted.value=false };rule.waitForIdle()
        FrameAnalysisViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible=true }.invoke(vm)
    }
    private fun actualNodeDialog(locale:String) {
        assertEquals(locale,app.resources.configuration.locales[0].language)
        rule.setContent { MaterialTheme { if(mounted.value) {
            val nodes=vm.nodes.observeAsState(emptyList()).value
            val members=vm.members.observeAsState(emptyList()).value
            NodesTab(nodes,members,vm)
        } } }
        rule.onNodeWithText(app.getString(R.string.frame_add_node)).performClick()
        val fields=rule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("Infinity")
        fields[1].performTextReplacement("-3")
        rule.onNodeWithText(app.getString(R.string.save)).performClick()
        rule.runOnIdle { assertTrue(vm.nodes.value.orEmpty().isEmpty()) }
        rule.onNodeWithText(app.getString(R.string.frame_input_finite)).assertIsDisplayed()
        fields[1].assertTextContains("-3")
        rule.onNode(isDialog()).assertIsDisplayed()
        val bitmap=rule.runOnIdle {
            // Capture the actual shown native dialog, not the Activity behind its window.
            val dialog=requireNotNull(ShadowDialog.getLatestDialog())
            check(dialog.isShowing)
            val decor=requireNotNull(dialog.window).decorView
            check(decor.isShown && decor.width>0 && decor.height>0)
            decor.drawToBitmap()
        }
        val dir=java.io.File("build/reports/tests/compose-shots").apply{mkdirs()}
        java.io.FileOutputStream(java.io.File(dir,"frame-input-node-$locale.png")).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        fields[0].performTextReplacement(if(locale=="ar") "٢٫٥" else "2.5")
        rule.onNodeWithText(app.getString(R.string.save)).performClick()
        rule.runOnIdle { val node=vm.nodes.value.orEmpty().single();assertEquals(2.5,node.x,0.0);assertEquals(-3.0,node.y,0.0) }
    }
    @Test @Config(sdk=[34],qualifiers="en-rUS-h900dp")
    fun englishInvalidNodeDoesNotMutateAndCorrectedSignedCoordinatesSave() = actualNodeDialog("en")
    @Test @Config(sdk=[34],qualifiers="ar-rEG-h900dp")
    fun arabicInvalidNodeDoesNotMutateAndCorrectedArabicCoordinatesSave() = actualNodeDialog("ar")
}
