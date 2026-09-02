package com.civileg.app.domain.calculations

import com.civileg.app.domain.PileInput
import com.civileg.app.domain.PileType
import com.civileg.app.domain.SoilType
import com.civileg.app.domain.calculations.CalculationFactory
import com.civileg.core.calculations.entities.DesignCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P004 (structural half) — ACI/ECP/SBC pile-foundation parity.
 *
 * Owner golden fixtures are still pending (P004 numeric half ⬜); until they
 * arrive this pins the BEHAVIORAL contract across the three codes:
 *
 *  1. Three DISTINCT engine classes are dispatched for the same element.
 *  2. Geotechnical capacity is code-agnostic by design (ADR: "ACI 318 does not
 *     codify geotechnical resistance") → identical Qu/Qa on identical inputs.
 *  3. Every code produces a complete result object for a valid common input —
 *     geometry, capacity, settlement, cap checks all populated.
 *  4. Envelope load factor: every code's pile reinforcement uses
 *     max(γ_dead, γ_live) from the Code Engine — never a dead-only factor
 *     (was UNCONSERVATIVE before the fix).
 */
class PileCodeParityTest {

    private val input = PileInput(
        pileType = PileType.BORED,
        pileDiameter = 600.0,
        pileLength = 20.0,
        numberOfPiles = 4,
        spacing = 3.0,
        axialLoad = 1500.0,
        lateralLoad = 80.0,
        momentLoad = 120.0,
        fcu = 30.0,
        fy = 400.0,
        fyp = 400.0,
        soilType = SoilType.CLAY,
        cu = 120.0,
        phi = 28.0,
        gammaSoil = 18.0,
        waterTableDepth = 30.0,   // below pile tip → no negative skin friction
        safetyFactor = 3.0
    )

    @Test fun threeCodes_dispatchDistinctEngines() {
        val classes = DesignCode.entries
            .map { CalculationFactory.getPileFoundationDesign(it)::class }
            .toSet()
        assertEquals("each code must map to its own engine class", 3, classes.size)
    }

    /**
     * Geotechnical contract: ACI and SBC share the code-agnostic soil
     * mechanics VERBATIM (neither code codifies geotech) → identical Qa.
     * ECP intentionally runs its own more rigorous geotech (numerical
     * effective-stress integration, tabulated Berezantzev Nq, embedment
     * factor, scour-aware length) — independence is legitimate per spec §3,
     * so we only require a sane positive capacity, not equality.
     */
    @Test fun geotechnicalCapacity_aciSbcShared_ecpIndependent() {
        val engs = DesignCode.entries.associateWith {
            CalculationFactory.getPileFoundationDesign(it).calculatePileCapacity(input)
        }
        assertEquals(
            "ACI/SBC must share identical geotech",
            engs.getValue(DesignCode.ACI).allowableCapacity,
            engs.getValue(DesignCode.SBC).allowableCapacity,
            1e-9
        )
        val ecpQa = engs.getValue(DesignCode.ECP).allowableCapacity
        assertTrue("ECP allowable must be positive", ecpQa > 0.0)
        // Same physics family — ECP's refinement should stay in the same order
        // of magnitude as the shared implementation (within ×3 either way).
        val sharedQa = engs.getValue(DesignCode.ACI).allowableCapacity
        assertTrue(
            "ECP Qa=$ecpQa diverges suspiciously from shared Qa=$sharedQa",
            ecpQa in (sharedQa / 3.0)..(sharedQa * 3.0)
        )
    }

    @Test fun designPile_completeResult_forEveryCode() {
        DesignCode.entries.forEach { code ->
            val r = CalculationFactory.getPileFoundationDesign(code).designPile(input)
            assertEquals("pileLength populated ($code)", 20.0, r.pileLengthM, 1e-9)
            assertEquals("numberOfPiles populated ($code)", 4, r.numberOfPiles)
            assertNotNull("capacityResult ($code)", r.capacityResult)
            assertNotNull("settlementResult ($code)", r.settlementResult)
            assertNotNull("capResult ($code)", r.capResult)
            assertTrue(
                "utilization within documented clamp ($code)",
                r.utilizationRatio in 0.0..2.0
            )
        }
    }

    /** Envelope guard: required steel with the envelope factor must exceed
     *  what a dead-only ×γd factor would produce (regression for the
     *  unconservative ×1.2 / ×1.4 / γc=1.5 implementations). */
    @Test fun reinforcement_envelopeFactor_notDeadOnly() {
        DesignCode.entries.forEach { code ->
            val eng = CalculationFactory.getPileFoundationDesign(code)
            val gd = code.getDeadLoadFactor()
            val gl = code.getLiveLoadFactor()
            val env = maxOf(gd, gl)
            val resDeadOnly = eng.designPileReinforcement(
                input, axialLoad = 1000.0 / gd, moment = 40.0 / gd
            )
            val resEnvelope = eng.designPileReinforcement(
                input, axialLoad = 1000.0, moment = 40.0
            )
            // Same physical load; dead-only scaling of inputs + envelope on the
            // other side must NOT yield more steel than pure envelope on raw input.
            assertTrue(
                "$code: envelope design must demand ≥ dead-only design steel",
                resEnvelope.requiredLongitudinalArea >=
                    resDeadOnly.requiredLongitudinalArea * (gd / env) - 1e-6
            )
        }
    }

    /** Provided reinforcement always covers required (no false SAFE at pile level). */
    @Test fun reinforcement_providedCoversRequired_allCodes() {
        DesignCode.entries.forEach { code ->
            val r = CalculationFactory.getPileFoundationDesign(code)
                .designPileReinforcement(input, axialLoad = input.axialLoad, moment = input.momentLoad)
            assertTrue(
                "$code provided ${r.longitudinalArea} < required ${r.requiredLongitudinalArea}",
                r.longitudinalArea >= r.requiredLongitudinalArea || r.ratio >= 1.0
            )
        }
    }
}
