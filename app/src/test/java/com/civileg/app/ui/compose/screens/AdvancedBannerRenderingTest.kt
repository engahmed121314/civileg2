package com.civileg.app.ui.compose.screens

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.civileg.app.domain.calculations.DisplayedCheckStatus
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdvancedBannerRenderingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun render(language: String) {
        val configuration = Configuration(rule.activity.resources.configuration).apply {
            setLocales(LocaleList(Locale(language)))
        }
        val localized = rule.activity.createConfigurationContext(configuration)
        rule.setContent {
            CompositionLocalProvider(LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalLayoutDirection provides if (language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                CivilEngineerPremiumTheme(darkTheme = false) {
                    Column(Modifier.width(320.dp).background(Color.White).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DisplayedCheckStatus.entries.forEach { CheckedResultBanner(it) }
                    }
                }
            }
        }
        rule.waitForIdle()
        val context = localized
        listOf(com.civileg.app.R.string.advanced_returned_checks_passed,
            com.civileg.app.R.string.advanced_returned_checks_failed,
            com.civileg.app.R.string.advanced_returned_checks_incomplete).forEach {
            rule.onNodeWithText(context.getString(it)).assertExists()
        }
        val image = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content).drawToBitmap()
        val directory = File("build/reports/tests/acceptance-banners").apply { mkdirs() }
        File(directory, "$language.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun `English actual banners render all statuses`() = render("en")
    @Test fun `Arabic actual banners render all statuses`() = render("ar")
}
