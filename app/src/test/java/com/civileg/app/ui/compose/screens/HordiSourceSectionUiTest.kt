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
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        val view = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        File(dir, "$name.png").outputStream().use {
            view.drawToBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
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
        scroll(calculate)
        rule.onNodeWithText(calculate).assertIsEnabled().performClick()
        rule.waitUntil(15000) { vm.result.value != null || vm.error.value != null }
        assertNull(vm.error.value)
        assertEquals(611.1777815875193,vm.result.value!!.requiredReinforcement,1e-8)
        assertTrue(vm.result.value!!.safetyAssessmentBlocked)
        assertFalse(vm.result.value!!.isSafe)
        scroll(app.getString(R.string.ecp_ribbed_section_trace))
        screenshot("hordi-source-result-$lang")
        fill("Rib Width (mm)","220")
        rule.waitForIdle()
        assertNull("Editing section inputs must invalidate the old result",vm.result.value)
    }
    @Test fun sourceSectionEnglish() { verify("en") }
    @Test fun sourceSectionArabic() { verify("ar") }
}
