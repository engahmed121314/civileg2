package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * Comprehensive Beam Design Engine
 * Implements ALL calculation methods from the 9 reference PDFs:
 * PDF-04: BMD/SFD for all beam types with max-max envelopes
 * PDF-05: Load distribution from slabs, walls, self-weight
 * PDF-06: Bending behavior - stress/strain, neutral axis, cracked section
 * PDF-07: Design using First Principles (K-method for ECP, Rn-ρ for ACI)
 * PDF-08: Design using Charts (K-ω-z/d tables)
 * PDF-09: Shear design (concrete capacity + stirrups)
 * PDF-10: Torsion design
 * PDF-11: Empirical reinforcement method (minimum steel rules)
 * PDF-12: Moment of Resistance method (MR approach)
 */
object BeamDesignEngine {

    private const val GAMMA_C_ECP = 1.5   // ECP concrete safety factor
    private const val GAMMA_S_ECP = 1.15  // ECP steel safety factor
    private const val BETA_WHITNEY = 0.8  // Whitney stress block (ECP)
    private const val PHI_FLEXURE_ACI = 0.9
    private const val PHI_SHEAR_ACI = 0.75
    private const val PI = 3.141592653589793

    // ==================== MAIN ENTRY POINT ====================

    /**
     * Full beam design with step-by-step calculations
     */
    fun designBeam(
        b: Double, h: Double, span: Double,
        deadLoad: Double, liveLoad: Double,
        fcu: Double, fy: Double,
        preferredDia: Int,
        code: DesignCode,
        supportType: String,
        // Optional advanced inputs
        cover: Double = 50.0,
        slabThickness: Double = 0.0,
        slabType: String = "",
        tributaryWidth: Double = 0.0,
        wallThickness: Double = 0.0,
        wallHeight: Double = 0.0,
        floorFinishLoad: Double = 2.0,
        plasterLoad: Double = 0.5,
        torsionalMoment: Double = 0.0,
        // T-beam
        flangeWidth: Double = 0.0,
        flangeThickness: Double = 0.0,
    ): BeamDesignResult {
        val steps = mutableListOf<CalculationStep>()
        var stepNum = 0

        // Effective dimensions
        val d = h - cover - 8.0 - preferredDia / 2.0 // cover + stirrup + bar/2
        val clearSpan = span - 0.3 // assume 300mm column/support width

        // ===== SECTION 1: SELF-WEIGHT (PDF 05) =====
        stepNum++
        val selfWeight = b * h / 1e6 * 25.0 // kN/m (concrete density = 25 kN/m³)
        val ownWeightLoad = selfWeight
        val flooringLoad = if (tributaryWidth > 0) floorFinishLoad * tributaryWidth else 0.0
        val plasterLoadVal = if (tributaryWidth > 0) plasterLoad * tributaryWidth * 2 else 0.0 // both sides
        val wallLoadCalc = if (wallThickness > 0 && wallHeight > 0) {
            wallThickness / 1000.0 * wallHeight * (if (wallThickness <= 120) 12.0 else 18.0) // brick density
        } else 0.0
        val totalDead = deadLoad + ownWeightLoad + flooringLoad + plasterLoadVal + wallLoadCalc
        val totalLive = liveLoad

        steps.add(CalculationStep(
            stepNum, "Self-Weight & Load Distribution",
            codeReference = "PDF-05: Load Distribution",
            formula = "w_own = b × h × γ_c = $b × $h × 25 / 10⁶",
            formulaWithValues = "w_own = $b × $h × 25 / 10⁶ = ${String.format("%.2f", ownWeightLoad)} kN/m",
            result = "Total DL = ${String.format("%.2f", totalDead)} kN/m (incl. self-weight, flooring, plaster${if (wallLoadCalc > 0) ", wall" else ""})",
            unit = "kN/m"
        ))

        // ===== SECTION 2: ULTIMATE LOAD (Code factors) =====
        stepNum++
        val dlFactor = code.getDeadLoadFactor()
        val llFactor = code.getLiveLoadFactor()
        val wu = dlFactor * totalDead + llFactor * totalLive
        val ws = totalDead + totalLive // service load

        steps.add(CalculationStep(
            stepNum, "Ultimate Load Calculation",
            codeReference = when(code) { DesignCode.ECP -> "ECP 203 §4-2-2" else -> "ACI 318 §5.3" },
            formula = "wu = ${dlFactor}×DL + ${llFactor}×LL",
            formulaWithValues = "wu = ${dlFactor}×${String.format("%.2f", totalDead)} + ${llFactor}×${String.format("%.2f", totalLive)} = ${String.format("%.2f", wu)} kN/m",
            result = "wu = ${String.format("%.2f", wu)} kN/m",
            unit = "kN/m"
        ))

        // ===== SECTION 3: STRUCTURAL ANALYSIS (PDF 04 - BMD/SFD) =====
        stepNum++
        val analysis = analyzeBeam(span, wu, supportType)
        val mu = analysis.maxMoment
        val vu = analysis.maxShear

        steps.add(CalculationStep(
            stepNum, "Structural Analysis - Max Moments & Shears",
            codeReference = "PDF-04: Max-Max BMD & SFD",
            formula = when(supportType) {
                "SS", "SIMPLY_SUPPORTED" -> "Mu = wu×L²/8, Vu = wu×L/2"
                "FF", "FIXED_FIXED" -> "Mu(±) = wu×L²/12, Vu = wu×L/2"
                "FH", "FIXED_HINGED" -> "Mu(-) = wu×L²/8, Mu(+) = 9×wu×L²/128, Vu(L) = 5×wu×L/8"
                "CANTILEVER" -> "Mu = wu×L²/2, Vu = wu×L"
                else -> "Mu = wu×L²/8"
            },
            formulaWithValues = when(supportType) {
                "SS", "SIMPLY_SUPPORTED" -> "Mu = ${String.format("%.2f", wu)}×${String.format("%.2f", span)}²/8 = ${String.format("%.2f", mu)} kN.m\nVu = ${String.format("%.2f", wu)}×${String.format("%.2f", span)}/2 = ${String.format("%.2f", vu)} kN"
                "FF", "FIXED_FIXED" -> "Mu(±) = ${String.format("%.2f", wu)}×${String.format("%.2f", span)}²/12 = ${String.format("%.2f", abs(mu))} kN.m\nVu = ${String.format("%.2f", wu)}×${String.format("%.2f", span)}/2 = ${String.format("%.2f", vu)} kN"
                "FH", "FIXED_HINGED" -> "Mu(-) = ${String.format("%.2f", wu)}×${String.format("%.2f", span)}²/8 = ${String.format("%.2f", analysis.maxMomentNeg)} kN.m\nMu(+) = 9×${String.format("%.2f", wu)}×${String.format("%.2f", span)}²/128 = ${String.format("%.2f", analysis.maxMomentPos)} kN.m"
                "CANTILEVER" -> "Mu = ${String.format("%.2f", wu)}×${String.format("%.2f", span)}²/2 = ${String.format("%.2f", mu)} kN.m\nVu = ${String.format("%.2f", wu)}×${String.format("%.2f", span)} = ${String.format("%.2f", vu)} kN"
                else -> "Mu = ${String.format("%.2f", mu)} kN.m, Vu = ${String.format("%.2f", vu)} kN"
            },
            result = "Mu = ${String.format("%.2f", mu)} kN.m, Vu = ${String.format("%.2f", vu)} kN",
            unit = "kN.m / kN"
        ))

        // ===== SECTION 4: MATERIAL PROPERTIES (PDF 06) =====
        stepNum++
        val fcEff = when(code) {
            DesignCode.ECP -> fcu / GAMMA_C_ECP
            else -> fcu * 0.8 // cylinder strength for ACI/SBC
        }
        val fs = fy / when(code) {
            DesignCode.ECP -> GAMMA_S_ECP
            else -> 1.0 // ACI uses φ factor separately
        }
        val ec = when(code) {
            DesignCode.ECP -> 4400.0 * sqrt(fcu)
            else -> 4700.0 * sqrt(fcu * 0.8)
        }
        val es = 200000.0 // MPa
        val alphaE = es / ec

        steps.add(CalculationStep(
            stepNum, "Material Properties & Effective Stresses",
            codeReference = "PDF-06: Behavior of Beams Under Bending",
            formula = when(code) {
                DesignCode.ECP -> "fc = f'cu/γc = $fcu/$GAMMA_C_ECP, fs = fy/γs = $fy/$GAMMA_S_ECP, Ec = 4400√f'cu"
                else -> "f'c = 0.8×f'cu = ${String.format("%.1f", fcu*0.8)}, φ = 0.9, Ec = 4700√f'c"
            },
            formulaWithValues = when(code) {
                DesignCode.ECP -> "fc = $fcu/$GAMMA_C_ECP = ${String.format("%.2f", fcEff)} MPa\nfs = $fy/$GAMMA_S_ECP = ${String.format("%.2f", fs)} MPa\nEc = 4400×√$fcu = ${String.format("%.0f", ec)} MPa"
                else -> "f'c = ${String.format("%.1f", fcu*0.8)} MPa\nEc = 4700×√${String.format("%.1f", fcu*0.8)} = ${String.format("%.0f", ec)} MPa"
            },
            result = "d = h - cover - Østirrup/2 - Øbar/2 = $h - $cover - 4 - ${preferredDia/2} = ${String.format("%.0f", d)} mm",
            unit = "MPa / mm"
        ))

        // ===== SECTION 5: FLEXURE DESIGN - FIRST PRINCIPLES (PDF 07) =====
        stepNum++
        val flexureResult = designFlexure(b, d, mu, fcu, fy, code, preferredDia)
        steps.addAll(flexureResult.steps)
        stepNum = steps.size

        // ===== SECTION 6: MOMENT OF RESISTANCE CHECK (PDF 12) =====
        stepNum++
        val MR = flexureResult.momentOfResistance
        steps.add(CalculationStep(
            stepNum, "Moment of Resistance Verification (PDF-12)",
            codeReference = "PDF-12: Reinforcement Using Moment of Resistance",
            formula = when(code) {
                DesignCode.ECP -> "MR = As×fs×z = ${flexureResult.asProvided.toInt()}×${String.format("%.1f", fs)}×${String.format("%.0f", flexureResult.leverArm)} / 10⁶"
                else -> "Mn = As×fy×(d-a/2) → φMn = 0.9×Mn"
            },
            formulaWithValues = "MR = ${String.format("%.2f", MR)} kN.m ≥ Mu = ${String.format("%.2f", mu)} kN.m → ${if (MR >= mu) "OK" else "FAIL"}",
            result = "${if (MR >= mu) "PASS" else "FAIL"}: MR/Mu = ${String.format("%.2f", MR/mu)}",
            unit = "kN.m",
            isPass = MR >= mu,
            status = if (MR >= mu) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
        ))

        // ===== SECTION 7: SHEAR DESIGN (PDF 09) =====
        stepNum++
        val shearResult = BeamDesignEnginePart2.designShear(b, d, vu, fcu, fy, code, span)
        steps.addAll(shearResult.steps)
        stepNum = steps.size

        // ===== SECTION 8: TORSION DESIGN (PDF 10) =====
        stepNum++
        val torsionResult = if (torsionalMoment > 0) {
            BeamDesignEnginePart2.designTorsion(b, h, d, torsionalMoment, vu, fcu, fy, code, cover)
        } else {
            BeamDesignEnginePart2.TorsionDesignResult()
        }
        if (torsionalMoment > 0) {
            steps.addAll(torsionResult.steps)
            stepNum = steps.size
        }

        // ===== SECTION 9: DEFLECTION CHECK (Enhanced) =====
        stepNum++
        val deflResult = BeamDesignEnginePart2.checkDeflection(b, h, d, span, mu, fcu, fy, code, flexureResult.asProvided, flexureResult.neutralAxisDepth)
        steps.addAll(deflResult.steps)
        stepNum = steps.size

        // ===== SECTION 10: CRACK WIDTH CHECK =====
        stepNum++
        val crackResult = BeamDesignEnginePart2.checkCrackWidth(b, d, span, mu, fcu, fy, code, flexureResult.asProvided, flexureResult.neutralAxisDepth, es, ec)
        steps.add(crackResult.step)
        stepNum = steps.size

        // ===== SECTION 11: DEVELOPMENT LENGTH =====
        stepNum++
        val devResult = BeamDesignEnginePart2.checkDevelopmentLength(fcu, fy, preferredDia, code, span, d)
        steps.add(devResult.step)
        stepNum = steps.size

        // ===== BUILD RESULT =====
        val safetyChecks = mutableListOf<SafetyCheckItem>()
        safetyChecks.add(SafetyCheckItem("Flexure (MR ≥ Mu)", MR, mu, "kN.m", MR >= mu, when(code) { DesignCode.ECP -> "ECP 203 §4-2-1" else -> "ACI 318 §22.2" }))
        safetyChecks.add(SafetyCheckItem("Shear (qu ≤ qu,max)", shearResult.appliedStress, shearResult.maxStress, "MPa", shearResult.appliedStress <= shearResult.maxStress, when(code) { DesignCode.ECP -> "ECP 203 §4-2-6" else -> "ACI 318 §22.5" }))
        safetyChecks.add(SafetyCheckItem("Min Reinforcement", flexureResult.asProvided, flexureResult.asMin, "mm²", flexureResult.asProvided >= flexureResult.asMin, when(code) { DesignCode.ECP -> "ECP 203 §4-2-1-1" else -> "ACI 318 §9.6.1" }))
        safetyChecks.add(SafetyCheckItem("Max Reinforcement %", flexureResult.rhoActual * 100, flexureResult.rhoMax * 100, "%", flexureResult.rhoActual <= flexureResult.rhoMax, when(code) { DesignCode.ECP -> "ECP 203 §4-2-1-1" else -> "ACI 318 §9.2.2" }))
        safetyChecks.add(SafetyCheckItem("Deflection", deflResult.longTerm, deflResult.allowable, "mm", deflResult.longTerm <= deflResult.allowable, when(code) { DesignCode.ECP -> "ECP 203 §4-2-3" else -> "ACI 318 §24.2" }))
        if (crackResult.calculated > 0) {
            safetyChecks.add(SafetyCheckItem("Crack Width", crackResult.calculated, crackResult.allowable, "mm", crackResult.calculated <= crackResult.allowable, when(code) { DesignCode.ECP -> "ECP 203 §4-2-4" else -> "ACI 318 §24.5" }))
        }
        if (torsionalMoment > 0) {
            safetyChecks.add(SafetyCheckItem("Torsion + Shear", torsionResult.combinedStress, torsionResult.combinedCapacity, "MPa", torsionResult.combinedStress <= torsionResult.combinedCapacity, when(code) { DesignCode.ECP -> "ECP 203 §4-2-7" else -> "ACI 318 §22.7" }))
        }

        val allWarnings = mutableListOf<String>()
        allWarnings.addAll(flexureResult.warnings)
        allWarnings.addAll(shearResult.warnings)
        if (deflResult.longTerm > deflResult.allowable) allWarnings.add("Deflection exceeds allowable limit")
        if (crackResult.calculated > crackResult.allowable) allWarnings.add("Crack width exceeds ${crackResult.allowable} mm")

        val isSafe = safetyChecks.all { it.isSafe }
        val flexUtil = mu / max(MR, 0.001)
        val shearUtil = shearResult.appliedStress / max(shearResult.maxStress, 0.001)
        val deflUtil = deflResult.longTerm / max(deflResult.allowable, 0.001)
        val overallUtil = maxOf(flexUtil, shearUtil, deflUtil, 0.0).coerceIn(0.0, 1.5)

        // Volume & Weight
        val vol = b * h * span / 1e9
        val bottomWeight = flexureResult.barCount * (preferredDia.toDouble().pow(2) / 162.0) * span
        val topWeight = flexureResult.topBarCount * (flexureResult.topBarDia.toDouble().pow(2) / 162.0) * span
        val numStirrups = ceil(span * 1000.0 / shearResult.spacingSupport).toInt()
        val stirrupLen = 2.0 * ((b - 2*cover) + (h - 2*cover)) / 1000.0
        val stirrupWeight = numStirrups * stirrupLen * (shearResult.stirrupDia.toDouble().pow(2) / 162.0)
        val totalSteel = (bottomWeight + topWeight + stirrupWeight) * 1.1

        val codeNotesList = mutableListOf<String>()
        codeNotesList.addAll(flexureResult.codeNotes)
        codeNotesList.addAll(shearResult.codeNotes)
        if (code == DesignCode.ECP) codeNotesList.add("Design per ECP 203-2020")
        else if (code == DesignCode.ACI) codeNotesList.add("Design per ACI 318-19")
        else codeNotesList.add("Design per SBC 304-2018")

        return BeamDesignResult(
            beamWidth = b, beamDepth = h, span = span, clearSpan = clearSpan,
            effectiveDepth = d, fcu = fcu, fy = fy,
            deadLoad = deadLoad, liveLoad = liveLoad,
            designCode = code, supportType = supportType,
            sectionType = if (flangeWidth > 0) "T_SECTION" else "RECTANGULAR",
            flangeWidth = flangeWidth, flangeThickness = flangeThickness,
            selfWeight = selfWeight, totalDeadLoad = totalDead, totalLiveLoad = totalLive,
            loadDistributionNotes = buildLoadNotes(slabType, tributaryWidth, wallLoadCalc, ownWeightLoad, flooringLoad, plasterLoadVal),
            slabType = slabType, slabThickness = slabThickness, tributaryWidth = tributaryWidth,
            wallLoad = wallLoadCalc, ownWeightLoad = ownWeightLoad, flooringLoad = flooringLoad, plasterLoad = plasterLoadVal,
            deadLoadFactor = dlFactor, liveLoadFactor = llFactor, ultimateLoad = wu, serviceTotalLoad = ws,
            maxMoment = mu, maxShear = vu,
            maxMomentPos = analysis.maxMomentPos, maxMomentNeg = analysis.maxMomentNeg,
            maxShearLeft = analysis.shearLeft, maxShearRight = analysis.shearRight,
            reactionLeft = analysis.reactionLeft, reactionRight = analysis.reactionRight,
            shearAtCriticalSection = analysis.shearAtD, pointOfZeroShear = analysis.zeroShearLocation,
            momentAtMidspan = analysis.midspanMoment,
            K = flexureResult.K, Kbal = flexureResult.Kbal,
            omega = flexureResult.omega, leverArmZ = flexureResult.leverArm,
            neutralAxisDepth = flexureResult.neutralAxisDepth, concreteStressBlockDepth = flexureResult.stressBlockDepth,
            Rn = flexureResult.Rn, rho = flexureResult.rhoActual, rhoBalanced = flexureResult.rhoBalanced,
            beta1 = flexureResult.beta1,
            momentOfResistance = MR, chartK = flexureResult.K, chartZD = flexureResult.leverArm / d,
            asRequired = flexureResult.asRequired, asMin = flexureResult.asMin,
            asMax = flexureResult.asMax, asProvided = flexureResult.asProvided,
            rhoActual = flexureResult.rhoActual, rhoMin = flexureResult.rhoMin, rhoMax = flexureResult.rhoMax,
            bottomBars = "${flexureResult.barCount}Ø$preferredDia",
            bottomBarCount = flexureResult.barCount, bottomBarDia = preferredDia,
            topBars = "${flexureResult.topBarCount}Ø${flexureResult.topBarDia}",
            topBarCount = flexureResult.topBarCount, topBarDia = flexureResult.topBarDia,
            topAsProvided = flexureResult.topAsProvided,
            needsCompressionSteel = flexureResult.needsCompressionSteel,
            compressionBars = flexureResult.compressionBars,
            compressionBarCount = flexureResult.compressionBarCount,
            compressionBarDia = flexureResult.compressionBarDia,
            compressionAsProvided = flexureResult.compressionAsProvided,
            vc = shearResult.vc, vcFormula = shearResult.vcFormula,
            vMax = shearResult.maxStress, appliedShearStress = shearResult.appliedStress,
            vs = shearResult.vs, avRequired = shearResult.avRequired, avProvided = shearResult.avProvided,
            stirrupDia = shearResult.stirrupDia, stirrupLegs = shearResult.legs,
            stirrupSpacingSupport = shearResult.spacingSupport, stirrupSpacingMidspan = shearResult.spacingMidspan,
            condensationZoneLength = shearResult.condensationZone,
            avMin = shearResult.avMin, maxSpacing = shearResult.maxSpacing, minSpacing = shearResult.minSpacing,
            torsionalMoment = torsionalMoment, torsionalThreshold = torsionResult.threshold,
            needsTorsionDesign = torsionalMoment > torsionResult.threshold,
            torsionalReinforcement = torsionResult.reinforcementDesc,
            combinedShearStress = torsionResult.combinedStress,
            combinedCapacity = torsionResult.combinedCapacity,
            torsionIsSafe = torsionResult.isSafe,
            torsionalStirrupSpacing = torsionResult.stirrupSpacing,
            longitudinalTorsionAs = torsionResult.longitudinalAs,
            torsionalLongitudinalBars = torsionResult.longitudinalBarsDesc,
            grossMomentOfInertia = deflResult.Ig, crackedMomentOfInertia = deflResult.Icr,
            effectiveMomentOfInertia = deflResult.Ie, modulusOfElasticity = deflResult.Ec,
            crackingMoment = deflResult.Mcr, immediateDeflection = deflResult.immediate,
            longTermDeflection = deflResult.longTerm, allowableDeflection = deflResult.allowable,
            deflectionIsSafe = deflResult.longTerm <= deflResult.allowable,
            deflectionSpanRatio = deflResult.ratioDesc,
            basicSpanDepthRatio = deflResult.basicRatio, modifiedSpanDepthRatio = deflResult.modifiedRatio,
            crackWidthCalculated = crackResult.calculated, crackWidthAllowable = crackResult.allowable,
            crackIsSafe = crackResult.calculated <= crackResult.allowable,
            averageStrain = crackResult.avgStrain, surfaceStrain = crackResult.surfaceStrain,
            effectiveModularRatio = alphaE, neutralAxisRatio = if (d > 0) flexureResult.neutralAxisDepth / d else 0.0,
            developmentLengthRequired = devResult.required, developmentLengthProvided = devResult.provided,
            bondStress = devResult.fbd, lapLength = devResult.lapLength,
            developmentIsSafe = devResult.required <= devResult.provided,
            isSafe = isSafe, utilizationRatio = overallUtil,
            flexureUtilization = flexUtil, shearUtilization = shearUtil,
            concreteVolume = vol, steelWeight = totalSteel,
            safetyChecks = safetyChecks, warnings = allWarnings, codeNotes = codeNotesList,
            calculationSteps = steps
        )
    }

    // ==================== STRUCTURAL ANALYSIS (PDF 04) ====================

    data class AnalysisResult(
        val maxMoment: Double, val maxMomentPos: Double, val maxMomentNeg: Double,
        val maxShear: Double, val shearLeft: Double, val shearRight: Double,
        val reactionLeft: Double, val reactionRight: Double,
        val shearAtD: Double, val zeroShearLocation: Double,
        val midspanMoment: Double
    )

    fun analyzeBeam(span: Double, wu: Double, supportType: String): AnalysisResult {
        return when (supportType) {
            "SS", "SIMPLY_SUPPORTED" -> {
                val mu = wu * span * span / 8.0
                val vu = wu * span / 2.0
                val shearAtD = wu * (span / 2.0 - 0.0) // at face of support (simplified)
                AnalysisResult(
                    maxMoment = mu, maxMomentPos = mu, maxMomentNeg = 0.0,
                    maxShear = vu, shearLeft = vu, shearRight = -vu,
                    reactionLeft = vu, reactionRight = vu,
                    shearAtD = vu, zeroShearLocation = span / 2.0,
                    midspanMoment = mu
                )
            }
            "FF", "FIXED_FIXED" -> {
                val muSupport = wu * span * span / 12.0  // negative at supports
                val muMidspan = wu * span * span / 24.0   // positive at midspan
                val vu = wu * span / 2.0
                AnalysisResult(
                    maxMoment = muSupport, maxMomentPos = muMidspan, maxMomentNeg = -muSupport,
                    maxShear = vu, shearLeft = vu, shearRight = -vu,
                    reactionLeft = vu, reactionRight = vu,
                    shearAtD = vu, zeroShearLocation = span / 2.0,
                    midspanMoment = muMidspan
                )
            }
            "FH", "FIXED_HINGED" -> {
                val muNeg = wu * span * span / 8.0    // negative at fixed end
                val muPos = 9.0 * wu * span * span / 128.0 // positive max
                val vLeft = 5.0 * wu * span / 8.0  // reaction at fixed end
                val vRight = 3.0 * wu * span / 8.0 // reaction at hinged end
                AnalysisResult(
                    maxMoment = muNeg, maxMomentPos = muPos, maxMomentNeg = -muNeg,
                    maxShear = vLeft, shearLeft = vLeft, shearRight = -vRight,
                    reactionLeft = vLeft, reactionRight = vRight,
                    shearAtD = vLeft, zeroShearLocation = 5.0 * span / 8.0,
                    midspanMoment = muPos * 0.85 // approximate at midspan
                )
            }
            "CANTILEVER" -> {
                val mu = wu * span * span / 2.0
                val vu = wu * span
                AnalysisResult(
                    maxMoment = mu, maxMomentPos = 0.0, maxMomentNeg = -mu,
                    maxShear = vu, shearLeft = vu, shearRight = 0.0,
                    reactionLeft = vu, reactionRight = 0.0,
                    shearAtD = vu, zeroShearLocation = 0.0,
                    midspanMoment = mu * 0.25
                )
            }
            else -> {
                val mu = wu * span * span / 8.0
                val vu = wu * span / 2.0
                AnalysisResult(
                    maxMoment = mu, maxMomentPos = mu, maxMomentNeg = 0.0,
                    maxShear = vu, shearLeft = vu, shearRight = -vu,
                    reactionLeft = vu, reactionRight = vu,
                    shearAtD = vu, zeroShearLocation = span / 2.0,
                    midspanMoment = mu
                )
            }
        }
    }

    // ==================== FLEXURE DESIGN - FIRST PRINCIPLES (PDF 07) ====================

    data class FlexureResult(
        val K: Double, val Kbal: Double, val omega: Double, val leverArm: Double,
        val neutralAxisDepth: Double, val stressBlockDepth: Double,
        val Rn: Double, val rhoActual: Double, val rhoBalanced: Double, val beta1: Double,
        val asRequired: Double, val asMin: Double, val asMax: Double, val asProvided: Double,
        val rhoMin: Double, val rhoMax: Double,
        val barCount: Int, val topBarCount: Int, val topBarDia: Int, val topAsProvided: Double,
        val momentOfResistance: Double,
        val needsCompressionSteel: Boolean,
        val compressionBars: String, val compressionBarCount: Int, val compressionBarDia: Int, val compressionAsProvided: Double,
        val steps: List<CalculationStep>,
        val warnings: List<String>, val codeNotes: List<String>
    )

    fun designFlexure(b: Double, d: Double, mu: Double, fcu: Double, fy: Double, code: DesignCode, preferredDia: Int): FlexureResult {
        val steps = mutableListOf<CalculationStep>()
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // Determine Kbal based on steel grade and fcu
        val Kbal = calculateKbal(fcu, fy, code)
        val Rn_bal = calculateRnBalanced(fcu, fy, code)
        val beta1 = calculateBeta1(fcu, code)
        val rhoBal = calculateRhoBalanced(fcu, fy, code, beta1)

        // Min/Max reinforcement
        val asMinVal = when(code) {
            DesignCode.ECP -> max(0.26 * sqrt(fcu) / fy, 0.0013) * b * d
            else -> max(0.25 * sqrt(fcu * 0.8) / fy, 1.4 / fy) * b * d
        }
        val asMaxVal = 0.04 * b * d // 4% max (ECP), ACI similar
        val rhoMin = asMinVal / (b * d)
        val rhoMax = asMaxVal / (b * d)

        var asRequired: Double = 0.0
        var leverArm: Double = 0.0
        var neutralAxisDepth: Double = 0.0
        var stressBlockDepth: Double = 0.0
        var omega: Double = 0.0
        var K: Double = 0.0
        var Rn: Double = 0.0
        var needsCompSteel: Boolean = false
        var compBars = ""
        var compBarCount = 0
        var compBarDia = 0
        var compAsProvided = 0.0

        when (code) {
            DesignCode.ECP -> {
                // ECP 203 - K-method (First Principles - PDF 07)
                K = (mu * 1e6) / ((fcu / GAMMA_C_ECP) * b * d * d)

                steps.add(CalculationStep(
                    steps.size + 1, "Flexure Design - K-Method (First Principles)",
                    codeReference = "ECP 203 §4-2-1-1 / PDF-07",
                    formula = "K = Mu / (f'cu/γc × b × d²)",
                    formulaWithValues = "K = ${String.format("%.2f", mu)}×10⁶ / (${String.format("%.2f", fcu/GAMMA_C_ECP)} × $b × ${String.format("%.0f", d)}²) = ${String.format("%.4f", K)}",
                    result = "K = ${String.format("%.4f", K)} ${if (K <= Kbal) "< Kbal = ${String.format("%.4f", Kbal)} → Singly Reinforced" else "> Kbal = ${String.format("%.4f", Kbal)} → Doubly Reinforced"}",
                    unit = "-"
                ))

                if (K <= Kbal) {
                    // Singly reinforced
                    needsCompSteel = false
                    omega = 1.0 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * K)))
                    leverArm = d * (0.5 + sqrt(max(0.0, 0.25 - K / 0.9)))
                    
                    // From K: a/d ratio
                    val adRatio = 1.0 - sqrt(max(0.0, 1.0 - 2.0 * K))
                    stressBlockDepth = adRatio * d // a = β*x, with β=0.8, and a/d = 1-√(1-2K)
                    neutralAxisDepth = stressBlockDepth / BETA_WHITNEY // x = a/β
                    
                    val fs = fy / GAMMA_S_ECP
                    asRequired = (mu * 1e6) / (fs * leverArm)
                    Rn = 0.0

                    steps.add(CalculationStep(
                        steps.size + 1, "Lever Arm & Reinforcement",
                        formula = "z = d×(0.5 + √(0.25 - K/0.9))\nAs = Mu / (fs × z)",
                        formulaWithValues = "z = ${String.format("%.0f", d)}×(0.5 + √(0.25 - ${String.format("%.4f", K)}/0.9)) = ${String.format("%.1f", leverArm)} mm\nAs = ${String.format("%.2f", mu)}×10⁶ / (${String.format("%.1f", fs)} × ${String.format("%.1f", leverArm)}) = ${String.format("%.1f", asRequired)} mm²",
                        result = "As req = ${String.format("%.1f", asRequired)} mm²",
                        unit = "mm²"
                    ))
                } else {
                    // Doubly reinforced (PDF 07 - compression steel needed)
                    needsCompSteel = true
                    val Mu_bal = Kbal * (fcu / GAMMA_C_ECP) * b * d * d / 1e6
                    val excessMu = mu - Mu_bal
                    val fs = fy / GAMMA_S_ECP
                    val dPrime = 50.0 // assume 50mm to compression steel
                    leverArm = d * (0.5 + sqrt(max(0.0, 0.25 - Kbal / 0.9)))
                    omega = 1.0 * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * Kbal)))
                    stressBlockDepth = (1.0 - sqrt(max(0.0, 1.0 - 2.0 * Kbal))) * d
                    neutralAxisDepth = stressBlockDepth / BETA_WHITNEY
                    // As tension = As1 (balanced) + As2 (excess)
                    val as1 = (Mu_bal * 1e6) / (fs * leverArm)
                    val as2 = (excessMu * 1e6) / (fs * (d - dPrime))
                    asRequired = as1 + as2
                    val asComp = as2 // compression steel area

                    compBarDia = max(12, preferredDia - 4)
                    compBarCount = ceil(asComp / (PI * compBarDia.toDouble().pow(2) / 4)).toInt().coerceAtLeast(2)
                    compAsProvided = compBarCount * PI * compBarDia.toDouble().pow(2) / 4
                    compBars = "${compBarCount}Ø$compBarDia"

                    warnings.add("Doubly reinforced section - compression steel required")
                    codeNotes.add("ECP 203 §4-2-1-1(d): Compression steel for K > Kbal")

                    steps.add(CalculationStep(
                        steps.size + 1, "Doubly Reinforced Design (Compression Steel)",
                        codeReference = "ECP 203 §4-2-1-1(d) / PDF-07",
                        formula = "Mu_bal = Kbal × (f'cu/γc) × b × d²\nAs1 = Mu_bal / (fs × z)\nAs2 = (Mu - Mu_bal) / (fs × (d-d'))\nAs = As1 + As2",
                        formulaWithValues = "Mu_bal = ${String.format("%.4f", Kbal)} × ${String.format("%.2f", fcu/GAMMA_C_ECP)} × $b × ${String.format("%.0f", d)}² / 10⁶ = ${String.format("%.2f", Mu_bal)} kN.m\nAs1 = ${String.format("%.2f", Mu_bal)}×10⁶ / (${String.format("%.1f", fs)} × ${String.format("%.1f", leverArm)}) = ${String.format("%.1f", as1)} mm²\nAs2 = ${String.format("%.2f", excessMu)}×10⁶ / (${String.format("%.1f", fs)} × ${String.format("%.0f", d - dPrime)}) = ${String.format("%.1f", as2)} mm²\nAs = ${String.format("%.1f", as1)} + ${String.format("%.1f", as2)} = ${String.format("%.1f", asRequired)} mm²",
                        result = "As req = ${String.format("%.1f", asRequired)} mm², As' = ${String.format("%.1f", asComp)} mm² → $compBars",
                        unit = "mm²"
                    ))
                    Rn = 0.0
                }
            }
            else -> {
                // ACI 318 / SBC 304 - Rn-ρ method
                val fcPrime = fcu * 0.8
                Rn = (mu * 1e6) / (PHI_FLEXURE_ACI * b * d * d)
                K = 0.0

                steps.add(CalculationStep(
                    steps.size + 1, "Flexure Design - Rn-ρ Method",
                    codeReference = when(code) { DesignCode.SBC -> "SBC 304 / PDF-07" else -> "ACI 318 §22.2 / PDF-07" },
                    formula = "Rn = Mu / (φ × b × d²)\nρ = (0.85×f'c / fy) × (1 - √(1 - 2×Rn / (0.85×f'c)))",
                    formulaWithValues = "Rn = ${String.format("%.2f", mu)}×10⁶ / (${PHI_FLEXURE_ACI} × $b × ${String.format("%.0f", d)}²) = ${String.format("%.4f", Rn)} MPa",
                    result = "Rn = ${String.format("%.4f", Rn)} ${if (Rn <= Rn_bal) "< Rn_bal = ${String.format("%.4f", Rn_bal)}" else "> Rn_bal"}",
                    unit = "MPa"
                ))

                if (Rn <= Rn_bal) {
                    needsCompSteel = false
                    val rhoCalc = (0.85 * fcPrime / fy) * (1.0 - sqrt(max(0.0, 1.0 - 2.0 * Rn / (0.85 * fcPrime))))
                    asRequired = rhoCalc * b * d
                    val a = asRequired * fy / (0.85 * fcPrime * b)
                    stressBlockDepth = a
                    neutralAxisDepth = a / beta1
                    leverArm = d - a / 2.0
                    omega = rhoCalc * fy / (0.85 * fcPrime)

                    steps.add(CalculationStep(
                        steps.size + 1, "Reinforcement Ratio & Area",
                        formula = "ρ = (0.85×f'c/fy)×(1 - √(1 - 2Rn/(0.85f'c)))\nAs = ρ × b × d\na = As×fy / (0.85×f'c×b)",
                        formulaWithValues = "ρ = ${String.format("%.6f", rhoCalc)}\nAs = ${String.format("%.6f", rhoCalc)} × $b × ${String.format("%.0f", d)} = ${String.format("%.1f", asRequired)} mm²\na = ${String.format("%.1f", asRequired)} × $fy / (0.85 × ${String.format("%.1f", fcPrime)} × $b) = ${String.format("%.1f", a)} mm",
                        result = "As req = ${String.format("%.1f", asRequired)} mm², a = ${String.format("%.1f", a)} mm, z = d-a/2 = ${String.format("%.1f", leverArm)} mm",
                        unit = "mm²"
                    ))
                } else {
                    needsCompSteel = true
                    val Mn_bal = Rn_bal * PHI_FLEXURE_ACI * b * d * d / 1e6
                    val excessMn = mu - Mn_bal
                    val dPrime = 50.0
                    leverArm = d - beta1 * (Rn_bal * PHI_FLEXURE_ACI * b * d * d) / (0.85 * fcPrime * b) / 2
                    stressBlockDepth = Rn_bal * PHI_FLEXURE_ACI * b * d * d / (0.85 * fcPrime * b)
                    neutralAxisDepth = stressBlockDepth / beta1
                    omega = rhoBal * fy / (0.85 * fcPrime)
                    val as1 = rhoBal * b * d
                    val as2 = (excessMn * 1e6) / (PHI_FLEXURE_ACI * fy * (d - dPrime))
                    asRequired = as1 + as2
                    val asComp = as2

                    compBarDia = max(12, preferredDia - 4)
                    compBarCount = ceil(asComp / (PI * compBarDia.toDouble().pow(2) / 4)).toInt().coerceAtLeast(2)
                    compAsProvided = compBarCount * PI * compBarDia.toDouble().pow(2) / 4
                    compBars = "${compBarCount}Ø$compBarDia"
                    warnings.add("Doubly reinforced - compression steel needed (ACI)")
                    Rn = Rn_bal
                }
            }
        }

        // Apply minimum steel
        val asFinal = max(asRequired, asMinVal)
        val barArea = PI * preferredDia.toDouble().pow(2) / 4.0
        val barCount = ceil(asFinal / barArea).toInt().coerceAtLeast(2)
        val asProvided = barCount * barArea
        val rhoActual = asProvided / (b * d)

        // Top bars (hanger bars) - 25-33% of bottom
        val topBarCount = max(2, (barCount * 0.33).toInt().coerceAtLeast(2))
        val topBarDia = max(10, preferredDia - 4)
        val topAsProvided = topBarCount * PI * topBarDia.toDouble().pow(2) / 4.0

        // Moment of Resistance (PDF 12)
        val MR = when(code) {
            DesignCode.ECP -> {
                val fs = fy / GAMMA_S_ECP
                val aProvided = if (asProvided > 0) BETA_WHITNEY * asProvided * fs / (0.67 * fcu / GAMMA_C_ECP * b) else 0.0
                val zProvided = d - aProvided / 2.0
                asProvided * fs * zProvided / 1e6
            }
            else -> {
                val fcPrime = fcu * 0.8
                val aProvided = if (asProvided > 0) asProvided * fy / (0.85 * fcPrime * b) else 0.0
                PHI_FLEXURE_ACI * asProvided * fy * (d - aProvided / 2.0) / 1e6
            }
        }

        // Empirical method check (PDF 11) - minimum steel from empirical rules
        if (asProvided < asMinVal) {
            warnings.add("Provided As (${String.format("%.0f", asProvided)} mm²) < As min (${String.format("%.0f", asMinVal)} mm²) - using min")
            codeNotes.add("PDF-11: Empirical method - As min = max(0.26√f'cu/fy, 0.0013)×b×d")
        }
        if (rhoActual > 0.04) {
            warnings.add("ρ = ${String.format("%.3f", rhoActual)} > 4% - section too small")
        }

        return FlexureResult(
            K = K, Kbal = Kbal, omega = omega, leverArm = leverArm,
            neutralAxisDepth = neutralAxisDepth, stressBlockDepth = stressBlockDepth,
            Rn = Rn, rhoActual = rhoActual, rhoBalanced = rhoBal, beta1 = beta1,
            asRequired = asFinal, asMin = asMinVal, asMax = asMaxVal, asProvided = asProvided,
            rhoMin = rhoMin, rhoMax = rhoMax,
            barCount = barCount, topBarCount = topBarCount, topBarDia = topBarDia,
            topAsProvided = topAsProvided,
            momentOfResistance = MR,
            needsCompressionSteel = needsCompSteel,
            compressionBars = compBars, compressionBarCount = compBarCount,
            compressionBarDia = compBarDia, compressionAsProvided = compAsProvided,
            steps = steps, warnings = warnings, codeNotes = codeNotes
        )
    }

    // ==================== HELPER FUNCTIONS ====================

    fun calculateKbal(fcu: Double, fy: Double, code: DesignCode): Double {
        return when (code) {
            DesignCode.ECP -> {
                // Kbal depends on fcu and fy - simplified formula
                val eps_cu = 0.0035
                val eps_y = (fy / 1.15) / 200000.0
                val xD = eps_cu / (eps_cu + eps_y)
                0.4 * xD * (1.0 - 0.5 * BETA_WHITNEY * xD)
            }
            else -> {
                // ACI/SBC: convert to equivalent K
                val fcPrime = fcu * 0.8
                val beta1 = calculateBeta1(fcu, code)
                val rhoBal = 0.85 * fcPrime / fy * beta1 * 0.003 / (0.003 + fy / 200000.0)
                rhoBal * fy / (0.85 * fcPrime) * (1.0 - rhoBal * fy / (2.0 * 0.85 * fcPrime))
            }
        }
    }

    fun calculateRnBalanced(fcu: Double, fy: Double, code: DesignCode): Double {
        val fcPrime = fcu * 0.8
        val beta1 = calculateBeta1(fcu, code)
        val rhoBal = 0.85 * fcPrime / fy * beta1 * 0.003 / (0.003 + fy / 200000.0)
        return rhoBal * fy * (1.0 - rhoBal * fy / (2.0 * 0.85 * fcPrime))
    }

    fun calculateBeta1(fcu: Double, code: DesignCode): Double {
        if (code == DesignCode.ECP) return BETA_WHITNEY
        val fcPrime = fcu * 0.8
        return when {
            fcPrime <= 28.0 -> 0.85
            fcPrime >= 55.0 -> 0.65
            else -> 0.85 - 0.05 * (fcPrime - 28.0) / 7.0
        }
    }

    fun calculateRhoBalanced(fcu: Double, fy: Double, code: DesignCode, beta1: Double): Double {
        val fcPrime = if (code == DesignCode.ECP) fcu else fcu * 0.8
        val epsCu = if (code == DesignCode.ECP) 0.0035 else 0.003
        val fsDivE = fy / 200000.0
        return 0.85 * fcPrime / fy * beta1 * epsCu / (epsCu + fsDivE)
    }

    fun buildLoadNotes(
        slabType: String, tributaryWidth: Double,
        wallLoad: Double, ownWeight: Double,
        flooring: Double, plaster: Double
    ): String {
        val notes = StringBuilder()
        notes.append("Load Sources:\n")
        if (ownWeight > 0) notes.append("  • Self-weight: ${String.format("%.2f", ownWeight)} kN/m\n")
        if (flooring > 0) notes.append("  • Flooring/finishes: ${String.format("%.2f", flooring)} kN/m\n")
        if (plaster > 0) notes.append("  • Plaster (both sides): ${String.format("%.2f", plaster)} kN/m\n")
        if (wallLoad > 0) notes.append("  • Wall load: ${String.format("%.2f", wallLoad)} kN/m\n")
        if (slabType.isNotEmpty()) notes.append("  • Slab type: $slabType, tributary width: ${String.format("%.2f", tributaryWidth)} m\n")
        return notes.toString()
    }

    // BMD/SFD diagram data generators (called from UI)
    fun generateBMD(span: Double, wu: Double, supportType: String, n: Int = 50): List<Pair<Float, Float>> {
        return when (supportType) {
            "SS", "SIMPLY_SUPPORTED" -> (0..n).map { i ->
                val x = i.toFloat() / n
                val m = (wu * x * (1 - x) * span * span / 2.0).toFloat()
                x to m
            }
            "FF", "FIXED_FIXED" -> (0..n).map { i ->
                val x = i.toFloat() / n
                val m = (wu * span * span / 12.0 * (6.0 * x * (1 - x) - 1.0)).toFloat()
                x to m
            }
            "FH", "FIXED_HINGED" -> (0..n).map { i ->
                val x = i.toFloat() / n
                val mL2 = wu * span * span
                val m = (-mL2 / 8.0 + 5.0 * mL2 * x / 8.0 - mL2 * x * x / 2.0).toFloat()
                x to m
            }
            "CANTILEVER" -> (0..n).map { i ->
                val x = i.toFloat() / n
                val m = (-wu * x * x * span * span / 2.0).toFloat()
                x to m
            }
            else -> (0..n).map { i ->
                val x = i.toFloat() / n
                (x to (wu * x * (1 - x) * span * span / 2.0).toFloat())
            }
        }
    }

    fun generateSFD(span: Double, wu: Double, supportType: String, n: Int = 50): List<Pair<Float, Float>> {
        return when (supportType) {
            "SS", "SIMPLY_SUPPORTED" -> (0..n).map { i ->
                val x = i.toFloat() / n
                (x to (wu * span / 2.0 * (1.0 - 2.0 * x)).toFloat())
            }
            "FF", "FIXED_FIXED" -> (0..n).map { i ->
                val x = i.toFloat() / n
                (x to (wu * span / 2.0 * (1.0 - 2.0 * x)).toFloat())
            }
            "FH", "FIXED_HINGED" -> (0..n).map { i ->
                val x = i.toFloat() / n
                (x to (5.0 * wu * span / 8.0 - wu * span * x).toFloat())
            }
            "CANTILEVER" -> (0..n).map { i ->
                val x = i.toFloat() / n
                (x to (-wu * span * (1.0 - x)).toFloat())
            }
            else -> (0..n).map { i ->
                val x = i.toFloat() / n
                (x to (wu * span / 2.0 * (1.0 - 2.0 * x)).toFloat())
            }
        }
    }
}
