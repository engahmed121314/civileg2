package com.civileg.app.ui.compose.screens

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.civileg.app.db.Design
import com.civileg.app.db.DesignType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/** Migrated designs with no recorded date must show a localized "unknown", never 1970. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnknownDesignDateUiTest {
    @get:Rule val rule = createComposeRule()

    private fun design(at: Long) = Design(id = 7, projectId = 1, type = DesignType.BEAM, name = "B1", inputData = "{}",
        results = "{}", isSafe = true, codeUsed = "ECP", createdAt = Date(at))

    @Test @Config(qualifiers = "en")
    fun homeLabelEnglish() {
        var label = ""
        rule.setContent { label = homeDesignDateLabel(design(0L)); Text(label) }
        rule.onNodeWithText("Date unknown").assertIsDisplayed()
        assertEquals("Date unknown", label)
    }

    @Test @Config(qualifiers = "ar")
    fun homeLabelArabic() {
        var label = ""
        rule.setContent { label = homeDesignDateLabel(design(0L)); Text(label) }
        rule.onNodeWithText("التاريخ غير معروف").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "en")
    fun homeLabelKnownDateIsFormattedNot1970() {
        var label = ""
        rule.setContent { label = homeDesignDateLabel(design(1700000000000L)); Text(label) }
        assertEquals(false, label.contains("1970"))
        assertEquals(false, label.contains("unknown"))
    }

    @Test @Config(qualifiers = "en")
    fun frameOpenDialogEnglish() {
        rule.setContent { FrameOpenDesignDialog(listOf(design(0L)), emptyList(), {}, {}) }
        rule.onNodeWithText("Date unknown", substring = true).assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("1970", substring = true).fetchSemanticsNodes().size)
    }

    @Test @Config(qualifiers = "ar")
    fun frameOpenDialogArabic() {
        rule.setContent { FrameOpenDesignDialog(listOf(design(0L)), emptyList(), {}, {}) }
        rule.onNodeWithText("التاريخ غير معروف", substring = true).assertIsDisplayed()
    }

}
