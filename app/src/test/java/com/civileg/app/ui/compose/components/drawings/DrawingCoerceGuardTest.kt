package com.civileg.app.ui.compose.components.drawings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.civileg.app.domain.RebarResult
import com.civileg.app.domain.entities.DesignCode
import com.civileg.app.ui.theme.CivilEngineerPremiumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Crash-guard for the drawing layer: coerceIn with a canvas-derived upper
 * bound throws IllegalArgumentException (empty range) when the zone becomes
 * smaller than the fixed readable minimum - this crashed the soil-structure
 * results view with "maximum -3.43 is less than minimum 20.0" on a perfectly
 * valid design whenever the drawing zone was measured too short (tiny
 * viewport, off-screen draw, split screen). Every fixed-min/variable-max
 * coerceIn in the drawing package now goes through coerceInMin, and the soil
 * zone draw functions early-return on non-positive zones.
 *
 * These tests render the constructible drawings through the real composables
 * and the real theme at a degenerate 24dp size and at boundary engineering
 * inputs. Any inverted-range throw fails the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawingCoerceGuardTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun setThemed(content: @Composable () -> Unit) {
        rule.setContent { CivilEngineerPremiumTheme(darkTheme = false, content = content) }
        rule.waitForIdle()
    }

    private fun capture(name: String) {
        val dir = File("build/reports/tests/compose-shots").apply { mkdirs() }
        val content = rule.activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        val bitmap = content.drawToBitmap()
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `coerceInMin clamps instead of throwing on inverted ranges`() {
        assertEquals(15f, 10f.coerceInMin(15f, 40f))
        assertEquals(40f, 99f.coerceInMin(15f, 40f))
        // Inverted range: prefer the readable minimum, never throw.
        assertEquals(20f, 10f.coerceInMin(20f, -3.43f))
        assertEquals(20f, 500f.coerceInMin(20f, -3.43f))
        // Fully negative degenerate zone still resolves without throwing.
        assertEquals(-4.5f, (-9f).coerceInMin(-4.5f, -10f))
        assertEquals(600.0, 300.0.coerceInMin(600.0, -1.0), 0.0)
    }

    @Test
    fun `soil drawing survives degenerate canvas in every view mode`() {
        setThemed {
            Column {
                for (mode in 0..4) {
                    Box(Modifier.size(24.dp)) {
                        ProfessionalSoilStructureDrawing(
                            foundationLengthMm = 3000.0, foundationWidthMm = 2000.0,
                            foundationThicknessMm = 500.0, columnLoadKN = 1200.0,
                            momentKNm = 50.0, soilBearingCapacityKpa = 150.0,
                            maxPressureKpa = 133.0, minPressureKpa = 61.0,
                            averagePressureKpa = 97.0, settlementMm = 12.0,
                            overturningFS = 4.4, slidingFS = 2.1, bearingFS = 1.13,
                            cover = 75.0, viewMode = mode, designCode = DesignCode.ECP
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `soil drawing survives boundary inputs with negative min pressure`() {
        // Moment far beyond the kern limit: uplift, minPressure negative.
        setThemed {
            Box(Modifier.size(24.dp)) {
                ProfessionalSoilStructureDrawing(
                    foundationLengthMm = 600.0, foundationWidthMm = 600.0,
                    foundationThicknessMm = 200.0, columnLoadKN = 100.0,
                    momentKNm = 500.0, soilBearingCapacityKpa = 100.0,
                    maxPressureKpa = 950.0, minPressureKpa = -394.0,
                    averagePressureKpa = 278.0, settlementMm = 45.0,
                    overturningFS = 0.9, slidingFS = 1.1, bearingFS = 0.3,
                    cover = 75.0, viewMode = 0, designCode = DesignCode.ECP,
                    waterTableDepthM = 0.0
                )
            }
        }
    }

    @Test
    fun `soil drawing renders healthy size for pixel audit`() {
        setThemed {
            Box(Modifier.size(360.dp)) {
                ProfessionalSoilStructureDrawing(
                    foundationLengthMm = 3000.0, foundationWidthMm = 2000.0,
                    foundationThicknessMm = 500.0, columnLoadKN = 1200.0,
                    momentKNm = 50.0, soilBearingCapacityKpa = 150.0,
                    maxPressureKpa = 133.0, minPressureKpa = 61.0,
                    averagePressureKpa = 97.0, settlementMm = 12.0,
                    overturningFS = 4.4, slidingFS = 2.1, bearingFS = 1.13,
                    cover = 75.0, viewMode = 0, designCode = DesignCode.ECP
                )
            }
        }
        capture("coerce-guard-soil-healthy")
    }

    @Test
    fun `slab and footing drawings survive degenerate canvas`() {
        setThemed {
            Column {
            Box(Modifier.size(24.dp)) {
                ProfessionalHordiSlabDrawing(
                    span = 6000.0, ribWidth = 150.0, ribSpacing = 650.0,
                    totalThickness = 300.0, toppingThickness = 60.0,
                    ribBottomDia = 14.0, ribBottomCount = 4,
                    stirrupDia = 8.0, stirrupSpacing = 200.0, cover = 25.0
                )
            }
            Box(Modifier.size(24.dp)) {
                ProfessionalWaffleSlabDrawing(
                    lx = 8.0, ly = 8.0, ribSpacing = 900.0, ribWidth = 150.0,
                    ribHeight = 350.0, toppingThickness = 80.0, solidHeadSize = 1800.0,
                    columnWidth = 600.0, ribBottomDia = 16.0, ribBottomCount = 3,
                    ribTopDia = 12.0, ribTopCount = 2, headBottomDia = 16.0,
                    headBottomCount = 6, cover = 25.0
                )
            }
            Box(Modifier.size(24.dp)) {
                ProfessionalCombinedFootingDrawing(
                    footingLength = 5000.0, footingWidth = 2500.0, footingThickness = 700.0,
                    col1X = 800.0, col1Width = 400.0, col1Depth = 400.0,
                    col2X = 3800.0, col2Width = 500.0, col2Depth = 500.0,
                    longBottomDia = 18.0, longBottomCount = 12,
                    longTopDia = 14.0, longTopCount = 8,
                    transBottomDia = 12.0, transBottomCount = 20,
                    cover = 75.0, soilPressureMax = 180.0, soilPressureMin = 60.0
                )
            }
            Box(Modifier.size(24.dp)) {
                ProfessionalStrapFootingDrawing(
                    footing1Length = 2000.0, footing1Width = 2000.0, footing1Thickness = 600.0,
                    footing2Length = 2500.0, footing2Width = 2500.0, footing2Thickness = 700.0,
                    strapWidth = 600.0, strapThickness = 900.0,
                    distanceBetweenColumns = 4000.0, col1Width = 400.0, col2Width = 500.0,
                    rebar1Dia = 16.0, rebar1Count = 10, rebar2Dia = 18.0, rebar2Count = 12,
                    strapDia = 20.0, strapCount = 6, cover = 75.0
                )
            }
            }
        }
    }

    @Test
    fun `pile steel flat slab and stair drawings survive degenerate canvas`() {
        setThemed {
            Column {
            Box(Modifier.size(24.dp)) {
                ProfessionalPileDrawing(
                    pileDiameter = 600.0, pileLength = 18.0, numberOfPiles = 4,
                    pileSpacing = 1800.0, pattern = "2x2", capWidth = 3000.0,
                    capLength = 3000.0, capThickness = 1000.0, columnWidth = 500.0,
                    columnLength = 500.0, longitBars = 12, longitDia = 18,
                    tiesDia = 10, tiesSpacing = 200, capRebarDia = 16,
                    capRebarCount = 10, soilType = "Dense Sand"
                )
            }
            Box(Modifier.size(24.dp)) {
                ProfessionalSteelDrawing(showBMD = true, showSFD = true)
            }
            val rebar = RebarResult(
                bars = 0, diameter = 16, spacing = 150,
                providedArea = 1340.0, requiredArea = 1200.0, ratio = 0.9
            )
            Box(Modifier.size(24.dp)) {
                ProfessionalFlatSlabDrawing(
                    lx = 6.0, ly = 6.0, slabThickness = 200.0,
                    columnWidth = 400.0, columnDepth = 400.0,
                    dropSizeX = 1800.0, dropSizeY = 1800.0, dropThickness = 100.0,
                    colTopRebar = rebar, colBotRebar = rebar,
                    midTopRebar = rebar, midBotRebar = rebar
                )
            }
            Box(Modifier.size(24.dp)) {
                ProfessionalStairDrawing(
                    stairWidth = 1200.0, totalHeight = 3000.0, totalLength = 4800.0,
                    riserHeight = 167.0, treadWidth = 300.0, slabThickness = 200.0,
                    mainRebarDia = 12.0, mainRebarSpacing = 150.0, cover = 25.0
                )
            }
            }
        }
    }
}
