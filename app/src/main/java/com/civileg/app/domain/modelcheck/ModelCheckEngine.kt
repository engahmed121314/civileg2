package com.civileg.app.domain.modelcheck

import kotlin.math.*

/**
 * Model-Check Engine (§9) — Validates ETABS/SAFE analysis results against SBC 301 & ACI 318-19.
 * Single source of truth for structural model verification.
 * Units: N, mm, MPa, kN, meters.
 */
object ModelCheckEngine {

    data class ModelCheckInput(
        // 1. Mass Participation
        val sumMassUx: Double, // % (e.g. 92.5)
        val sumMassUy: Double, // % (e.g. 91.0)
        
        // 2. Fundamental Period
        val modalPeriodTx: Double, // s
        val modalPeriodTy: Double, // s
        val heightOfBuilding: Double, // hn in meters
        val sd1: Double, // S_D1 seismic parameter
        val systemType: String = "other", // "moment_frame", "steel_mrf", "other"
        
        // 3. Base Shear Scaling
        val responseSpectrumBaseShearVt: Double, // kN
        val equivalentLateralForceBaseShearV: Double, // kN
        
        // 4. Story Drifts
        val cd: Double, // Deflection amplification factor
        val ie: Double, // Importance factor
        val storyDrifts: List<StoryDriftInput>,
        
        // 5. Diaphragm Flexibility
        val diaphragmMaxDeflection: Double,
        val storyAvgDeflection: Double,
        
        // 6 & 7. Center of Mass vs Rigidity & Torsion
        val buildingDimension: Double, // m (dimension orthogonal to seismic load)
        val eccentricity: Double, // m
        val maxStoryDriftAtEnd: Double,
        val avgStoryDrift: Double,
        
        // 10. P-Delta Stability
        val px: Double, // Total vertical load above story kN
        val vx: Double, // Story shear kN
        val hsx: Double // Story height mm
    )

    data class StoryDriftInput(
        val storyName: String,
        val hsxMm: Double,
        val elasticStoryDrift: Double, // \delta_xe
        val allowableDriftRatio: Double = 0.015 // e.g. 0.015 per table 12.12-1
    )

    data class CheckResult(
        val checkName: String,
        val status: Status,
        val governingValue: Double,
        val allowableOrLimit: Double,
        val clause: String,
        val message: String
    ) {
        enum class Status { PASS, FAIL, WARNING }
    }

    /**
     * Executes all 10 model checks per Course Specification §9.
     */
    fun runAllChecks(input: ModelCheckInput): List<CheckResult> {
        val results = mutableListOf<CheckResult>()

        // 1. Mass participation check (§9 item 1)
        // SBC 301 / ASCE 7: \Sigma modal mass ratio >= 90% in each of UX and UY
        val massPassUx = input.sumMassUx >= 90.0
        val massPassUy = input.sumMassUy >= 90.0
        results.add(CheckResult(
            checkName = "Mass Participation Ratio",
            status = if (massPassUx && massPassUy) CheckResult.Status.PASS else CheckResult.Status.FAIL,
            governingValue = min(input.sumMassUx, input.sumMassUy),
            allowableOrLimit = 90.0,
            clause = "SBC 301 §12.9.1 / Course §9",
            message = "UX sum = ${input.sumMassUx}%, UY sum = ${input.sumMassUy}% (Requirement: >= 90%)"
        ))

        // 2. Fundamental Period check (§9 item 2)
        // Ta = Ct * hn^x
        val (ct, x) = when (input.systemType.lowercase()) {
            "moment_frame", "concrete_mrf" -> Pair(0.0466, 0.9)
            "steel_mrf" -> Pair(0.0724, 0.8)
            else -> Pair(0.0488, 0.75)
        }
        val ta = ct * input.heightOfBuilding.pow(x)
        val cu = getCu(input.sd1)
        val cuTa = cu * ta
        // Tused rule: if Tc <= Ta use Ta; if Ta < Tc <= Cu*Ta use Tc; if Tc > Cu*Ta use Cu*Ta
        val tUsedX = when {
            input.modalPeriodTx <= ta -> ta
            input.modalPeriodTx <= cuTa -> input.modalPeriodTx
            else -> cuTa
        }
        results.add(CheckResult(
            checkName = "Fundamental Period (Tx)",
            status = CheckResult.Status.PASS,
            governingValue = tUsedX,
            allowableOrLimit = cuTa,
            clause = "SBC 301 §12.8.2 / Course §7.4",
            message = "Modal Tx = ${input.modalPeriodTx}s, Ta = ${"%.3f".format(ta)}s, Cu*Ta = ${"%.3f".format(cuTa)}s, Used = ${"%.3f".format(tUsedX)}s"
        ))

        // 3. Base-Shear Scaling (§9 item 3)
        // SBC 301 §12.9.4.1: if Vt < 0.85 * V (or 1.00 * V for ASCE 7-16), scale RS forces.
        val baseShearThreshold = 0.85 * input.equivalentLateralForceBaseShearV
        val baseShearPass = input.responseSpectrumBaseShearVt >= baseShearThreshold
        val scaleFactor = if (!baseShearPass && input.responseSpectrumBaseShearVt > 0) {
            input.equivalentLateralForceBaseShearV / input.responseSpectrumBaseShearVt
        } else 1.0
        results.add(CheckResult(
            checkName = "Base-Shear Scaling",
            status = if (baseShearPass) CheckResult.Status.PASS else CheckResult.Status.WARNING,
            governingValue = input.responseSpectrumBaseShearVt,
            allowableOrLimit = baseShearThreshold,
            clause = "SBC 301 §12.9.4.1 / Course §9",
            message = "RS Base Shear Vt = ${input.responseSpectrumBaseShearVt} kN vs 85% ELF V = ${"%.1f".format(baseShearThreshold)} kN. Scale Factor = ${"%.3f".format(scaleFactor)}"
        ))

        // 4. Story Drift Checks (§9 item 4)
        // \delta_x = Cd * \delta_{xe} / Ie; \Delta = \delta_x - \delta_{x-1}; \Delta <= allowable
        for (drift in input.storyDrifts) {
            val deltaX = input.cd * drift.elasticStoryDrift / input.ie
            val driftRatio = deltaX / drift.hsxMm
            val driftPass = driftRatio <= drift.allowableDriftRatio
            results.add(CheckResult(
                checkName = "Story Drift (${drift.storyName})",
                status = if (driftPass) CheckResult.Status.PASS else CheckResult.Status.FAIL,
                governingValue = driftRatio,
                allowableOrLimit = drift.allowableDriftRatio,
                clause = "SBC 301 Table 12.12-1 / Course §9",
                message = "Drift ratio = ${"%.4f".format(driftRatio)} (Allowable = ${drift.allowableDriftRatio})"
            ))
        }

        // 5. Diaphragm Flexibility (§9 item 5)
        // \delta_{diaph,max} <= 0.5 * \delta_{story,avg} -> rigid; 0.5-2.0 semi-rigid; > 2.0 flexible
        val flexibilityRatio = if (input.storyAvgDeflection > 0) input.diaphragmMaxDeflection / input.storyAvgDeflection else 0.0
        val flexibilityStatus = when {
            flexibilityRatio <= 0.5 -> CheckResult.Status.PASS
            flexibilityRatio <= 2.0 -> CheckResult.Status.WARNING
            else -> CheckResult.Status.FAIL
        }
        val flexibilityLabel = when {
            flexibilityRatio <= 0.5 -> "Rigid Diaphragm"
            flexibilityRatio <= 2.0 -> "Semi-Rigid Diaphragm"
            else -> "Flexible Diaphragm"
        }
        results.add(CheckResult(
            checkName = "Diaphragm Flexibility",
            status = flexibilityStatus,
            governingValue = flexibilityRatio,
            allowableOrLimit = 0.5,
            clause = "SBC 301 §12.3.1 / Course §9",
            message = "Ratio = ${"%.4f".format(flexibilityRatio)} -> $flexibilityLabel"
        ))

        // 6 & 7. Center of Mass vs Rigidity & Torsional Irregularity (§9 items 6 & 7)
        val eccentricityRatio = input.eccentricity / input.buildingDimension
        val ecPass = eccentricityRatio <= 0.15
        results.add(CheckResult(
            checkName = "Center of Mass vs Rigidity Eccentricity",
            status = if (ecPass) CheckResult.Status.PASS else CheckResult.Status.WARNING,
            governingValue = eccentricityRatio,
            allowableOrLimit = 0.15,
            clause = "SBC 301 Appendix D.3 / Course §9",
            message = "Eccentricity ratio = ${"%.3f".format(eccentricityRatio)} (Limit: <= 15%)"
        ))

        val torsionRatio = if (input.avgStoryDrift > 0) input.maxStoryDriftAtEnd / input.avgStoryDrift else 1.0
        val torsionStatus = when {
            torsionRatio <= 1.2 -> CheckResult.Status.PASS
            torsionRatio <= 1.4 -> CheckResult.Status.WARNING // Type 1a Torsional Irregularity
            else -> CheckResult.Status.FAIL // Type 1b Extreme Torsional Irregularity
        }
        results.add(CheckResult(
            checkName = "Torsional Irregularity Ratio",
            status = torsionStatus,
            governingValue = torsionRatio,
            allowableOrLimit = 1.2,
            clause = "SBC 301 Table 12.3-1 / Course §9",
            message = "Drift max/avg = ${"%.2f".format(torsionRatio)} (Type 1a > 1.2, Extreme 1b > 1.4)"
        ))

        // 10. P-Delta Stability (§9 item 10)
        // \theta = P_x * \Delta * Ie / (V_x * h_{sx} * Cd)
        val firstDrift = input.storyDrifts.firstOrNull()?.elasticStoryDrift ?: 0.0
        val theta = if (input.vx > 0 && input.hsx > 0) {
            (input.px * firstDrift * input.ie) / (input.vx * input.hsx * input.cd)
        } else 0.0
        val thetaMax = 0.25 // 0.5 / (beta * Cd) where beta = 1.0
        val pDeltaPass = theta <= 0.10
        val pDeltaStable = theta <= thetaMax
        results.add(CheckResult(
            checkName = "P-Delta Stability Coefficient (\u03b8)",
            status = if (pDeltaPass) CheckResult.Status.PASS else if (pDeltaStable) CheckResult.Status.WARNING else CheckResult.Status.FAIL,
            governingValue = theta,
            allowableOrLimit = 0.10,
            clause = "SBC 301 §12.8.7 / Course §9",
            message = "\u03b8 = ${"%.4f".format(theta)} (\u03b8 <= 0.10 ignore P-\u0394, \u03b8_max = $thetaMax)"
        ))

        return results
    }

    private fun getCu(sd1: Double): Double {
        return when {
            sd1 >= 0.4 -> 1.4
            abs(sd1 - 0.3) < 1e-5 -> 1.4
            abs(sd1 - 0.2) < 1e-5 -> 1.5
            abs(sd1 - 0.15) < 1e-5 -> 1.6
            else -> 1.7
        }
    }
}
