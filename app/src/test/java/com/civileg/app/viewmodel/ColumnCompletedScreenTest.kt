package com.civileg.app.viewmodel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewTreeObserver
import androidx.test.platform.app.InstrumentationRegistry
import org.robolectric.shadows.ShadowDialog
import java.util.concurrent.atomic.AtomicLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.MutableLiveData
import com.civileg.app.db.DesignRepository
import com.civileg.app.db.Project
import com.civileg.app.domain.calculations.utils.CalculatorEngine
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.ui.compose.screens.ColumnScreen
import com.civileg.app.utils.SettingsManager
import io.mockk.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Actual screen/VM/engine, mocked storage/projects. Not engineering-method or Room/device proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColumnCompletedScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var host: View
    private lateinit var vm: ColumnViewModel
    private lateinit var repository: DesignRepository

    private fun open(configure: ColumnViewModel.() -> Unit = {}) {
        val settings = mockk<SettingsManager>(relaxed = true)
        every { settings.defaultDesignCode } returns DesignCode.ECP
        repository = mockk(relaxed = true)
        vm = ColumnViewModel(repository, CalculatorEngine(settings), settings)
        val projectVm = mockk<ProjectViewModel>()
        every { projectVm.allProjects } returns MutableLiveData<List<Project>>(emptyList())
        compose.setContent {
            host = LocalView.current
            MaterialTheme { ColumnScreen(viewModel = vm, projectViewModel = projectVm) }
        }
        compose.runOnIdle { configure(vm); vm.calculateManual() }
        compose.waitForIdle()
        assertNotNull("Actual default route must produce a captured output for this containment fixture", vm.uiState.value.completedDesign)
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("column-content").performScrollToNode(hasTestTag(tag))
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val bitmap = compose.runOnIdle {
            Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888).also { host.draw(Canvas(it)) }
        }
        val directory = File("build/reports/tests/column-completed-shots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun `editing candidate diameter removes stale checked drawing while leaving controls reachable`() {
        open()
        val completed = requireNotNull(vm.uiState.value.completedDesign)
        scrollTo("column-visible-drawing")
        compose.onNodeWithTag("column-visible-drawing").assertExists()
        capture("calculated-output-before-edit")
        scrollTo("column-candidate-diameter")
        compose.onNodeWithTag("column-candidate-diameter").performTextReplacement("25")
        compose.waitForIdle()
        assertNull(vm.uiState.value.completedDesign)
        assertFalse(vm.uiState.value.autoOptimize)
        assertFalse(vm.isCurrent(completed))
        compose.onNodeWithTag("column-candidate-diameter").assertExists()
        compose.onNodeWithTag("column-pdf-drawing").assertDoesNotExist()
        compose.onNodeWithTag("column-completed-revision").assertDoesNotExist()
        capture("candidate-edit-invalidates-output")
    }

    @Test fun `unsafe result cannot receive the reassuring under-capacity banner`() {
        open {
            updateInputs(width = "400", depth = "600", height = "3.0", fcu = "30", fy = "420",
                axialLoad = "1000", mx = "120", my = "80")
        }
        val result = requireNotNull(vm.uiState.value.result)
        assertFalse(result.isSafe)
        assertTrue("Fixture must distinguish isSafe from axial utilization alone", result.utilizationRatio < 1.0)
        val unsafe = host.context.getString(com.civileg.app.R.string.column_section_unsafe)
        compose.onNodeWithTag("column-content").performScrollToNode(hasText(unsafe))
        compose.onNodeWithText(unsafe).assertExists()
        capture("unsafe-underutilized-banner")
    }

    // AppButton's tag is on its layout wrapper. Assert/click the real Button.
    private fun button(tag: String) = compose.onNodeWithTag(tag)
        .onChildren().filterToOne(hasClickAction())

    private fun captureDialog(name: String) {
        val shown = requireNotNull(ShadowDialog.getLatestDialog())
        check(shown.isShowing)
        val root = requireNotNull(shown.window?.decorView)
        check(root.width > 0 && root.height > 0)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val directory = File("build/reports/tests/column-completed-shots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    private fun assertNoExistingProjectIsNotSaved() {
        open()
        scrollTo("column-save")
        val layouts = AtomicLong()
        val draws = AtomicLong()
        var dialogRoot: View? = null
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { layouts.incrementAndGet() }
        val drawListener = ViewTreeObserver.OnPreDrawListener { draws.incrementAndGet(); true }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun attachWhenAvailable() {
            if (dialogRoot != null) return
            val shown = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing } ?: return
            dialogRoot = shown.window?.decorView
            dialogRoot?.viewTreeObserver?.addOnGlobalLayoutListener(layoutListener)
            dialogRoot?.viewTreeObserver?.addOnPreDrawListener(drawListener)
        }
        try {
            // Include the click itself: it may also wait for idle and fail.
            button("column-save").performClick()
            instrumentation.runOnMainSync { attachWhenAvailable() }
            compose.waitForIdle()
            button("column-save-confirm").assertIsNotEnabled()
            compose.runOnIdle { captureDialog("no-project-save-disabled-${host.height}") }
            coVerify(exactly = 0) { repository.saveColumnDesignSnapshot(any(), any(), any()) }
        } catch (failure: Throwable) {
            // Diagnostics never turn a timeout/assertion into a pass or replace its cause.
            runCatching {
                instrumentation.runOnMainSync {
                    attachWhenAvailable()
                    val root = dialogRoot
                    println("COLUMN_DIALOG_UI_FAILURE type=${failure.javaClass.name} " +
                        "layoutsSinceAttach=${layouts.get()} drawsSinceAttach=${draws.get()} " +
                        "dialog=${root?.width}x${root?.height} layoutRequested=${root?.isLayoutRequested} " +
                        "host=${host.width}x${host.height} hostLayoutRequested=${host.isLayoutRequested}")
                    captureDialog("failed-save-dialog-${host.height}")
                }
            }.onFailure { println("COLUMN_DIALOG_DIAGNOSTIC_FAILED ${it.javaClass.name}") }
            throw failure
        } finally {
            runCatching {
                instrumentation.runOnMainSync {
                    dialogRoot?.viewTreeObserver?.let {
                        if (it.isAlive) {
                            it.removeOnGlobalLayoutListener(layoutListener)
                            it.removeOnPreDrawListener(drawListener)
                        }
                    }
                }
            }.onFailure { println("COLUMN_DIALOG_DIAGNOSTIC_CLEANUP_FAILED ${it.javaClass.name}") }
        }
    }

    @Test fun `no existing project disables save confirm without assigning project one`() {
        assertNoExistingProjectIsNotSaved()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w400dp-h800dp")
    fun `normal phone viewport also refuses a missing project`() {
        assertNoExistingProjectIsNotSaved()
    }
}
