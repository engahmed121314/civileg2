package com.civileg.app.ui.compose.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.drawToBitmap
import com.civileg.app.R
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.ui.DesignCodeStore
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.viewmodel.HordiSlabViewModel
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HordiSourceSectionUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun scroll(text: String) {
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText(text))
        rule.waitForIdle()
    }
    private fun fill(text: String, value: String) {
        scroll(text)
        rule.onNode(hasText(text) and hasSetTextAction()).performTextReplacement(value)
    }
    private fun screenshot(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle {
            val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
            val view = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
            File(dir, "$name.png").outputStream().use {
                view.drawToBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
            }
        }
    }

    private fun verify(lang: String) {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.initApplicationContext(app)
        LocaleHelper.setLocale(app,lang)
        DesignCodeStore.set(app,DesignCode.ECP)
        val vm = HordiSlabViewModel(mockk(relaxed=true))
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides
                if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                CivilEngineerPremiumTheme(darkTheme=false) { HordiSlabScreen(viewModel=vm) }
            }
        }
        rule.waitForIdle()
        fill("Rib Width (mm)","200")
        fill("Rib Spacing (mm)","600")
        fill("Total Thickness (mm)","350")
        fill("Topping Thk (mm)","60")
        fill("Span (mm)","5000")
        fill("Moment (kN.m/rib)","62")
        fill("Shear (kN/rib)","10")
        fill(app.getString(R.string.ecp_ribbed_main_diameter),"20")
        screenshot("hordi-source-bars-$lang")
        fill(app.getString(R.string.ecp_ribbed_contraflexure),"5000")
        screenshot("hordi-source-links-region-$lang")
        val calculate=app.getString(R.string.hordi_slab_design_button)
        scroll(app.getString(R.string.ecp_ribbed_immediate_udl))
        rule.onNodeWithTag("ecp-immediate-deflection-toggle").performClick()
        scroll(calculate)
        rule.onNodeWithText(calculate).assertIsNotEnabled()
        fill(app.getString(R.string.ecp_ribbed_service_dead), "6")
        fill(app.getString(R.string.ecp_ribbed_analysis_span), "4900")
        scroll(calculate)
        rule.onNodeWithText(calculate).assertIsNotEnabled()
        fill(app.getString(R.string.ecp_ribbed_analysis_span), "5000")
        fill(app.getString(R.string.ecp_ribbed_live_load), "8")
        scroll(app.getString(R.string.ecp_ribbed_analysis_span))
        screenshot("hordi-source-sls-inputs-$lang")
        scroll(calculate)
        rule.onNodeWithText("Load Combination").assertDoesNotExist()
        rule.onNodeWithText(calculate).assertIsEnabled().performClick()
        rule.waitUntil(15000) { vm.result.value != null || vm.error.value != null }
        assertNull(vm.error.value)
        assertEquals(611.1777815875193,vm.result.value!!.requiredReinforcement,1e-8)
        assertTrue(vm.result.value!!.safetyAssessmentBlocked)
        assertFalse(vm.result.value!!.isSafe)
        val frozen = requireNotNull(vm.currentDesignSnapshot())
        assertTrue(frozen.hasValidFingerprint())
        assertEquals(vm.result.value, frozen.result())
        assertEquals(6.0, frozen.input().sectionContext!!.immediateDeflectionInput!!.characteristicDeadLoadKnM2, 0.0)
        scroll(app.getString(R.string.ecp_ribbed_section_trace))
        screenshot("hordi-source-result-$lang")
        assertTrue(vm.result.value!!.codeNotes.any { it.contains("deltaLive=deltaD+L-deltaD=3.94892 mm") })
        val serviceStage = vm.result.value!!.calculationStages!!.first { it.key == "immediate_live" }
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasTestTag("ecp-stage-immediate_live"))
        rule.onNodeWithTag("ecp-stage-result-immediate_live").assertIsDisplayed()
            .assertTextEquals("deltaLive = 3.94892 mm")
        rule.onNodeWithText(serviceStage.equation).assertIsDisplayed()
        rule.onNodeWithText(serviceStage.substitution).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.ecp_stage_numbered_heading, 14,
            app.getString(R.string.ecp_stage_immediate_live))).assertIsDisplayed()
        screenshot("hordi-source-sls-result-$lang")
        scroll(app.getString(R.string.ecp_ribbed_long_term))
        rule.onNodeWithTag("ecp-long-term-toggle").performClick()
        fill(app.getString(R.string.ecp_ribbed_clear_span), "4800")
        fill(app.getString(R.string.ecp_ribbed_sustained_fraction), "0.7")
        fill(app.getString(R.string.ecp_ribbed_end_months), "60")
        scroll(app.getString(R.string.ecp_ribbed_attachment_sensitive))
        rule.onNodeWithTag("ecp-attachment-DEFLECTION_SENSITIVE").performClick()
        fill(app.getString(R.string.ecp_ribbed_install_months), "72")
        scroll(calculate)
        rule.onNodeWithText(calculate).assertIsNotEnabled()
        fill(app.getString(R.string.ecp_ribbed_install_months), "6")
        scroll(calculate)
        rule.onNodeWithText(calculate).assertIsNotEnabled()
        scroll(app.getString(R.string.ecp_ribbed_common_age))
        rule.onNodeWithTag("ecp-common-sustained-age").performClick()
        scroll(calculate)
        rule.onNodeWithText(calculate).assertIsEnabled() // Blank thermal values remain unknown, not invalid/zero.
        fill(app.getString(R.string.ecp_ribbed_thermal_total), "1.5")
        fill(app.getString(R.string.ecp_ribbed_thermal_after), "0.5")
        screenshot("hordi-source-long-term-inputs-$lang")
        scroll(calculate)
        rule.onNodeWithText(calculate).assertIsEnabled().performClick()
        rule.waitUntil(15000) { vm.result.value != null || vm.error.value != null }
        assertNull(vm.error.value)
        val longTermStage = vm.result.value!!.calculationStages!!.first { it.key == "post_installation" }
        assertEquals("deltaAfter = 4.39733 mm", longTermStage.result)
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasTestTag("ecp-stage-post_installation"))
        rule.onNodeWithTag("ecp-stage-result-post_installation").assertIsDisplayed()
            .assertTextEquals(longTermStage.result)
        rule.onNodeWithText(longTermStage.equation).assertIsDisplayed()
        rule.onNodeWithText(longTermStage.substitution).assertIsDisplayed()
        rule.onNodeWithText(app.getString(R.string.ecp_stage_numbered_heading, 21,
            app.getString(R.string.ecp_stage_post_installation))).assertIsDisplayed()
        screenshot("hordi-source-long-term-result-$lang")
        assertTrue(vm.result.value!!.safetyAssessmentBlocked)
        assertFalse(vm.result.value!!.isSafe)
        val completedLongTerm = vm.currentDesignSnapshot()!!
        assertEquals(0.7, completedLongTerm.input().sectionContext!!.immediateDeflectionInput!!.longTermInput!!.sustainedLiveFraction, 0.0)
        assertNotEquals(frozen.revisionSha256, completedLongTerm.revisionSha256)
        fill(app.getString(R.string.ecp_ribbed_service_dead), "7")
        rule.waitForIdle()
        assertNull("Editing service loads must invalidate the old result", vm.result.value)
        assertNull(vm.currentDesignSnapshot())
        assertEquals(6.0, frozen.input().sectionContext!!.immediateDeflectionInput!!.characteristicDeadLoadKnM2, 0.0)
        scroll(calculate)
        rule.onNodeWithText(calculate).assertIsEnabled().performClick()
        rule.waitUntil(15000) { vm.result.value != null || vm.error.value != null }
        assertNotNull(vm.result.value)
        assertNotEquals(frozen.revisionSha256, vm.currentDesignSnapshot()!!.revisionSha256)
        fill("Rib Width (mm)","220")
        rule.waitForIdle()
        assertNull("Editing section inputs must invalidate the old result",vm.result.value)
        assertNull(vm.currentDesignSnapshot())
    }
    @Test fun sourceSectionEnglish() { verify("en") }
    @Test fun sourceSectionArabic() { verify("ar") }
}
