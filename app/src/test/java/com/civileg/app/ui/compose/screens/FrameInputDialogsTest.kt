package com.civileg.app.ui.compose.screens

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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

/** Actual production dialog functions, not a test-only form. No engine/store acceptance implied. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameInputDialogsTest {
    @get:Rule val rule = createComposeRule()
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
    private fun nodal(locale: String) {
        assertEquals(locale, app.resources.configuration.locales[0].language)
        var calls = 0
        var result: Triple<Double, Double, Double>? = null
        val opened = mutableStateOf(true)
        rule.setContent { MaterialTheme { if(opened.value) NodalLoadDialog(1, onDismiss = {}, onConfirm = { x,y,z,_ -> calls++;result=Triple(x,y,z);opened.value=false }) } }
        field(0).performTextReplacement("NaN")
        field(1).performTextReplacement("-12.5")
        field(2).performTextReplacement("2")
        rule.onNodeWithText(if(locale=="ar") "إضافة" else "Add").performClick()
        assertEquals(0,calls)
        rule.onNodeWithText(app.getString(R.string.frame_input_finite)).assertIsDisplayed()
        field(0).assertTextContains("NaN")
        field(1).assertTextContains("-12.5")
        capture("nodal-$locale")
        field(0).performTextReplacement(if(locale=="ar") "١٫٥" else "1.5")
        rule.onNodeWithText(if(locale=="ar") "إضافة" else "Add").performClick()
        rule.runOnIdle { assertEquals(1,calls);assertEquals(Triple(1.5,-12.5,2.0),result) }
    }
    @Config(sdk=[34],qualifiers="en-rUS-w411dp-h800dp")
    @Test fun nodalInvalidEnglishRetainsAllTextAndCorrectsWithoutZeroFallback() = nodal("en")
    @Config(sdk=[34],qualifiers="ar-rEG-w411dp-h800dp")
    @Test fun nodalInvalidArabicRetainsAllTextAndCorrectsWithoutZeroFallback() = nodal("ar")

    @Config(sdk=[34],qualifiers="en-rUS-w411dp-h900dp")
    @Test fun actualMemberLoadDialogRejectsOutsidePositionThenAcceptsEndpoint() {
        var calls=0;var result:Double?=null
        rule.setContent { MaterialTheme { MemberLoadDialog(1,4.0,initial=MemberLoad(1,MemberLoadType.PointLoad,10.0,1.0),onDismiss={},onConfirm={_,_,p,_->calls++;result=p}) } }
        field(0).performTextReplacement("-10")
        field(1).performTextReplacement("5")
        rule.onNodeWithText("Update").performClick()
        assertEquals(0,calls)
        rule.onNodeWithText(app.getString(R.string.frame_input_position,"4.0")).assertIsDisplayed()
        field(0).assertTextContains("-10")
        capture("member-position-en")
        field(1).performTextReplacement("4")
        rule.onNodeWithText("Update").performClick()
        rule.runOnIdle { assertEquals(1,calls);assertEquals(4.0,result!!,0.0) }
    }
    @Config(sdk=[34],qualifiers="en-rUS-w411dp-h900dp")
    @Test fun actualSlabDialogRejectsMalformedThicknessAndBlankCaseNoDefault() {
        var calls=0
        rule.setContent { MaterialTheme { SlabDialog(onDismiss={},onConfirm={_,_,_,_->calls++}) } }
        field(0).performTextReplacement("bad")
        field(2).performTextReplacement("")
        rule.onNodeWithText("Add").performClick()
        assertEquals(0,calls)
        rule.onNodeWithText(app.getString(R.string.frame_input_positive)).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.frame_input_required)).assertIsDisplayed()
        field(0).assertTextContains("bad")
        field(0).performTextReplacement("200")
        field(2).performTextReplacement("LL")
        rule.onNodeWithText("Add").performClick()
        rule.runOnIdle { assertEquals(1,calls) }
    }
}
