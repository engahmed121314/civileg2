package com.civileg.app.ui.compose.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.MutableLiveData
import com.civileg.app.R
import com.civileg.app.db.DesignRepository
import com.civileg.app.db.Project
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.domain.entities.FrozenDesignRecord
import com.civileg.app.domain.entities.LoadCombination
import com.civileg.app.testutils.saveNativeComposeScreenshot
import com.civileg.app.ui.DesignCodeStore
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import com.civileg.app.utils.LocaleHelper
import com.civileg.app.viewmodel.HordiSlabViewModel
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.core.engine.rc.ecp.*
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real Hordi screen, Save button, project/name dialog and source ViewModel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HordiSnapshotSaveUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val repository = mockk<DesignRepository>(relaxed = true)
    private val saved = mutableListOf<FrozenDesignRecord>()
    private lateinit var vm: HordiSlabViewModel
    private var projects = MutableLiveData(listOf(Project(id = 7L, name = "Project A"),
        Project(id = 9L, name = "Project B")))

    private fun calculate(moment: Double = 62.0) {
        val context = Ecp203RibbedSection.Context(25.0, 20.0,
            listOf(Ecp203BarLayout.Layer(2, 20.0)),
            Ecp203RectangularFlexure.SteelSurface.HIGH_YIELD_DEFORMED,
            Ecp203RibbedSection.Region.FLANGE_IN_COMPRESSION, 5000.0, 8.0,
            Ecp203WebShear.VerticalLinks(240.0,
                Ecp203RectangularFlexure.SteelSurface.SMOOTH_MILD, 8.0, 2, 200.0))
        vm.calculateHordiSlab(30.0, 400.0, 200.0, 600.0, 350.0, 60.0,
            5000.0, moment, 10.0, LoadCombination.DEAD_LIVE, "ECP", context)
    }

    private fun open(lang: String = "en") {
        val app = RuntimeEnvironment.getApplication()
        LocaleHelper.initApplicationContext(app)
        LocaleHelper.setLocale(app, lang)
        DesignCodeStore.set(app, DesignCode.ECP)
        coEvery { repository.saveHordiSlabSnapshot(any(), any(), capture(saved)) } returns 41L
        vm = HordiSlabViewModel(repository)
        val projectVm = mockk<ProjectViewModel> { every { allProjects } returns projects }
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides
                if (lang == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr) {
                CivilEngineerPremiumTheme(darkTheme = false) {
                    HordiSlabScreen(viewModel = vm, projectViewModel = projectVm)
                }
            }
        }
        // Initial form composition clears old results. Calculate after that effect.
        rule.runOnIdle { calculate() }
        rule.waitUntil(15000) { vm.result.value != null || vm.error.value != null }
        assertNull(vm.error.value)
        rule.onAllNodes(hasScrollAction())[0].performScrollToNode(hasTestTag("hordi-save-open"))
        button("hordi-save-open").assertIsEnabled().performClick()
        button("hordi-save-confirm").assertIsNotEnabled()
    }

    // AppButton puts its supplied modifier on a layout wrapper, not the clickable child.
    private fun button(tag: String) = rule.onNodeWithTag(tag).onChildren().filterToOne(hasClickAction())

    private fun selectProject() {
        button("hordi-save-project-picker").performClick()
        rule.onNodeWithTag("hordi-save-project-9").performClick()
    }

    private fun screenshot(name: String) {
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.runOnIdle { saveNativeComposeScreenshot(rule.activity, name, dialog = true) }
    }

    @Test fun actualConfirmSavesDialogOpenRecordAfterClearAndRecalculate() {
        open()
        val opened = requireNotNull(vm.currentDesignSnapshot())
        selectProject()
        rule.onNodeWithTag("hordi-save-name").performTextReplacement("Rib R1")
        rule.runOnIdle { vm.clearResult(); calculate(50.0) }
        rule.waitUntil(15000) { vm.currentDesignSnapshot() != null }
        val newer = requireNotNull(vm.currentDesignSnapshot())
        assertNotEquals(opened.revisionSha256, newer.revisionSha256)
        screenshot("hordi-save-dialog-en")
        button("hordi-save-confirm").assertIsEnabled().performClick()
        rule.waitUntil(10000) { saved.size == 1 }
        assertSame(opened.record, saved.single())
        coVerify(exactly = 1) { repository.saveHordiSlabSnapshot(9L, "Rib R1", opened.record) }
        rule.onNodeWithTag("hordi-save-confirm").assertDoesNotExist()
        rule.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.save_success)).assertIsDisplayed()
    }

    @Test fun arabicDialogRequiresExplicitProjectAndNonblankName() {
        open("ar")
        selectProject()
        rule.onNodeWithTag("hordi-save-name").performTextReplacement("  ")
        button("hordi-save-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("hordi-save-name").performTextReplacement("Rib R2")
        button("hordi-save-confirm").assertIsEnabled()
        screenshot("hordi-save-dialog-ar")
        button("hordi-save-cancel").performClick()
        assertTrue(saved.isEmpty())
    }

    @Test fun noProjectCannotFallBackToProjectOne() {
        projects = MutableLiveData(emptyList())
        open()
        button("hordi-save-confirm").assertIsNotEnabled()
        rule.onNodeWithTag("hordi-save-project-picker").assertDoesNotExist()
        screenshot("hordi-save-no-project")
        button("hordi-save-cancel").performClick()
        coVerify(exactly = 0) { repository.saveHordiSlabSnapshot(any(), any(), any()) }
    }

    @Test fun removedProjectDisablesConfirmationInsteadOfSavingAStaleDestination() {
        open()
        selectProject()
        button("hordi-save-confirm").assertIsEnabled()
        rule.runOnIdle { projects.value = listOf(Project(id = 7L, name = "Project A")) }
        button("hordi-save-confirm").assertIsNotEnabled()
        assertTrue(saved.isEmpty())
    }

    @Test fun successIsNotShownBeforeInsertAndRepeatedConfirmIsDisabled() {
        open()
        selectProject()
        val pending = CompletableDeferred<Long>()
        coEvery { repository.saveHordiSlabSnapshot(any(), any(), capture(saved)) } coAnswers { pending.await() }
        button("hordi-save-confirm").performClick()
        button("hordi-save-confirm").assertIsNotEnabled()
        rule.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.save_success)).assertDoesNotExist()
        rule.runOnIdle { pending.complete(41L) }
        rule.waitUntil(10000) { vm.isSaving.value == false }
        rule.onNodeWithTag("hordi-save-confirm").assertDoesNotExist()
        assertEquals(1, saved.size)
    }

    @Test fun failedInsertKeepsDialogAndShowsErrorWithoutFalseSuccess() {
        open()
        selectProject()
        coEvery { repository.saveHordiSlabSnapshot(any(), any(), any()) } throws
            IllegalStateException("synthetic storage failure")
        button("hordi-save-confirm").performClick()
        rule.waitUntil(10000) { vm.error.value != null }
        rule.onNodeWithTag("hordi-save-error").performScrollTo().assertIsDisplayed()
            .assertTextContains("synthetic storage failure", substring = true)
        button("hordi-save-confirm").assertIsEnabled()
        rule.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.save_success)).assertDoesNotExist()
        screenshot("hordi-save-storage-error")
    }
}
