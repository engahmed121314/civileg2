package com.civileg.app.domain.calculations

import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * Comprehensive Column Design Engine - ECP 203 / ACI 318 / SBC 304
 * Based on Eng. Yasser El-Leathy's Column Design Notes
 * 
 * Covers:
 * 1. K-factor (effective length) from braced/unbraced tables
 * 2. Slenderness classification (Short/Long/Unsafe)
 * 3. Additional moment (M_add) for long columns
 * 4. Biaxial bending with eccentricity
 * 5. Axial capacity with moment interaction
 * 6. Minimum/maximum reinforcement
 * 7. Tie/stirrup design with condensation zones
 * 8. Step-by-step calculation logging
 */
object ColumnDesignEngine {

    private const val GAMMA_C_ECP = 1.5
    private const val GAMMA_S_ECP = 1.15
    private const val PI = 3.141592653589793

    // K-factor tables
    private data class KFactorTable(
        val braced: Map<Pair<Int, Int>, Double>,
        val unbraced: Map<Pair<Int, Int>, Double>
    )

    private val kTables = KFactorTable(
        braced = mapOf(
            Pair(1,1) to 0.75, Pair(1,2) to 0.80, Pair(1,3) to 0.90,
            Pair(2,1) to 0.80, Pair(2,2) to 0.85, Pair(2,3) to 0.95,
            Pair(3,1) to 0.90, Pair(3,2) to 0.95, Pair(3,3) to 1.00,
            Pair(4,1) to 2.20, Pair(4,2) to 2.20, Pair(4,3) to 2.20, Pair(4,4) to 2.20
        ),
        unbraced = mapOf(
            Pair(1,1) to 1.20, Pair(1,2) to 1.30, Pair(1,3) to 1.60,
            Pair(2,1) to 1.30, Pair(2,2) to 1.50, Pair(2,3) to 1.80,
            Pair(3,1) to 1.60, Pair(3,2) to 1.80, Pair(3,3) to 2.00,
            Pair(4,1) to 2.20, Pair(4,2) to 2.20, Pair(4,3) to 2.20, Pair(4,4) to 2.20
        )
    )

    fun getKFactor(isBraced: Boolean, topCond: Int, botCond: Int): Double {
        val table = if (isBraced) kTables.braced else kTables.unbraced
        val key = Pair(min(topCond, botCond), max(topCond, botCond))
        return table[key] ?: 1.0
    }

    fun designColumn(
        b: Double, t: Double, H: Double,
        beamDepthIn: Double, beamDepthOut: Double,
        cover: Double,
        Pu: Double, MextIn: Double, MextOut: Double,
        fcu: Double, fy: Double,
        isBraced: Boolean,
        topCond: Int, botCond: Int,
        preferredDia: Int,
        code: DesignCode
    ): ColumnDesignResult {
        val steps = mutableListOf<CalculationStep>()
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val ag = b * t
        val steps_s = mutableListOf<CalculationStep>()
        var stepNum = 0

        // ===== STEP 1: Effective Length =====
        stepNum++
        val K_in = getKFactor(isBraced, topCond, botCond)
        val K_out = getKFactor(isBraced, topCond, botCond)
        val Ho_in = max(0.0, H - beamDepthIn)
        val Ho_out = max(0.0, H - beamOut)
        steps_s.add(CalculationStep(
            stepNum, "Effective Length Factors (K)",
            codeReference = when(code) { DesignCode.ECP -> "ECP 203 §9-6"; DesignCode.ACI -> "ACI 318 Table 6.2.5"; else -> "SBC 304" },
            formula = "K from code tables based on end conditions",
            formulaWithValues = "K_in = $K_in, K_out = $K_out\nHo_in = H - beam_depth_in = $H - $beamDepthIn = ${String.format("%.0f", Ho_in)} mm\nHo_out = H - beam_depth_out = $H - $beamOut = ${String.format("%.0f", Ho_out)} mm",
            result = "K_in = $K_in, K_out = $K_out"", unit = "-"
        ))

        // ===== STEP 2: Slenderness Ratios =====
        stepNum++
        val lambdaIn = (K_in * Ho_in) / t
        val lambdaOut = (K_out * Ho_out) / b
        val lambdaMax = max(lambdaIn, lambdaOut)
        val (limitShort, limitLong) = if (isBraced) Pair(15.0, 30.0) else Pair(10.0, 23.0)
        val classification = when {
            lambdaMax > limitLong -> "Unsafe_Slender"
            lambdaMax > limitShort -> "Long"
            else -> "Short"
        }
        steps_s.add(CalculationStep(
            stepNum, "Slenderness Classification",
            codeReference = when(code) { DesignCode.ECP -> "ECP 203 §9-4"; else -> "ACI 318 §6.2.5" },
            formula = if (isBraced) "lambda = K*Ho/i, Braced: Short<=15, Long<=30" else "lambda = K*Ho/i, Unbraced: Short<=10, Long<=23",
            formulaWithValues = "lambda_in = $K_in * ${String.format("%.0f", Ho_in)} / $t = ${String.format("%.2f", lambdaIn)}\n" +
                    "lambda_out = $K_out * ${String.format("%.0f", Ho_out)} / $b = ${String.format("%.2f", lambdaOut)}\n" +
                    "lambda_max = max($lambdaIn, $lambdaOut) = $lambdaMax ${if (isBraced) "(Braced: Short<=15, Long<=30)" else "(Unbraced: Short<=10, Long<=23)"}",
            result = "Classification: $classification (${String.format("%.2f", lambdaMax)} <= $limitLong)",
            unit = "-",
            isPass = classification != "Unsafe_Slender",
            status = if (classification == "Unsafe_Slender") StepStatus.CHECK_FAIL
                     else if (classification == "Long") StepStatus.WARNING else StepStatus.CALCULATION
        ))

        if (classification == "Unsafe_Slender") {
            warnings.add("Slenderness exceeds code limit. Increase section dimensions.")
            return ColumnDesignResult(
                columnWidth = b, columnDepth = t, totalHeight = H,
                clearHeightIn = Ho_in, clearHeightOut = Ho_out,
                fcu = fcu, fy = fy, Pu = Pu, MextIn = MextIn, MextOut = MextOut,
                isBraced = isBraced, topCond = topCond, botCond = botCond,
                beamDepthIn = beamDepthIn, beamDepthOut = beamDepthOut, cover = cover,
                designCode = code, KfactorIn = K_in, KfactorOut = K_out,
                lambdaIn = lambdaIn, lambdaOut = lambdaOut, lambdaMax = lambdaMax,
                lambdaLimitShort = limitShort, lambdaLimitLong = limitLong,
                columnClassification = classification,
                deflectionIn = 0.0, deflectionOut = 0.0, MaddIn = 0.0, MaddOut = 0.0,
                MdesIn = 0.0, MdesOut = 0.0, eccentricityIn = 0.0, eccentricityOut = 0.0,
                Phi = 0.0, alphaFactor = 0.0, Pu0 = 0.0, axialCapacity = 0.0,
                utilizationRatio = 0.0, AsRequired = 0.0, AsMin = 0.0, AsMax = 0.0,
                AsProvided = 0.0, rhoActual = 0.0, finalBars = "", finalBarCount = 0, finalBarDia = 0,
                isSafe = false, calculationSteps = steps_s, warnings = warnings, codeNotes = codeNotes
            )
        }

        // ===== STEP 3: Additional Moment (Long Columns) =====
        stepNum++
        val (deflIn, deflOut, MaddIn, MaddOut, MdesIn, MdesOut) = when (classification) {
            "Short" -> {
                steps_s.add(CalculationStep(
                    stepNum, "Additional Moment (Short Column)",
                    formula = "Short column: M_add = 0 (no P-Delta effect)",
                    formulaWithValues = "M_des_in = M_ext_in = ${String.format("%.2f", MextIn)} kN.m\nM_des_out = M_ext_out = ${String.format("%.2f", MextOut)} kN.m",
                    result = "No additional moment needed for short column.", unit = "kN.m",
                    status = StepStatus.CALCULATION
                ))
                Triple(0.0, 0.0, 0.0, 0.0, MextIn, MextOut)
            }
            else -> {
                // δ = (λ² * dimension) / 2000  (dimension in meters)
                val deflIn = (lambdaIn.pow(2) * (t / 1000.0)) / 2000.0
                val deflOut = (lambdaOut.pow(2) * (b / 1000.0)) / 2000.0
                val mAddIn = Pu * deflIn
                val mAddOut = Pu * deflOut
                val mDesIn = MextIn + mAddIn
                val mDesOut = MextOut + mAddOut
                steps_s.add(CalculationStep(
                    stepNum, "Additional Moment (Long Column - P-Delta)",
                    codeReference = when(code) { DesignCode.ECP -> "ECP 203 §9-4-2"; else -> "ACI 318 §6.6.4" },
                    formula = "delta = (lambda^2 * dimension) / 2000\nM_add = Pu * delta",
                    formulaWithValues = "defl_in = (${String.format("%.2f", lambdaIn)}^2 * ${(t/1000.0)}) / 2000 = ${String.format("%.4f", deflIn)} m\n" +
                        "defl_out = (${String.format("%.2f", lambdaOut)}^2 * ${(b/1000.0)}) / 2000 = ${String.format("%.4f", deflOut)} m\n" +
                        "M_add_in = $Pu * $deflIn = ${String.format("%.2f", mAddIn)} kN.m\n" +
                        "M_add_out = $Pu * $deflOut = ${String.format(".2f", mAddOut)} kN.m\n" +
                        "M_des_in = ${String.format(".2f", MextIn)} + ${String.format(".2f", mAddIn)} = ${String.format(".2f", mDesIn)} kN.m\n" +
                        "M_des_out = ${String.format(".2f", MextOut)} + ${String.format(".2f", mAddOut)} = ${String.format(".2f", mDesOut)} kN.m",
                    result = "M_add_in = ${String.format(".2f", mAddIn)} kN.m, M_add_out = ${String.format(".2f", mAddOut)} kN.m",
                    unit = "kN.m"
                ))
                Quintuple(deflIn, deflOut, mAddIn, mAddOut, mDesIn, mDesOut)
            }
        }

        val eccIn = if (Pu > 0) (MdesIn * 1000.0) / (Pu * 1000.0) * 1000.0 else 0.0
        val eccOut = if (Pu > 0) (MdesOut * 1000.0) / (Pu * 1000.0) * 1000.0 else 0.0

        // ===== STEP 4: Design (Axial + Biaxial) =====
        stepNum++
        val d = t - cover - 10.0  // effective depth
        val d_prime = cover + 10.0  // to compression steel
        val Pu_N = Pu * 1000.0
        val MdesIn_Nmm = MdesIn * 1e6
        val MdesOut_Nmm = MdesOut * 1e6

        val (AsReq, phi, alpha, capacity, asMin, asMax, rhoMin, rhoMax, pu0) = when (code) {
            DesignCode.ECP -> {
                val fcEff = fcu / GAMMA_C_ECP
                val fs = fy / GAMMA_S_ECP
                // Eccentricity-based simplified approach
                val e = max(eccIn, eccOut) / 1000.0 // m
                val eRatio = e / d
                val magnification = 1.0 + 2.0 * eRatio.coerceIn(0.0, 1.0)
                val Pu_eff = Pu_N * magnification
                val asReqCalc = ((Pu_eff) - 0.35 * fcu * ag) / (0.67 * fs)
                val asMinECP = when(classification) {
                    "Long" -> ((0.25 + 0.05 * lambdaMax) / 100.0) * ag
                    else -> 0.008 * ag
                }
                val asMaxECP = 0.08 * ag  // ECP allows 8%
                val asReqFinal = max(asMinECP, asReqCalc)
                val asProv = calculateBarSelection(asReqFinal, preferredDia, min(b, t), 4)
                val actualAs = asProv.second
                // Recalculate capacity
                val pu0 = (0.35 * fcu * (ag - actualAs) + 0.67 * fs * actualAs) / 1000.0
                Tuple(asReqFinal, 0.0, 0.0, pu0, asMinECP, asMaxECP, asMinECP/ag*100, 8.0, actualAs/ag*100)
            }
            else -> {
                val fcPrime = fcu * 0.8
                val phiVal = 0.65
                val alphaVal = 0.80
                // Simplified: Pu/(phi*alpha) with moment
                val e = max(eccIn, eccOut) / 1000.0
                val eRatio = e / d
                val magnification = 1.0 + 2.0 * eRatio.coerceIn(0.0, 1.0)
                val Pu_eff = Pu_N * magnification
                val asReqCalc = max(0.0, (Pu_eff / (alphaVal * phiVal) - 0.85 * fcPrime * ag) / (fy - 0.85 * fcPrime))
                val asMinACI = when(classification) {
                    "Long" -> ((0.25 + 0.05 * lambdaMax) / 100.0) * ag
                    else -> 0.01 * ag
                }
                val asMaxACI = 0.08 * ag
                val asReqFinal = max(asMinACI, asReqCalc)
                val asProv = calculateBarSelection(asReqFinal, preferredDia, min(b, t), 4)
                val actualAs = asProv.second
                val pu0 = (alphaVal * phiVal * (0.85 * fcPrime * (ag - actualAs) + fy * actualAs)) / 1000.0
                Tuple(asReqFinal, phiVal, alphaVal, pu0, asMinACI, asMaxACI, asMinACI/ag*100, 8.0, actualAs/ag*100)
            }
        }

        steps_s.add(CalculationStep(
            stepNum, "Reinforcement Design",
            codeReference = when(code) { DesignCode.ECP -> "ECP 203 §7-9"; DesignCode.ACI -> "ACI 318 §10.6"; else -> "SBC 304" },
            formula = when(code) {
                DesignCode.ECP -> "As = (Pu_eff - 0.35*f'cu*Ag) / (0.67*fy/1.15)"
                else -> "As = (Pu/(phi*alpha) - 0.85*f'c*Ag) / (f'y - 0.85*f'c)"
            },
            formulaWithValues = "As_req = ${String.format("%.1f", AsReq)} mm^2\n" +
                "As_min = ${String.format("%.1f", asMin)} mm^2 (${String.format("%.2f", rhoMin)}%)\n" +
                "As_max = ${String.format(".1f", asMax)} mm^2 ($rhoMax%)\n" +
                "As_provided = ${String.format(".1f", asProv.second)} mm^2 = ${asProv.first}",
            result = "As = ${asProv.first}, rho = ${String.format("%.2f", asProv.second/ag*100)}%",
            unit = "mm^2"
        ))

        // ===== STEP 5: Tie/Stirrup Design =====
        stepNum++
        val tieD = 8
        val sMax = when(code) {
            DesignCode.ECP -> minOf(200.0, 16.0 * preferredDia, 48.0 * tieD, min(b, t) / 2.0)
            else -> minOf(16.0 * preferredDia, 48.0 * tieD, min(b, t))
        }
        val condLen = min(max(b, t), H * 0.15)  // condensation zone = min(dimension, 15% of height)
        val sDense = (sMax * 0.5).coerceIn(75.0, sMax)
        val nDense = ceil(condLen / sDense).toInt()
        val nNormal = ceil((H - 2 * condLen) / sMax).toInt()
        val tieDesc = "${nDense}x${sDense.toInt()}mm (ends) + ${nNormal}x${sMax.toInt()}mm (mid)"
        val tieWeight = (nDense + nNormal) * 2.0 * ((b - 2*cover) + (t - 2*cover)) / 1000.0 * (tieD.toDouble().pow(2) / 162.0)

        steps_s.add(CalculationStep(
            stepNum, "Tie/Stirrup Design",
            codeReference = when(code) { DesignCode.ECP -> "ECP 203 §7-9-2"; else -> "ACI 318 §25.7" },
            formula = when(code) { DesignCode.ECP -> "s_max = min(200, 16*db, 48*dt, min(b,t)/2)"; else -> "s_max = min(16*db, 48*dt, min(b,t))" },
            formulaWithValues = "s_max = $sMax mm\nCondensation zone = ${String.format(".0f", condLen)} mm\nDense: ${nDense}x${sDense.toInt()}mm, Normal: ${nNormal}x${sMax.toInt()}mm\nTie weight = ${String.format(".1f", tieWeight)} kg",
            result = tieDesc, unit = "-"
        ))

        // ===== STEP 6: Verification =====
        stepNum++
        val safetyChecks = mutableListOf<SafetyCheckItem>()
        val util = (Pu / capacity).coerceIn(0.0, 1.5)
        safetyChecks.add(SafetyCheckItem("Axial Capacity", Pu, capacity, "kN", capacity >= Pu, when(code) { DesignCode.ECP -> "ECP 203 §7-9"; else -> "ACI 318" }))
        safetyChecks.add(SafetyCheckItem("Min Reinforcement", asProv.second/ag*100, rhoMin, "%", asProv.second >= asMin))
        safetyChecks.add(SafetyCheckItem("Max Reinforcement", asProv.second/ag*100, rhoMax, "%", asProv.second/ag*100 <= rhoMax))
        safetyChecks.add(SafetyCheckItem("Slenderness", lambdaMax, limitLong, "", lambdaMax <= limitLong, when(code) { DesignCode.ECP -> "ECP 203 §9-4"; else -> "ACI 318 §6.2" }))

        val vol = ag * H / 1e9
        val mainWt = asProv.first * (H / 1000.0) * (preferredDia.toDouble().pow(2) / 162.0)
        val totalWt = (mainWt + tieWeight) * 1.05
        codeNotes.add(when(code) { DesignCode.ECP -> "ECP 203-2020"; DesignCode.ACI -> "ACI 318-19"; else -> "SBC 304-2018" })

        steps_s.add(CalculationStep(
            stepNum, "Verification & Summary",
            formula = "Check all safety criteria",
            formulaWithValues = "Pn0 = ${String.format(".1f", capacity)} kN >= Pu = ${String.format(".1f", Pu)} kN -> ${if(capacity >= Pu) "PASS" else "FAIL"}\n" +
                "Utilization = ${String.format(".1f", util*100)}%\n" +
                "Concrete = ${String.format(".3f", vol)} m3, Steel = ${String.format(".1f", totalWt)} kg",
            result = "${if(capacity >= Pu) "SAFE" else "UNSAFE"}",
            isPass = capacity >= Pu,
            status = if (capacity >= Pu) StepStatus.CHECK_PASS else StepStatus.CHECK_FAIL
        ))

        return ColumnDesignResult(
            columnWidth = b, columnDepth = t, totalHeight = H,
            clearHeightIn = Ho_in, clearHeightOut = Ho_out,
            fcu = fcu, fy = fy, Pu = Pu, MextIn = MextIn, MextOut = MextOut,
            isBraced = isBraced, topCond = topCond, botCond = botCond,
            beamDepthIn = beamDepthIn, beamDepthOut = beamDepthOut, cover = cover,
            designCode = code, KfactorIn = K_in, KfactorOut = K_out,
            lambdaIn = lambdaIn, lambdaOut = lambdaOut, lambdaMax = lambdaMax,
            lambdaLimitShort = limitShort, lambdaLimitLong = limitLong,
            columnClassification = classification,
            deflectionIn = deflIn, deflectionOut = deflOut,
            MaddIn = mAddIn, MaddOut = mAddOut,
            MdesIn = MdesIn, MdesOut = MdesOut,
            eccentricityIn = eccIn, eccentricityOut = eccOut,
            Phi = phi, alphaFactor = alpha, Pu0 = pu0, axialCapacity = capacity,
            utilizationRatio = util, AsRequired = AsReq, AsMin = asMin, AsMax = asMax,
            AsProvided = asProv.second, rhoActual = asProv.second/ag*100,
            rhoMin = rhoMin, rhoMax = rhoMax,
            finalBars = asProv.first, finalBarCount = asProv.first, finalBarDia = preferredDia,
            rebarAlternatives = listOf(12,14,16,18,20,22,25).map { d ->
                val a = PI * d.toDouble().pow(2) / 4.0
                RebarAlternative(ceil(AsReq / a).toInt().coerceAtLeast(4), d, a, "${ceil(AsReq/a).toInt().coerceAtLeast(4)}Ø$d")
            },
            tieDia = tieD, tieSpacingMax = sMax, tieSpacingDense = sDense,
            tieSpacingNormal = sMax, condensationZoneLength = condLen,
            tieDescription = tieDesc, tieWeightKg = tieWeight,
            isSafe = capacity >= Pu, safetyChecks = safetyChecks,
            warnings = warnings, codeNotes = codeNotes,
            concreteVolume = vol, steelWeight = totalWt,
            calculationSteps = steps_s
        )
    }

    private fun calculateBarSelection(asReq: Double, preferredDia: Int, minSide: Double, minBars: Int): Pair<String, Double> {
        val areaOne = PI * preferredDia.toDouble().pow(2) / 4.0
        var n = ceil(asReq / areaOne).toInt().coerceAtLeast(minBars)
        // Ensure bars fit in the section
        val usable = minSide - 2.0 * 50.0 - preferredDia  // 50mm cover each side
        val maxBarsPerSide = (usable / (preferredDia * 2.0)).toInt().coerceAtLeast(1)
        val maxTotal = 2 * (maxBarsPerSide + 1) // 2 long faces + corners
        if (n > maxTotal) n = maxTotal
        return Pair("${n}Ø$preferredDia", n * areaOne)
    }
}