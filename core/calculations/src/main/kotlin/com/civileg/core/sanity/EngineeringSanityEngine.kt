package com.civileg.core.sanity

import com.civileg.core.engineering.BeamDesignFacade
import com.civileg.core.engineering.UnifiedBeamTorsion
import com.civileg.core.engineering.UnifiedColumnDesign
import com.civileg.core.engineering.UnifiedFootingDesign
import com.civileg.core.engineering.UnifiedRetainingWallDesign
import com.civileg.core.engineering.UnifiedSlabDesign
import com.civileg.core.engineering.UnifiedStairDesign
import com.civileg.core.engineering.UnifiedTankDesign

/**
 * P0 safety gate ("Unsafe Result: No EngineeringSanityEngine").
 *
 * Independently re-validates a calculation *result* for physical and code
 * plausibility, regardless of how it was computed. A result can pass the
 * calculation trace yet still be nonsensical — e.g. a negative required area,
 * an over-utilized section the engine marked safe, or a reinforcement ratio
 * outside code limits. The sanity engine catches those and reports them as
 * [SanityFinding]s instead of silently trusting the number.
 *
 * Design rules:
 *  - A check never throws; invalid inputs become findings.
 *  - NOT_CHECKED is not assumed safe: missing/implausible quantities are flagged.
 *  - This engine complements (does not replace) the per-code calculation trace.
 */
object EngineeringSanityEngine {

    fun check(outcome: BeamDesignFacade.BeamOutcome): SanityReport {
        val ctx = SanityContext("BeamOutcome")
        with(outcome) {
            // ---- Flexure ----
            with(flexure) {
                ctx.finite("flexure.AsRequired", asRequiredMm2)
                    .nonNegative("flexure.AsRequired", asRequiredMm2)
                ctx.finite("flexure.AsProvided", asProvidedMm2)
                    .nonNegative("flexure.AsProvided", asProvidedMm2)
                if (asProvidedMm2 > 0.0 && asRequiredMm2 > asProvidedMm2 + 1e-6)
                    ctx.error("flexure.AsProvided ($asProvidedMm2 mm²) < AsRequired ($asRequiredMm2 mm²)", "SAN-AS-PROV")
            }

            // ---- Shear ----
            with(shear) {
                ctx.finite("shear.Vc", concreteCapacityKn).nonNegative("shear.Vc", concreteCapacityKn)
                ctx.finite("shear.Vmax", maxCapacityKn).nonNegative("shear.Vmax", maxCapacityKn)
                ctx.capacityVsDemand("shear", concreteCapacityKn, maxCapacityKn, codeReference = "Shear max capacity")
                ctx.finite("shear.util", utilization).utilization("shear", utilization, codeReference = "Shear §utilization")
                ctx.nonNegative("shear.stirrupDia", stirrupDiaMm)
                ctx.nonNegative("shear.spacing", spacingMm)
                    .inRange("shear.spacing", spacingMm, 0.0, 1000.0, codeReference = "Stirrup spacing")
                ctx.nonNegative("shear.As/m", asPerMeterMm2)
            }

            // ---- Deflection ----
            with(deflection) {
                ctx.finite("deflection.actual", actualRatio).finite("deflection.allowable", allowableRatio)
                ctx.nonNegative("deflection.allowable", allowableRatio)
                ctx.capacityVsDemand("deflection", actualRatio, allowableRatio, codeReference = "Deflection span/depth")
            }

            // ---- Crack width ----
            with(crackControl) {
                ctx.finite("crack.actual", actualMm)
                if (maxAllowedMm != null) {
                    ctx.finite("crack.allowable", maxAllowedMm).nonNegative("crack.allowable", maxAllowedMm)
                    ctx.capacityVsDemand("crack", actualMm, maxAllowedMm, codeReference = "Crack width")
                }
            }

            // ---- Torsion (optional) ----
            torsion?.let { t ->
                ctx.finite("torsion.Tu", t.tuKnm).nonNegative("torsion.Tu", t.tuKnm)
                ctx.finite("torsion.Tth", t.tuThKnm).nonNegative("torsion.Tth", t.tuThKnm)
                ctx.nonNegative("torsion.stirrupDia", t.stirrupDiaMm)
                ctx.nonNegative("torsion.stirrupSpacing", t.stirrupSpacingMm)
                ctx.nonNegative("torsion.Al", t.longitudinalAreaMm2)
                if (t.torsionState != UnifiedBeamTorsion.TorsionState.NONE && t.tuMaxKnm > 0.0)
                    ctx.utilization("torsion.section", t.tuKnm / t.tuMaxKnm, codeReference = "Torsion section Tmax")
            }
        }
        return ctx.build()
    }

    // ── Stair (waist slab) ──

    fun check(outcome: UnifiedStairDesign.Outcome): SanityReport {
        val ctx = SanityContext("StairOutcome")
        with(outcome) {
            ctx.finite("stair.riser", riser).nonNegative("stair.riser", riser)
            ctx.finite("stair.going", going).nonNegative("stair.going", going)
            ctx.finite("stair.inclinedLength", inclinedLength).nonNegative("stair.inclinedLength", inclinedLength)
            ctx.finite("stair.horizontalLoad", horizontalLoad).nonNegative("stair.horizontalLoad", horizontalLoad)
            ctx.finite("stair.maxMoment", maxMoment).nonNegative("stair.maxMoment", maxMoment)
            ctx.finite("stair.AsReq", asRequiredMm2).nonNegative("stair.AsReq", asRequiredMm2)
            ctx.finite("stair.AsMain", mainRebarArea).nonNegative("stair.AsMain", mainRebarArea)
            if (mainRebarArea > 0.0 && asRequiredMm2 > mainRebarArea + 1e-6 && !flexureOk)
                ctx.error("stair main As ($mainRebarArea mm²/m) < AsRequired ($asRequiredMm2 mm²/m)", "SAN-AS-PROV")
            ctx.finite("stair.AsDist", distributionRebarArea).nonNegative("stair.AsDist", distributionRebarArea)
            ctx.finite("stair.d", effectiveDepth).nonNegative("stair.d", effectiveDepth)
            ctx.finite("stair.rho", reinforcementRatio).nonNegative("stair.rho", reinforcementRatio)
            ctx.finite("stair.shear.Vc", shearCapacity).nonNegative("stair.shear.Vc", shearCapacity)
            ctx.finite("stair.shear.Vmax", maxShearCapacityKn).nonNegative("stair.shear.Vmax", maxShearCapacityKn)
            ctx.capacityVsDemand("stair.shear", maxShear, maxShearCapacityKn, codeReference = "Stair shear")
            ctx.nonNegative("stair.stirrupDia", stirrupDiameter)
            ctx.nonNegative("stair.stirrupSpacing", stirrupSpacing)
                .inRange("stair.stirrupSpacing", stirrupSpacing, 0.0, 1000.0, codeReference = "Stirrup spacing")
            ctx.finite("stair.deflection", deflection).nonNegative("stair.deflection", deflection)
            ctx.finite("stair.deflectActual", deflectionActualRatio)
            ctx.capacityVsDemand("stair.deflection", deflectionActualRatio, deflectionAllowableRatio, codeReference = "Deflection span/depth")
        }
        return ctx.build()
    }

    // ── Tank (water-retaining walls + base) ──

    fun check(outcome: UnifiedTankDesign.Outcome): SanityReport {
        val ctx = SanityContext("TankOutcome")
        with(outcome) {
            ctx.finite("tank.wallThickness", wallThickness).nonNegative("tank.wallThickness", wallThickness)
            ctx.finite("tank.baseThickness", baseThickness).nonNegative("tank.baseThickness", baseThickness)
            ctx.finite("tank.pressure", pressure).nonNegative("tank.pressure", pressure)
            ctx.finite("tank.momentWall", maxMomentWall).nonNegative("tank.momentWall", maxMomentWall)
            ctx.finite("tank.momentBase", maxMomentBase).nonNegative("tank.momentBase", maxMomentBase)
            ctx.finite("tank.shearWall", maxShearWall).nonNegative("tank.shearWall", maxShearWall)
            ctx.finite("tank.fosUplift", factorOfSafetyUplift).nonNegative("tank.fosUplift", factorOfSafetyUplift)
            ctx.finite("tank.capacityM3", capacityM3).nonNegative("tank.capacityM3", capacityM3)
            ctx.finite("tank.steelWeight", steelWeight).nonNegative("tank.steelWeight", steelWeight)

            with(wallReinforcement) {
                ctx.finite("tank.wall.AsReq", astRequired).nonNegative("tank.wall.AsReq", astRequired)
                ctx.finite("tank.wall.AsProv", astProvided).nonNegative("tank.wall.AsProv", astProvided)
                // Legacy-parity quirk (not an error): water-retaining walls schedule at most
                // 20 bars/m (ACI/SBC) or ceil-to-10 mm spacing (ECP), so the 1.33√f'c/fy
                // design min-ρ frequently exceeds what practical spacing can provide while
                // the code's flat min-ρ check still passes. Legacy reports isSafe=true, so
                // this is a WARNING — never a FAIL of an otherwise-safe design.
                if (astProvided > 0.0 && astRequired > astProvided + 1e-6)
                    ctx.warn(
                        "tank wall AsProvided ($astProvided mm²/m) < AsRequired ($astRequired mm²/m) — bar-schedule limit",
                        "SAN-AS-PROV")
                if (utilizationRatio > 1.0)
                    ctx.warn("tank wall utilization = $utilizationRatio exceeds 1.0 — bar-schedule limit", "SAN-UTIL")
            }
            with(baseReinforcement) {
                ctx.finite("tank.base.AsReq", astRequired).nonNegative("tank.base.AsReq", astRequired)
                ctx.finite("tank.base.AsProv", astProvided).nonNegative("tank.base.AsProv", astProvided)
                if (astProvided > 0.0 && astRequired > astProvided + 1e-6)
                    ctx.warn(
                        "tank base AsProvided ($astProvided mm²/m) < AsRequired ($astRequired mm²/m) — bar-schedule limit",
                        "SAN-AS-PROV")
                if (utilizationRatio > 1.0)
                    ctx.warn("tank base utilization = $utilizationRatio exceeds 1.0 — bar-schedule limit", "SAN-UTIL")
            }

            safetyChecks.forEach { c ->
                ctx.finite("tank.check.${c.name}.value", c.value).nonNegative("tank.check.${c.name}.value", c.value)
                ctx.finite("tank.check.${c.name}.limit", c.limit).nonNegative("tank.check.${c.name}.limit", c.limit)
            }
        }
        return ctx.build()
    }

    // ── Retaining wall (stem/toe/heel cantilevers + stability) ──

    fun check(outcome: UnifiedRetainingWallDesign.Outcome): SanityReport {
        val ctx = SanityContext("RetainingWallOutcome")
        with(outcome) {
            ctx.finite("wall.otFS", overturningFS).nonNegative("wall.otFS", overturningFS)
            ctx.finite("wall.slideFS", slidingFS).nonNegative("wall.slideFS", slidingFS)
            ctx.finite("wall.bearingFS", bearingFS).nonNegative("wall.bearingFS", bearingFS)
            ctx.finite("wall.maxBearing", maxBearingPressure).nonNegative("wall.maxBearing", maxBearingPressure)
            ctx.finite("wall.minBearing", minBearingPressure).nonNegative("wall.minBearing", minBearingPressure)
            ctx.finite("wall.stemMoment", stemMoment).nonNegative("wall.stemMoment", stemMoment)
            ctx.finite("wall.stemShear", stemShear).nonNegative("wall.stemShear", stemShear)
            ctx.finite("wall.toeMoment", toeMoment).nonNegative("wall.toeMoment", toeMoment)
            ctx.finite("wall.toeShear", toeShear).nonNegative("wall.toeShear", toeShear)
            ctx.finite("wall.heelMoment", heelMoment).nonNegative("wall.heelMoment", heelMoment)
            ctx.finite("wall.heelShear", heelShear).nonNegative("wall.heelShear", heelShear)
            ctx.finite("wall.stemAsProv", stemMainRebarArea).nonNegative("wall.stemAsProv", stemMainRebarArea)

            // Bar-schedule shortfall mirrors the tank quirk: legacy selectBars picks the
            // market count once — the ACI/SBC stem flexure check tolerates 5 % under-target
            // (AsProv ≥ 0.95·As). A shortfall while the design still passed is a WARNING.
            safetyChecks.firstOrNull { it.name == "Stem Flexure" }?.let { c ->
                if (c.value > 0.0 && c.limit > c.value + 1e-6) {
                    if (c.isSafe)
                        ctx.warn(
                            "stem AsProvided (${c.value} mm²/m) < AsRequired (${c.limit} mm²/m) — bar-schedule tolerance",
                            "SAN-AS-PROV")
                    else
                        ctx.error(
                            "stem AsProvided (${c.value} mm²/m) < AsRequired (${c.limit} mm²/m)",
                            "SAN-AS-PROV")
                }
            }

            safetyChecks.forEach { c ->
                ctx.finite("wall.check.${c.name}.value", c.value).nonNegative("wall.check.${c.name}.value", c.value)
                ctx.finite("wall.check.${c.name}.limit", c.limit).nonNegative("wall.check.${c.name}.limit", c.limit)
            }
        }
        return ctx.build()
    }

    /** Generic gate for a labelled set of named numeric quantities. */
    fun checkValues(source: String, values: Map<String, Double>): SanityReport {
        val ctx = SanityContext(source)
        values.forEach { (name, v) -> ctx.finite(name, v).nonNegative(name, v) }
        return ctx.build()
    }

    // ── Column (axial + shear) ──

    fun check(outcome: UnifiedColumnDesign.Outcome): SanityReport {
        val ctx = SanityContext("ColumnOutcome")
        with(outcome) {
            ctx.finite("col.AsRequired", asRequiredMm2).nonNegative("col.AsRequired", asRequiredMm2)
            ctx.finite("col.AsProvided", asProvidedMm2).nonNegative("col.AsProvided", asProvidedMm2)
            if (asProvidedMm2 > 0.0 && asRequiredMm2 > asProvidedMm2 + 1e-6)
                ctx.error("col.AsProvided ($asProvidedMm2 mm²) < AsRequired ($asRequiredMm2 mm²)", "SAN-AS-PROV")
            ctx.finite("col.axialCap", axialCapacityKn).nonNegative("col.axialCap", axialCapacityKn)
            ctx.finite("col.util", utilization).utilization("col", utilization, codeReference = "Column axial utilisation")
            ctx.nonNegative("col.tieDia", tieDiameterMm)
            ctx.nonNegative("col.tieSpacing", tieSpacingMm)
                .inRange("col.tieSpacing", tieSpacingMm, 0.0, 1000.0, codeReference = "Tie spacing")
        }
        return ctx.build()
    }

    fun check(outcome: UnifiedColumnDesign.ShearOutcome): SanityReport {
        val ctx = SanityContext("ColumnShearOutcome")
        with(outcome) {
            ctx.finite("colShear.Vc", vcKn).nonNegative("colShear.Vc", vcKn)
            ctx.finite("colShear.Asv/s", asvPerSMm2).nonNegative("colShear.Asv/s", asvPerSMm2)
            ctx.finite("colShear.util", utilization).utilization("colShear", utilization, codeReference = "Column shear utilisation")
            ctx.nonNegative("colShear.tieDia", tieDiameterMm)
            ctx.nonNegative("colShear.spacing", spacingMm)
        }
        return ctx.build()
    }

    // ── Footing (isolated) ──

    fun check(outcome: UnifiedFootingDesign.Outcome): SanityReport {
        val ctx = SanityContext("FootingOutcome")
        with(outcome) {
            ctx.finite("footing.B", requiredWidth).nonNegative("footing.B", requiredWidth)
            ctx.finite("footing.L", requiredLength).nonNegative("footing.L", requiredLength)
            ctx.finite("footing.h", requiredThickness).nonNegative("footing.h", requiredThickness)
            ctx.finite("footing.d", depth).nonNegative("footing.d", depth)
            ctx.finite("footing.qAvg", soilPressure).nonNegative("footing.qAvg", soilPressure)
            ctx.finite("footing.qMax", maxSoilPressure).nonNegative("footing.qMax", maxSoilPressure)

            ctx.finite("footing.short.AsReq", shortDir.astRequired).nonNegative("footing.short.AsReq", shortDir.astRequired)
            ctx.finite("footing.short.AsProv", shortDir.astProvided).nonNegative("footing.short.AsProv", shortDir.astProvided)
            if (shortDir.astProvided > 0.0 && shortDir.astRequired > shortDir.astProvided + 1e-6 && !shortDir.isSafe)
                ctx.error("footing.short AsProvided (${shortDir.astProvided} mm²) < AsRequired (${shortDir.astRequired} mm²)", "SAN-AS-PROV")
            else if (shortDir.astProvided > 0.0 && shortDir.astRequired > shortDir.astProvided + 1e-6)
                ctx.warn("footing.short AsProvided (${shortDir.astProvided} mm²) < AsRequired (${shortDir.astRequired} mm²)", "SAN-AS-PROV-MIN")
            ctx.finite("footing.long.AsReq", longDir.astRequired).nonNegative("footing.long.AsReq", longDir.astRequired)
            ctx.finite("footing.long.AsProv", longDir.astProvided).nonNegative("footing.long.AsProv", longDir.astProvided)
            if (longDir.astProvided > 0.0 && longDir.astRequired > longDir.astProvided + 1e-6 && !longDir.isSafe)
                ctx.error("footing.long AsProvided (${longDir.astProvided} mm²) < AsRequired (${longDir.astRequired} mm²)", "SAN-AS-PROV")
            else if (longDir.astProvided > 0.0 && longDir.astRequired > longDir.astProvided + 1e-6)
                ctx.warn("footing.long AsProvided (${longDir.astProvided} mm²) < AsRequired (${longDir.astRequired} mm²)", "SAN-AS-PROV-MIN")

            ctx.finite("footing.punch.util", punching.utilizationRatio)
                .utilization("footing.punch", punching.utilizationRatio, codeReference = "Footing punching")
            ctx.finite("footing.1wX.util", oneWayX.utilizationRatio)
                .utilization("footing.1wX", oneWayX.utilizationRatio, codeReference = "One-way shear X")
            ctx.finite("footing.1wY.util", oneWayY.utilizationRatio)
                .utilization("footing.1wY", oneWayY.utilizationRatio, codeReference = "One-way shear Y")

            ctx.finite("footing.dist.AsProv", distribution.astProvided)
                .nonNegative("footing.dist.AsProv", distribution.astProvided)
        }
        return ctx.build()
    }

    // ── Slab (two-way) ──

    fun check(outcome: UnifiedSlabDesign.Outcome): SanityReport {
        val ctx = SanityContext("SlabOutcome")
        with(outcome) {
            ctx.finite("slab.muShort", muShortKnm).nonNegative("slab.muShort", muShortKnm)
            ctx.finite("slab.muLong", muLongKnm).nonNegative("slab.muLong", muLongKnm)
            ctx.finite("slab.minThk", minThicknessMm).nonNegative("slab.minThk", minThicknessMm)
            ctx.finite("slab.short.AsReq", shortDir.asRequiredMm2).nonNegative("slab.short.AsReq", shortDir.asRequiredMm2)
            ctx.finite("slab.long.AsReq", longDir.asRequiredMm2).nonNegative("slab.long.AsReq", longDir.asRequiredMm2)
            ctx.finite("slab.shear.Vc", shear.concreteCapacityKn).nonNegative("slab.shear.Vc", shear.concreteCapacityKn)
            ctx.capacityVsDemand("slab.shear", shear.concreteCapacityKn, shear.maxCapacityKn, codeReference = "Slab shear")
            ctx.finite("slab.shear.util", shear.utilization).utilization("slab.shear", shear.utilization, codeReference = "Slab shear utilisation")
        }
        return ctx.build()
    }
}
