package com.civileg.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodes
import com.civileg.app.db.Project
import com.civileg.app.testutils.saveNativeComposeScreenshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SaveToProjectDialogTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun p(id: Long, name: String) = Project(id = id, name = name)
    private val confirmed = mutableListOf<Long>()
    private var dismissed = 0
    private val projects = mutableStateOf(listOf(p(5, "Alpha"), p(9, "Beta")))

    private fun show() {
        rule.setContent {
            SaveToProjectDialog(
                title = "Save design", nameLabel = "Name", noProjectsText = "No projects",
                designName = "D1", onDesignNameChange = {}, projects = projects.value,
                onConfirm = { confirmed += it }, onDismiss = { dismissed++ }
            )
        }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle(); rule.mainClock.advanceTimeByFrame(); rule.waitForIdle()
        rule.runOnIdle { saveNativeComposeScreenshot(rule.activity, name, dialog = true) }
    }

    @Test fun noProjectDisablesSaveAndShowsMessage() {
        projects.value = emptyList()
        show()
        rule.onNodeWithText("No projects").assertExists()
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithText("Save").performClick()
        assertTrue(confirmed.isEmpty())
        shot("save-dialog-no-project-en")
    }

    @Test fun firstProjectIsVisiblyPreselectedAndSavesToIt() {
        show()
        val radios = rule.onAllNodes(isSelectable())
        radios[0].assertIsSelected()
        radios[1].assertIsNotSelected()
        rule.onNodeWithText("Save").assertIsEnabled().performClick()
        assertEquals(listOf(5L), confirmed)
        shot("save-dialog-preselected-en")
    }

    @Test fun changedSelectionSavesToChosenProjectNotFirstOrId1() {
        show()
        rule.onAllNodes(isSelectable())[1].performClick()
        rule.onAllNodes(isSelectable())[1].assertIsSelected()
        rule.onAllNodes(isSelectable())[0].assertIsNotSelected()
        rule.onNodeWithText("Save").performClick()
        assertEquals(listOf(9L), confirmed)
        shot("save-dialog-changed-en")
    }

    @Test fun staleSelectionFallsBackToVisibleExistingProject() {
        show()
        rule.onAllNodes(isSelectable())[1].performClick()
        projects.value = listOf(p(5, "Alpha"), p(12, "Gamma"))
        rule.waitForIdle()
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
        rule.onNodeWithText("Save").performClick()
        assertEquals(listOf(5L), confirmed)
        assertEquals(0, rule.onAllNodesWithText("Beta").fetchSemanticsNodes().size)
    }

    @Test fun cancelNeverSaves() {
        show()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(1, dismissed)
        assertTrue(confirmed.isEmpty())
    }

    @Test fun effectiveIdNeverInventsProject() {
        assertEquals(null, effectiveSaveProjectId(-1L, emptyList()))
        assertEquals(null, effectiveSaveProjectId(1L, emptyList()))
        assertEquals(9L, effectiveSaveProjectId(9L, projects.value))
        assertEquals(5L, effectiveSaveProjectId(77L, projects.value))
    }

    private fun arString(id: Int) = rule.activity.getString(id)

    @Test @Config(qualifiers = "ar")
    fun arabicPreselectedChangedAndStaleSelection() {
        show()
        val save = arString(com.civileg.app.R.string.save)
        val cancel = arString(com.civileg.app.R.string.cancel)
        assertTrue(save != "Save" && cancel != "Cancel")
        rule.onNodeWithText(arString(com.civileg.app.R.string.select_project)).assertExists()
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
        shot("save-dialog-preselected-ar")
        rule.onAllNodes(isSelectable())[1].performClick()
        rule.onAllNodes(isSelectable())[1].assertIsSelected()
        shot("save-dialog-changed-ar")
        projects.value = listOf(p(5, "Alpha"), p(12, "Gamma"))
        rule.waitForIdle()
        rule.onAllNodes(isSelectable())[0].assertIsSelected()
        rule.onAllNodes(isSelectable())[1].assertIsNotSelected()
        shot("save-dialog-stale-fallback-ar")
        rule.onNodeWithText(save).assertIsEnabled().performClick()
        assertEquals(listOf(5L), confirmed)
    }

    @Test @Config(qualifiers = "ar")
    fun arabicNoProjectDisablesSave() {
        projects.value = emptyList()
        show()
        rule.onNodeWithText(arString(com.civileg.app.R.string.save)).assertIsNotEnabled()
        shot("save-dialog-no-project-ar")
    }
}
