package com.civileg.app.ui.compose.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.civileg.app.domain.entities.*
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import com.civileg.app.viewmodel.DiagramType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Synthetic drawing regressions, not a reproduction of the owner's unrecorded crash inputs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FrameDrawingRegressionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val nodes = listOf(
        FrameNode(1, 0.0, 0.0, SupportType.Fixed),
        FrameNode(2, 0.0, 3.0),
        FrameNode(3, 5.0, 3.0),
        FrameNode(4, 5.0, 0.0, SupportType.Fixed)
    )
    private val members = listOf(
        FrameMember(1, 1, 2, concreteSection = ConcreteSectionProps(width = 300.0, depth = 500.0), memberType = FrameMemberType.Column),
        FrameMember(2, 2, 3, concreteSection = ConcreteSectionProps(width = 300.0, depth = 500.0)),
        FrameMember(3, 3, 4, concreteSection = ConcreteSectionProps(width = 300.0, depth = 500.0), memberType = FrameMemberType.Column)
    )

    private fun render(mode: Int, small: Boolean = false, empty: Boolean = false) {
        rule.setContent {
            CivilEngineerPremiumTheme(darkTheme = false) {
                Box(Modifier.size(if (small) 24.dp else 360.dp)) {
                    FrameDrawingCanvas(
                        nodes = if (empty) emptyList() else nodes,
                        members = if (empty) emptyList() else members,
                        memberLoads = emptyList(), nodalLoads = emptyList(), result = null,
                        diagramType = DiagramType.BMD, selectedMemberId = null, viewMode = mode
                    )
                }
            }
        }
        rule.waitForIdle()
        val image = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content).drawToBitmap()
        val dir = File("build/reports/tests/frame-regression").apply { mkdirs() }
        File(dir, "mode-$mode-small-$small-empty-$empty.png").outputStream().use {
            image.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun `frame view draws a portal`() = render(0)
    @Test fun `longitudinal view draws a portal`() = render(1)
    @Test fun `cross section draws a portal`() = render(2)
    @Test fun `plan view draws a portal`() = render(3)
    @Test fun `tiny frame view does not throw`() = render(0, small = true)
    @Test fun `tiny longitudinal view does not throw`() = render(1, small = true)
    @Test fun `tiny cross section does not throw`() = render(2, small = true)
    @Test fun `tiny plan view does not throw`() = render(3, small = true)
    @Test fun `empty model frame view does not throw`() = render(0, empty = true)
}
