package com.civileg.core.calculations.entities

import com.civileg.core.engineering.CheckStatus
import com.civileg.core.engineering.CodeVersionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingModelTest {

    private fun sampleResult(safe: Boolean = true) = ReinforcementResult(
        astRequired = 1200.0,
        astProvided = 1570.0,
        barDiameter = 20.0,
        numberOfBars = 5,
        tiesDiameter = 8.0,
        tiesSpacing = 150.0,
        numLegs = 2,
        isSafe = safe,
        utilizationRatio = if (safe) 0.76 else 1.4,
        spacing = 150.0,
        description = "5Ø20"
    )

    private fun buildModel(
        result: ReinforcementResult = sampleResult(),
        appliedMomentKnM: Double = 0.0,
        appliedShearKn: Double = 0.0
    ) = DrawingModelBuilder.buildBeam(
        project = "Test Project",
        drawingNumber = "B-101",
        sheetNumber = "1/1",
        titleBlock = TitleBlock(
            project = "Test Project",
            drawingTitle = "Beam Section",
            drawingNumber = "B-101",
            date = "2026-08-27",
            scale = "1:25",
            designCode = "ECP 203-2020"
        ),
        code = DesignCode.ECP,
        edition = CodeVersionRegistry.defaultFor(DesignCode.ECP),
        overallHeight = 700.0,
        overallWidth = 300.0,
        effectiveDepth = 640.0,
        concreteCover = 40.0,
        beamLength = 5000.0,
        beamResult = result,
        appliedMomentKnM = appliedMomentKnM,
        appliedShearKn = appliedShearKn
    )

    @Test
    fun buildBeamProducesValidModel() {
        val model = buildModel()

        assertEquals(300.0, model.beamSection?.sectionBounds?.maxX ?: 0.0, 0.001)
        assertEquals(700.0, model.beamSection?.sectionBounds?.maxY ?: 0.0, 0.001)

        // Bounds cover the whole section plus any annotations/dims.
        assertTrue(model.bounds.maxX >= 300.0)
        assertTrue(model.bounds.maxY >= 700.0)

        // One bar per required bar, plus stirrups across the span.
        assertEquals(5, model.reinforcement.mainTensionBars.size)
        val expectedStirrups = kotlin.math.ceil(5000.0 / 150.0).toInt()
        assertEquals(expectedStirrups, model.reinforcement.stirrups.size)

        // Every tension bar has a mark and a code reference.
        model.reinforcement.mainTensionBars.forEach { bar ->
            assertTrue(bar.mark.startsWith("B-T"))
            assertTrue(bar.codeReference.isNotBlank())
        }
    }

    @Test
    fun barScheduleContainsAllBars() {
        val model = buildModel()
        val schedule = model.reinforcement.barSchedule

        assertTrue(schedule.startsWith("BAR SCHEDULE"))
        assertTrue(schedule.contains("B-T1"))
        assertTrue(schedule.contains("Ø20"))
        assertTrue(schedule.contains("Qty=1"))
    }

    @Test
    fun totalWeightIsPositiveAndDerivable() {
        val model = buildModel()

        assertTrue(model.reinforcement.totalWeightKg > 0.0)

        // Recompute independently: Σ (length_m × 0.006165·d² × qty).
        val expected = model.reinforcement.all.sumOf { bar ->
            val unitWeight = 0.006165 * bar.diameter * bar.diameter
            (bar.totalLengthMm / 1000.0) * unitWeight * bar.quantity
        }
        assertEquals(expected, model.reinforcement.totalWeightKg, 0.0001)
    }

    @Test
    fun validatePassesForSafeModel() {
        val model = buildModel()
        val flags = DrawingModelBuilder.validate(model)

        assertFalse(flags.hasInvalid)
        assertFalse(flags.sanityWarnings)
    }

    @Test
    fun validateFlagsUnsafeResultAsSaneWarningOnly() {
        val model = buildModel(result = sampleResult(safe = false))
        val flags = DrawingModelBuilder.validate(model)

        assertFalse(flags.hasInvalid)
        assertTrue(flags.sanityWarnings)
        assertEquals(CheckStatus.FAIL, model.state.overallStatus)
    }

    @Test
    fun validateDetectsNanInReinforcement() {
        val model = buildModel().let {
            it.copy(
                reinforcement = it.reinforcement.copy(
                    mainTensionBars = it.reinforcement.mainTensionBars.map { bar ->
                        if (bar.mark == "B-T1") bar.copy(totalLengthMm = Double.NaN) else bar
                    }
                )
            )
        }

        assertTrue(DrawingModelBuilder.validate(model).hasInvalid)
    }

    @Test
    fun validateDetectsInvertedBoundingBox() {
        val model = buildModel().copy(
            beamSection = buildModel().beamSection?.copy(
                sectionBounds = BoundingBox(300.0, 700.0, 0.0, 0.0)
            )
        )

        assertTrue(DrawingModelBuilder.validate(model).hasInvalid)
    }

    @Test
    fun stateTracksCodeEdition() {
        val edition = CodeVersionRegistry.defaultFor(DesignCode.ECP)
        val model = buildModel()

        assertEquals(DesignCode.ECP, model.state.code)
        assertEquals(edition.key, model.state.edition.key)
        assertEquals(CheckStatus.PASS, model.state.overallStatus)
    }

    @Test
    fun zonableStirrupZonesEmitDenseSupportsAndRelaxedMidSpan() {
        val zones = listOf(
            StirrupZone("Support Zone (Left)", 0.0, 1400.0, 100.0, 4, 10, "Ø10 @ 100 mm c/c · 4-Leg"),
            StirrupZone("Mid-Span Zone", 1400.0, 3600.0, 150.0, 4, 10, "Ø10 @ 150 mm c/c · 4-Leg"),
            StirrupZone("Support Zone (Right)", 3600.0, 5000.0, 100.0, 4, 10, "Ø10 @ 100 mm c/c · 4-Leg")
        )
        val model = buildModel(
            sampleResult().copy(tiesSpacing = 150.0, tiesDiameter = 10.0, zones = zones)
        )

        val stirrups = model.reinforcement.stirrups
        // floor(len/spacing)+1 per zone (first stirrup offset 50 mm from the left face).
        val nLeft = (1400.0 / 100.0).toInt() + 1
        val nMid = (2200.0 / 150.0).toInt() + 1
        val nRight = (1400.0 / 100.0).toInt() + 1
        assertEquals(nLeft + nMid + nRight, stirrups.size)

        // Sequential marks starting at B-S1; each bar carries its zone spacing verbatim.
        assertEquals("B-S1", stirrups.first().mark)
        assertEquals("B-S${stirrups.size}", stirrups.last().mark)
        assertTrue(stirrups.all { it.spacing != null && it.spacing!! > 0.0 })
        assertTrue(stirrups.all { it.diameter == 10.0 })
        assertTrue(stirrups.all { it.shape == "HOOK_135" })
        assertTrue(stirrups.all { it.hookLength == 120.0 })

        // Dense support-zone bars outnumber the relaxed mid-span bars.
        val at100 = stirrups.count { it.spacing == 100.0 }
        val at150 = stirrups.count { it.spacing == 150.0 }
        assertEquals(nLeft + nRight, at100)
        assertEquals(nMid, at150)
        assertTrue(at100 > at150)
    }

    @Test
    fun beamElevationCarriesZoneLayoutAndStirrupCaption() {
        val zones = listOf(
            StirrupZone("Support Zone (Left)", 0.0, 1400.0, 100.0, 4, 10, "Ø10 @ 100 mm c/c · 4-Leg"),
            StirrupZone("Mid-Span Zone", 1400.0, 3600.0, 150.0, 4, 10, "Ø10 @ 150 mm c/c · 4-Leg"),
            StirrupZone("Support Zone (Right)", 3600.0, 5000.0, 100.0, 4, 10, "Ø10 @ 100 mm c/c · 4-Leg")
        )
        val model = buildModel(
            sampleResult().copy(tiesSpacing = 150.0, tiesDiameter = 10.0, zones = zones),
            appliedMomentKnM = 120.0,
            appliedShearKn = 60.0
        )

        val elevation = model.beamElevation ?: error("elevation expected with appliedMoment > 0")
        assertEquals(zones, elevation.stirrupZones)
        assertEquals(10.0, elevation.stirrupDiameter, 1e-9)
        assertTrue(elevation.captionBottom.contains("Ø10 @ 100/150 c/c"))
        assertTrue(elevation.captionBottom.contains("4-LEG"))
    }

    @Test
    fun uniformFallbackWhenZonesAbsentKeepsLegacyCount() {
        val model = buildModel()
        val expected = kotlin.math.ceil(5000.0 / 150.0).toInt()
        assertEquals(expected, model.reinforcement.stirrups.size)
        assertTrue(model.reinforcement.stirrups.all { it.spacing == 150.0 })
    }
}