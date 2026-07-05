package com.civileg.app.domain.calculations.aci

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

class ACIBeam : BeamDesign {
    
    companion object {
        private const val PHI_FLEXURE = 0.9      // معامل الاختزال للانحناء
        private const val PHI_SHEAR = 0.75       // معامل الاختزال للقص
        private const val BETA_1 = 0.85          // عامل كتلة الإجهاد (لـ fc' ≤ 28 MPa)
        private const val LAMBDA = 1.0           // عامل الوزن للخرسانة العادية
        private var ACI_MIN_DEVELOPMENT_LENGTH = 300.0  // mm - قابل للتعديل حسب المتطلبات
    }

    override fun calculateFlexureReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        effectiveDepth: Double,
        totalDepth: Double,
        designMoment: Double,
        loadCombination: LoadCombination
    ): ReinforcementResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        val Mu = designMoment * 1e6  // N.mm - العزم التصميمي (مضروب في معامل التحميل بالفعل)
        // ACI uses cylinder strength fc' ≈ 0.8 × fcu (cube strength)
        val fc = 0.8 * fcu  // تحويل مقاومة المكعب لمقاومة الأسطوانة
        
        // Calculate Rn
        val denominator = PHI_FLEXURE * width * effectiveDepth * effectiveDepth
        val Rn = if (denominator > 0) Mu / denominator else 0.0
        
        // Calculate reinforcement ratio ρ
        val m = if (fc > 0) fy / (0.85 * fc) else 0.0
        val rho = if (m > 0 && (1 - 2 * m * Rn / fy) >= 0) {
            (1 - sqrt(1 - 2 * m * Rn / fy)) / m
        } else {
            0.0
        }
        
        // Check maximum reinforcement (tension-controlled limit) - ACI 21.2.2
        val beta1 = calculateBeta1(fc)
        // ρ_max_tc (tension-controlled): εt ≥ 0.005 → c/d ≤ 0.375
        // ρ_max_tc = 0.85β1(fc'/fy) × 0.375
        val rhoMaxTc = 0.85 * beta1 * (fc / fy) * 0.375
        if (rho > rhoMaxTc) {
            warnings.add("Section exceeds tension-controlled limit (ρ > ρmax_tc) - εt < 0.005")
            codeNotes.add(CodeReference.ACI.BEAM_REINFORCEMENT_MAX)
        }
        
        // Required reinforcement area
        var astRequired = rho * width * effectiveDepth
        
        // Minimum reinforcement per ACI 9.6.1
        val minSteel1 = 0.25 * sqrt(fc) / fy * width * effectiveDepth
        val minSteel2 = 1.4 / fy * width * effectiveDepth
        val minSteel = max(minSteel1, minSteel2)
        
        if (astRequired < minSteel) {
            astRequired = minSteel
            warnings.add("Minimum reinforcement applied per ${CodeReference.ACI.BEAM_REINFORCEMENT_MIN}")
        }
        
        // Choose bar diameter مع بدائل اقتصادية وآمنة
        val availableBars = listOf(12.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0)  // No.4 to No.10
        var selectedBarDia = availableBars.firstOrNull { 
            val area = PI * it * it / 4
            ceil(astRequired / area) <= 6
        } ?: 19.0
        
        // حساب البدائل (اقتصادية + آمنة إضافية)
        val alternatives = mutableListOf<String>()
        for (dia in availableBars) {
            val area = PI * dia * dia / 4
            val numBars = ceil(astRequired / area).toInt().coerceIn(2, 12)
            val asProv = numBars * area
            val a_calc = if (fc > 0) asProv * fy / (0.85 * fc * width) else 0.0
            val Mn_calc = asProv * fy * (effectiveDepth - a_calc / 2)
            val cap = PHI_FLEXURE * Mn_calc / 1e6
            val util = if (cap > 0) designMoment / cap else 2.0
            if (util in 0.5..1.0 && dia != selectedBarDia) {
                alternatives.add("${numBars}Ø${dia.toInt()} (${(util*100).toInt()}%)")
            }
        }
        if (alternatives.size >= 2) {
            codeNotes.add("Economical: ${alternatives.first()}")
            codeNotes.add("Safest: ${alternatives.last()}")
        }
        
        val barArea = PI * selectedBarDia * selectedBarDia / 4
        val numberOfBars = ceil(astRequired / barArea).toInt().coerceIn(2, 12)
        val astProvided = numberOfBars * barArea
        val barDiameter = selectedBarDia
        
        // Check spacing
        val clearSpacing = if (numberOfBars > 1) {
            (width - 2 * getMinCover() - 2 * 10 - numberOfBars * barDiameter) / (numberOfBars - 1)
        } else {
            width - 2 * getMinCover()
        }
        
        if (clearSpacing < max(25.0, barDiameter)) {
            warnings.add("Consider two layers for bar spacing")
        }
        
        // Calculate actual moment capacity for verification
        val a = if (fc > 0) astProvided * fy / (0.85 * fc * width) else 0.0
        val Mn = astProvided * fy * (effectiveDepth - a / 2)
        val capacity = PHI_FLEXURE * Mn / 1e6  // kN.m
        val utilizationRatio = if (capacity > 0) designMoment / capacity else 2.0
        
        codeNotes.add(CodeReference.ACI.BEAM_FLEXURE)
        codeNotes.add("φ = $PHI_FLEXURE for tension-controlled sections")
        
        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = barDiameter,
            numberOfBars = numberOfBars,
            tiesDiameter = 0.0,
            tiesSpacing = 0.0,
            isSafe = utilizationRatio <= 1.0 && rho <= rhoMaxTc,
            utilizationRatio = utilizationRatio,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun calculateShearReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        effectiveDepth: Double,
        designShear: Double,
        axialLoad: Double,
        loadCombination: LoadCombination
    ): ShearReinforcementResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        val Vu = designShear * 1000.0  // N - القوة القصية التصميمية
        
        // Concrete shear capacity per ACI 22.5.5.1
        val fc_prime = 0.8 * fcu  // تحويل مكعب لأسطوانة
        val Vc = 0.17 * LAMBDA * sqrt(fc_prime) * width * effectiveDepth  // N
        
        // Check if reinforcement is needed
        val phiVc = PHI_SHEAR * Vc / 1000  // kN
        
        var requiredStirrups = 0.0
        if (Vu / 1000 > phiVc / 2) {
            // Minimum reinforcement
            val minAv_s = max(
                0.062 * sqrt(fc_prime) * width / fy,
                0.35 * width / fy
            ) * 1000  // mm²/m
            
            if (Vu / 1000 > phiVc) {
                // Calculate required reinforcement
                val Vs = (Vu / 1000 - phiVc) / PHI_SHEAR  // kN
                requiredStirrups = Vs * 1000 / (fy * effectiveDepth.coerceAtLeast(1.0)) * 1000  // mm²/m
                requiredStirrups = max(requiredStirrups, minAv_s)
            } else {
                requiredStirrups = minAv_s
                warnings.add("Minimum shear reinforcement applied per ACI 9.6.3")
            }
        }
        
        // Choose stirrup diameter
        val stirrupDiameter = if (Vu / 1000 > 0.5 * phiVc) 10.0 else 8.0
        val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4  // 2 legs
        
        // Calculate spacing
        var stirrupSpacing = if (requiredStirrups > 0) stirrupArea * 1000 / requiredStirrups else getMaxShearSpacing()
        
        // Spacing limits per ACI 9.7.6.2
        val maxVsLimit = 0.33 * sqrt(fc_prime) * width * effectiveDepth / 1000
        val VsActual = if (Vu / 1000 > phiVc) (Vu / 1000 - phiVc) / PHI_SHEAR else 0.0
        
        val maxSpacing1 = if (VsActual <= maxVsLimit) {
            minOf(effectiveDepth / 2, 600.0)
        } else {
            minOf(effectiveDepth / 4, 300.0)
        }
        stirrupSpacing = minOf(stirrupSpacing, maxSpacing1, getMaxShearSpacing())
        stirrupSpacing = max(stirrupSpacing, 50.0)
        
        // Maximum shear limit
        val maxVs = 0.66 * sqrt(fc_prime) * width * effectiveDepth / 1000  // kN
        val maxShearCapacity = phiVc + PHI_SHEAR * maxVs
        val isSafe = (Vu / 1000) <= maxShearCapacity
        
        if (!isSafe) {
            warnings.add("WARNING: Shear exceeds ACI limits - increase section or fc'")
        }
        
        codeNotes.add(CodeReference.ACI.BEAM_SHEAR)
        codeNotes.add("Vc = ${"%.1f".format(Vc/1000)} kN, φVc = ${"%.1f".format(phiVc)} kN")
        
        return ShearReinforcementResult(
            concreteShearCapacity = Vc / 1000,
            requiredShearReinforcement = requiredStirrups,
            providedShearReinforcement = stirrupArea * 1000 / stirrupSpacing,
            stirrupDiameter = stirrupDiameter,
            stirrupSpacing = stirrupSpacing,
            isSafe = isSafe,
            utilizationRatio = if (maxShearCapacity > 0) (Vu / 1000) / maxShearCapacity else 2.0,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    override fun checkDeflection(
        span: Double,
        totalDepth: Double,
        reinforcementRatio: Double,
        supportCondition: SupportCondition
    ): DeflectionCheckResult {
        // Simplified ratio method per ACI Table 24.2.2
        val basicRatio = when (supportCondition) {
            SupportCondition.SIMPLY_SUPPORTED -> 16.0
            SupportCondition.CONTINUOUS -> 21.0
            SupportCondition.CANTILEVER -> 8.0
        }
        
        // Modification for fy (ACI Table 7.3.1.1)
        val fyFactor = min(1.0, 0.4 + 420.0 / fy.coerceAtLeast(200.0))
        
        val actualRatio = (span * 1000) / totalDepth
        val allowableRatio = basicRatio * fyFactor
        
        val ratio = actualRatio / allowableRatio
        val allowableDeflection = getDeflectionLimit(span)
        
        val calculatedDeflection = if (ratio > 1.0) {
            allowableDeflection * ratio * 1.2
        } else {
            allowableDeflection * 0.7
        }
        
        return DeflectionCheckResult(
            calculatedDeflection = calculatedDeflection,
            allowableDeflection = allowableDeflection,
            ratio = ratio,
            isSafe = ratio <= 1.0,
            recommendation = if (ratio > 1.0) 
                "Increase depth or perform detailed deflection analysis per ACI 24.2"
                else "Span/depth ratio acceptable"
        )
    }

    override fun calculateDevelopmentLength(
        barDiameter: Double,
        fy: Double,
        fcu: Double,
        barLocation: BarLocation,
        coating: CoatingType
    ): Double {
        // ACI 25.4.2: Ld = (fy * ψt * ψe * ψs) / (1.7 * λ * √(fc')) * db
        // λ يظهر في المقام فقط (ليس في البسط)
        
        var psi_t = 1.0
        if (barLocation == BarLocation.TOP) psi_t = 1.3
        
        var psi_e = 1.0
        if (coating == CoatingType.EPOXY_COATED) psi_e = 1.2
        
        val psi_s = if (barDiameter <= 22.0) 1.0 else 0.8
        val lambda = LAMBDA
        val fc_prime = 0.8 * fcu  // تحويل مكعب لأسطوانة
        
        val numerator = fy * psi_t * psi_e * psi_s
        val denominator = 1.7 * lambda * sqrt(fc_prime.coerceAtLeast(1.0))
        
        var Ld = (numerator / denominator) * barDiameter
        
        Ld = max(Ld, ACI_MIN_DEVELOPMENT_LENGTH)
        return ceil(Ld / 25) * 25
    }

    private fun calculateBeta1(fc: Double): Double {
        return when {
            fc <= 28 -> 0.85
            fc >= 55 -> 0.65
            else -> 0.85 - (0.05 * (fc - 28) / 7)
        }
    }

    /**
     * تصميم كمرة مضاعفة التسليح (Doubly-Reinforced Beam) حسب ACI 318-19
     * يُستخدم عندما Rn > Rn_bal (المقطع يحتاج حديد ضغط)
     *
     * ACI 318-19 طريقة Rn-ρ:
     * - Rn = Mu / (φ × b × d²)
     * - ρ_bal = 0.85β₁(fc'/fy) × 600/(600+fy)
     * - Rn_bal = ρ_bal × fy × (1 - 0.5×ρ_bal×fy/(0.85×fc'))
     * - If Rn > Rn_bal: needs compression steel
     * - As' = Rn_excess × b × d² / (fy × (d - d'))
     * - As = ρ_bal × b × d + As'
     */
    fun calculateDoublyReinforcedBeam(
        designMoment: Double,  // kN.m
        width: Double,         // mm
        depth: Double,         // mm (العمق الكلي h)
        fcu: Double,           // MPa
        fy: Double,            // MPa
        compressionSteelDia: Double = 16.0,
        d_prime: Double = 50.0 // mm - المسافة من وجه الضغط لمركز حديد الضغط
    ): DoublyReinforcedResult {
        val notes = mutableListOf<String>()
        
        // العمق الفعال
        val d = depth - 60.0
        val b = width
        
        // تحويل العزم إلى نيوتن.مم
        val Mu = designMoment * 1e6
        
        // ACI uses cylinder strength fc' ≈ 0.8 × fcu
        val fc = 0.8 * fcu
        val beta1 = calculateBeta1(fc)
        
        // Calculate Rn = Mu / (φ × b × d²)
        val denominator = PHI_FLEXURE * b * d * d
        val Rn = if (denominator > 0) Mu / denominator else 0.0
        
        // Calculate ρ_bal (balanced reinforcement ratio)
        // ρ_bal = 0.85 × β₁ × (fc'/fy) × εcu/(εcu + εy)
        // εcu = 0.003, εy = fy/Es, Es = 200000 MPa
        val epsilonCu = 0.003
        val epsilonY = fy / 200000.0
        val rhoBal = 0.85 * beta1 * (fc / fy) * (epsilonCu / (epsilonCu + epsilonY))
        
        // Rn_bal = ρ_bal × fy × (1 - 0.5 × ρ_bal × fy / (0.85 × fc'))
        val RnBal = rhoBal * fy * (1.0 - 0.5 * rhoBal * fy / (0.85 * fc))
        
        // Neutral axis depth at balanced condition
        val cOverD = epsilonCu / (epsilonCu + epsilonY)
        val neutralAxisDepth = cOverD * d
        
        notes.add("Rn = ${"%.2f".format(Rn)} MPa")
        notes.add("Rn_bal = ${"%.2f".format(RnBal)} MPa")
        notes.add("ρ_bal = ${"%.4f".format(rhoBal)}")
        notes.add("β₁ = ${"%.2f".format(beta1)}")
        
        // If Rn ≤ Rn_bal: singly reinforced is sufficient
        if (Rn <= RnBal) {
            val m = if (fc > 0) fy / (0.85 * fc) else 0.0
            val rho = if (m > 0 && (1 - 2 * m * Rn / fy) >= 0) {
                (1 - sqrt(1 - 2 * m * Rn / fy)) / m
            } else 0.0
            val asReq = rho * b * d
            
            // Minimum reinforcement per ACI 9.6.1
            val minSteel = max(0.25 * sqrt(fc) / fy, 1.4 / fy) * b * d
            val asFinal = max(asReq, minSteel)
            
            // Lever arm
            val a = if (fc > 0) asFinal * fy / (0.85 * fc * b) else 0.0
            val leverArm = d - a / 2
            
            notes.add("Rn ≤ Rn_bal → Singly reinforced section is sufficient")
            notes.add(CodeReference.ACI.BEAM_FLEXURE)
            
            return DoublyReinforcedResult(
                needsCompressionSteel = false,
                balancedMoment = designMoment,
                excessMoment = 0.0,
                tensionSteelArea = asFinal,
                compressionSteelArea = 0.0,
                tensionBars = selectACIBars(asFinal),
                compressionBars = "None",
                leverArm = leverArm,
                neutralAxisDepth = neutralAxisDepth,
                isSafe = true,
                utilizationRatio = Rn / RnBal,
                codeNotes = notes.joinToString("\n")
            )
        }
        
        // ========== Doubly reinforced design ==========
        val RnExcess = Rn - RnBal
        
        // As' = Rn_excess × b × d² / (fy × (d - d'))
        val leverArmExcess = d - d_prime
        val AsPrime = if (leverArmExcess > 0) {
            RnExcess * b * d * d / (fy * leverArmExcess)
        } else 0.0
        
        // As1 = ρ_bal × b × d (tension steel for balanced portion)
        val As1 = rhoBal * b * d
        
        // As = As1 + As' (since fy' = fy)
        val AsTotal = As1 + AsPrime
        
        // Minimum reinforcement check
        val minSteel = max(0.25 * sqrt(fc) / fy, 1.4 / fy) * b * d
        val asFinal = max(AsTotal, minSteel)
        
        // Select bars
        val tensionBars = selectACIBars(asFinal)
        val compressionBars = if (AsPrime > 0) selectACIBars(AsPrime, compressionSteelDia) else "None"
        
        // Calculate provided areas for capacity verification
        val tensionBarArea = parseACIBarArea(tensionBars)
        val compressionBarArea = if (AsPrime > 0) parseACIBarArea(compressionBars) else 0.0
        
        // Capacity: φMn = φ × [As1_prov × fy × (d - a1/2) + As'_prov × fy × (d - d')]
        val a1 = if (fc > 0) As1 * fy / (0.85 * fc * b) else 0.0
        val Mn = tensionBarArea * fy * (d - a1 / 2) + compressionBarArea * fy * leverArmExcess
        val capacity = PHI_FLEXURE * Mn / 1e6
        val utilizationRatio = if (capacity > 0) designMoment / capacity else 2.0
        
        notes.add("Rn > Rn_bal → Compression steel required")
        notes.add("Rn_excess = ${"%.2f".format(RnExcess)} MPa")
        notes.add("As₁ (balanced) = ${"%.0f".format(As1)} mm²")
        notes.add("As (total) = ${"%.0f".format(asFinal)} mm²")
        notes.add("As' (compression) = ${"%.0f".format(AsPrime)} mm²")
        notes.add("Neutral axis depth c = ${"%.1f".format(neutralAxisDepth)} mm")
        notes.add("φ = $PHI_FLEXURE for tension-controlled sections")
        notes.add(CodeReference.ACI.BEAM_FLEXURE)
        notes.add("ACI 318-19: Section 9.3.3.2 (Doubly reinforced)")
        
        return DoublyReinforcedResult(
            needsCompressionSteel = true,
            balancedMoment = PHI_FLEXURE * RnBal * b * d * d / 1e6,
            excessMoment = PHI_FLEXURE * RnExcess * b * d * d / 1e6,
            tensionSteelArea = asFinal,
            compressionSteelArea = AsPrime,
            tensionBars = tensionBars,
            compressionBars = compressionBars,
            leverArm = d - a1 / 2,
            neutralAxisDepth = neutralAxisDepth,
            isSafe = utilizationRatio <= 1.0,
            utilizationRatio = utilizationRatio,
            codeNotes = notes.joinToString("\n")
        )
    }

    /**
     * اختيار أسياخ مناسبة حسب المساحة المطلوبة (ACI - مقاسات أمريكية)
     */
    private fun selectACIBars(requiredArea: Double, preferredDia: Double? = null): String {
        val availableBars = listOf(12.0, 16.0, 19.0, 22.0, 25.0, 29.0, 32.0)
        val barDia = preferredDia ?: availableBars.firstOrNull {
            val area = PI * it * it / 4
            ceil(requiredArea / area) <= 6
        } ?: 19.0
        val barArea = PI * barDia * barDia / 4
        val numBars = ceil(requiredArea / barArea).toInt().coerceIn(2, 12)
        return "${numBars}Ø${barDia.toInt()}"
    }

    /**
     * تحليل نص الأسياخ واستخراج المساحة الإجمالية
     */
    private fun parseACIBarArea(barString: String): Double {
        if (barString == "None" || !barString.contains("Ø")) return 0.0
        try {
            val parts = barString.split("Ø")
            val count = parts[0].trim().toInt()
            val dia = parts[1].trim().toInt().toDouble()
            return count * PI * dia * dia / 4
        } catch (e: Exception) {
            return 0.0
        }
    }

    override fun getMinReinforcementRatio(): Double = 0.0033
    override fun getMaxReinforcementRatio(): Double = 0.025
    override fun getMinShearReinforcementRatio(): Double = 0.0015
    override fun getMaxShearSpacing(): Double = 300.0
    override fun getMinCover(): Double = 40.0
    
    override fun getDeflectionLimit(span: Double): Double {
        return (span * 1000) / 240
    }
}
