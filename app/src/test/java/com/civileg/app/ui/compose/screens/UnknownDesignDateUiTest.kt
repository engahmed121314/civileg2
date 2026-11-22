package com.civileg.app.ui.compose.screens

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.civileg.app.db.Design
import com.civileg.app.db.DesignType
import com.civileg.app.testutils.saveNativeComposeScreenshot
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Date

/** Migrated designs with no recorded date must show a localized "unknown", never 1970. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UnknownDesignDateUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun design(at: Long) = Design(id = 7, projectId = 1, type = DesignType.BEAM, name = "B1", inputData = "{}",
        results = "{}", isSafe = true, codeUsed = "ECP", createdAt = Date(at))

    private fun screenshot(name: String, dialog: Boolean = false) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle { saveNativeComposeScreenshot(rule.activity, name, dialog) }
    }

    /** The real Home recent-designs card, fed the same label the Home screen computes. */
    private fun homeCard(d: Design) {
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) {
                RecentProjectsSection(
                    projects = listOf(RecentProject(d.name, "Beam", homeDesignDateLabel(d), d.codeUsed, Icons.Default.Description)),
                    isDark = false
                )
            }
        }
    }

    @Test @Config(qualifiers = "en")
    fun homeCardEnglish() {
        homeCard(design(0L))
        rule.onNodeWithText("Date unknown").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("1970", substring = true).fetchSemanticsNodes().size)
        screenshot("unknown-date-home-en")
    }

    @Test @Config(qualifiers = "ar")
    fun homeCardArabic() {
        homeCard(design(0L))
        rule.onNodeWithText("التاريخ غير معروف").assertIsDisplayed()
        screenshot("unknown-date-home-ar")
    }

    @Test @Config(qualifiers = "en")
    fun homeCardKnownDateIsFormattedNot1970() {
        homeCard(design(1700000000000L))
        assertEquals(0, rule.onAllNodesWithText("Date unknown", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("1970", substring = true).fetchSemanticsNodes().size)
        rule.onNodeWithText(java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(Date(1700000000000L)), substring = true).assertIsDisplayed()
    }

    @Test @Config(qualifiers = "en")
    fun frameOpenDialogEnglish() {
        rule.setContent { CivilEngineerPremiumTheme(darkTheme = false) { FrameOpenDesignDialog(listOf(design(0L), design(1700000000000L).copy(id = 8, name = "B2")), emptyList(), {}, {}) } }
        rule.onNodeWithText("Date unknown", substring = true).assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("1970", substring = true).fetchSemanticsNodes().size)
        screenshot("unknown-date-dialog-en", dialog = true)
    }

    @Test @Config(qualifiers = "ar")
    fun frameOpenDialogArabic() {
        rule.setContent { CivilEngineerPremiumTheme(darkTheme = false) { FrameOpenDesignDialog(listOf(design(0L), design(1700000000000L).copy(id = 8, name = "B2")), emptyList(), {}, {}) } }
        rule.onNodeWithText("التاريخ غير معروف", substring = true).assertIsDisplayed()
        screenshot("unknown-date-dialog-ar", dialog = true)
    }
}
