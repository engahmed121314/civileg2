package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

class ECPBeam : BeamDesign {
    
    companion object {
        private const val GAMMA_C = 1.5      // معامل أمان الخرسانة
        private const val GAMMA_S = 1.15     // معامل أمان الحديد
        private const val BETA_1 = 0.8       // عامل كتلة الإجهاد
        // Ec = 4400 * sqrt(fcu) per ECP 203 (MPa, fcu in MPa)
        private fun ec(fcu: Double) = 4400.0 * sqrt(fcu)
        private const val E_S = 200000.0     // معامل مرونة الحديد
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
        
        // تحويل العزم إلى نيوتن.مم (العزم التصميمي المطلق - بدون قسمة على factor)
        val Mu = designMoment * 1e6
        
        // K-method حسب ECP 203 البند 4-2-2-1
        // K = Mu / (fcu × b × d²) - نستخدم fcu مباشرة وليس fc
        val K = Mu / (fcu * width * effectiveDepth * effectiveDepth)
        // K_bal ديناميكي حسب fcu و fy (ECP 203 البند 4-2-2-1)
        val K_bal = calculateKBal(fcu, fy)
        
        // التحقق من أن المقطع غير مفرط التسليح
        if (K > K_bal) {
            warnings.add("Section is over-reinforced! Consider increasing dimensions")
            codeNotes.add(CodeReference.ECP.BEAM_REINFORCEMENT_MAX)
        }
        
        // حساب ذراع العزم الداخلي: z = d × (0.5 + √(0.25 - K/1.25)) حسب ECP 203
        val leverArm = if (0.25 - K / 1.25 > 0) {
            effectiveDepth * (0.5 + sqrt(0.25 - K / 1.25))
        } else {
            effectiveDepth * 0.7 // Fallback for over-reinforced
        }
        
        // مساحة التسليح المطلوبة: As = Mu / (fy/γs × z) حسب ECP 203
        val fs = fy / GAMMA_S
        var astRequired = Mu / (fs * leverArm)
        
        // تطبيق حدود التسليح
        val Ag = width * effectiveDepth
        val minSteel = getMinReinforcementRatioDynamic(fcu, fy) * Ag
        val maxSteel = getMaxReinforcementRatioDynamic(fcu, fy) * Ag
        
        if (astRequired < minSteel) {
            astRequired = minSteel
            warnings.add("Minimum reinforcement applied per ${CodeReference.ECP.BEAM_REINFORCEMENT_MIN}")
        }
        
        // اختيار قطر حديد مناسب مع بدائل اقتصادية وآمنة
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
        var selectedBarDia = availableBars.firstOrNull { 
            val area = PI * it * it / 4
            ceil(astRequired / area) <= 6  // أقصى 6 أسياخ في صف واحد
        } ?: 16.0
        
        val barArea = PI * selectedBarDia * selectedBarDia / 4
        val numberOfBars = ceil(astRequired / barArea).toInt().coerceIn(2, 12)
        val astProvided = numberOfBars * barArea
        val barDiameter = selectedBarDia
        
        // نسبة الاستغلال - يجب حساب capacity قبل البدائل
        val capacity = calculateMomentCapacity(fcu, fy, width, effectiveDepth, astProvided)
        val utilizationRatio = if (capacity > 0) designMoment / capacity else 2.0
        
        // التحقق من التباعد بين الأسياخ
        val clearSpacing = if (numberOfBars > 1) {
            (width - 2 * getMinCover() - 2 * 10 - numberOfBars * barDiameter) / (numberOfBars - 1)
        } else {
            width - 2 * getMinCover()
        }
        
        if (clearSpacing < 25 || (numberOfBars > 1 && clearSpacing < barDiameter)) {
            warnings.add("Bar spacing may be insufficient - consider two layers")
        }
        
        // حساب البدائل (اقتصادية + آمنة إضافية)
        val alternatives = mutableListOf<String>()
        for (dia in availableBars) {
            val area = PI * dia * dia / 4
            val numBars = ceil(astRequired / area).toInt().coerceIn(2, 12)
            val asProv = numBars * area
            val altCapacity = calculateMomentCapacity(fcu, fy, width, effectiveDepth, asProv)
            val util = if (altCapacity > 0) designMoment / altCapacity else 2.0
            if (util in 0.5..1.0 && dia != selectedBarDia) {
                alternatives.add("${numBars}Ø${dia.toInt()} (${(util*100).toInt()}%)")
            }
        }
        if (alternatives.size >= 2) {
            codeNotes.add("Economical: ${alternatives.first()}")
            codeNotes.add("Safest: ${alternatives.last()}")
        }
        
        codeNotes.add(CodeReference.ECP.BEAM_FLEXURE)
        codeNotes.add(CodeReference.ECP.BEAM_REINFORCEMENT_MIN)
        
        return ReinforcementResult(
            astRequired = astRequired,
            astProvided = astProvided,
            barDiameter = barDiameter,
            numberOfBars = numberOfBars,
            tiesDiameter = 0.0,  // ليس للكمرات
            tiesSpacing = 0.0,
            isSafe = utilizationRatio <= 1.0 && astRequired <= maxSteel,
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
        
        val Vu = designShear * 1000  // N (القوة القصية التصميمية)
        
        // قدرة الخرسانة على تحمل القص حسب ECP 203 البند 4-3-1-2
        // qcu = 0.24 × √(fcu) ثم يُقسم على γc عند حساب القدرة
        val qcu = 0.24 * sqrt(fcu) / GAMMA_C  // MPa
        val concreteShearCapacity = qcu * width * effectiveDepth / 1000  // kN
        
        // إذا كان القص أقل من قدرة الخرسانة، نضع تسليح أدنى
        val minStirrups = getMinShearReinforcementRatio() * width * 1000 // mm²/m
        
        var requiredStirrups = 0.0
        if (Vu / 1000 > concreteShearCapacity) {
            // حساب تسليح القص المطلوب
            val excessShear = (Vu / 1000 - concreteShearCapacity) * 1000 // N
            requiredStirrups = excessShear / (fy / GAMMA_S * effectiveDepth) * 1000  // mm²/m
            requiredStirrups = max(requiredStirrups, minStirrups)
        } else {
            requiredStirrups = minStirrups
            warnings.add("Shear reinforcement at minimum per code")
        }
        
        // اختيار قطر الكانة (8, 10, 12 مم)
        val stirrupDiameter = if (width < 250) 8.0 else 10.0
        val stirrupArea = 2 * PI * stirrupDiameter * stirrupDiameter / 4  // كانة مغلقة = فرعين
        
        // حساب التباعد
        var stirrupSpacing = if (requiredStirrups > 0) stirrupArea * 1000 / requiredStirrups else getMaxShearSpacing()
        stirrupSpacing = stirrupSpacing.coerceIn(50.0, getMaxShearSpacing())
        
        // الحد الأقصى لإجهاد القص: qcu_max = 0.7 × √(fcu/γc) (ECP 203)
        val maxShearStress = 0.7 * sqrt(fcu / GAMMA_C)  // MPa
        val maxShearCapacity = maxShearStress * width * effectiveDepth / 1000  // kN
        val isSafe = (Vu / 1000) <= maxShearCapacity
        
        if (!isSafe) {
            warnings.add("WARNING: Shear stress exceeds maximum limit! Increase section or concrete strength")
        }
        
        codeNotes.add(CodeReference.ECP.BEAM_SHEAR)
        codeNotes.add("qcu = ${"%.3f".format(qcu)} MPa")
        
        return ShearReinforcementResult(
            concreteShearCapacity = concreteShearCapacity,
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
        // طريقة النسبة (Span/Depth) المبسطة حسب الكود المصري
        val basicRatio = when (supportCondition) {
            SupportCondition.SIMPLY_SUPPORTED -> 20.0
            SupportCondition.CONTINUOUS -> 26.0
            SupportCondition.CANTILEVER -> 7.0
        }
        
        // تعديل النسبة حسب نسبة التسليح - ECP 203: MF = 0.55 + (0.45 × M/bd²) / (ρ × fy) أساساً
        // طريقة مبسطة: MF = 0.55 + 0.45 × (basicRatio × d / span) × (1000 / (ρ% × 100))
        // الأبسط والأكثر دقة حسب ECP: MF = 0.55 + 0.45 × (K_bal / K) عند K ≤ K_bal
        // نستخدم الطريقة المباشرة: MF = 0.55 + (477 / (fy × ρ%)) × sqrt(basicRatio × d / span)
        // النسخة المبسطة المعتمدة: MF = 0.55 + 0.0075 × fs / (ρ × fy) 
        // حيث fs = 0.58 × fy → MF = 0.55 + 0.0075 / ρ
        val rhoPercent = (reinforcementRatio * 100).coerceAtLeast(0.15)
        val modificationFactor = 0.55 + 0.45 / rhoPercent
        val allowableRatio = basicRatio * modificationFactor
        
        val actualRatio = (span * 1000) / totalDepth  // تحويل span إلى مم
        val ratio = actualRatio / allowableRatio
        
        val calculatedDeflection = if (ratio > 1.0) {
            // تقدير تقريبي للانحراف الزائد
            (span * 1000) / 250 * ratio  // mm
        } else {
            (span * 1000) / 250  // الانحراف المسموح الأساسي
        }
        
        val allowableDeflection = getDeflectionLimit(span)
        
        return DeflectionCheckResult(
            calculatedDeflection = calculatedDeflection,
            allowableDeflection = allowableDeflection,
            ratio = ratio,
            isSafe = ratio <= 1.0,
            recommendation = if (ratio > 1.0) 
                "Increase depth to ${((span * 1000) / allowableRatio * 1.1).toInt()} mm minimum" 
                else "Deflection OK"
        )
    }

    override fun calculateDevelopmentLength(
        barDiameter: Double,
        fy: Double,
        fcu: Double,
        barLocation: BarLocation,
        coating: CoatingType
    ): Double {
        // حسب الكود المصري: Ld = (fy/γs) * φ / (4 * fb)
        // حيث fb = إجهاد التماسك
        
        val fs = fy / GAMMA_S
        // fbd = 0.3 × √(fcu) per ECP 203 §5-2-2
        val fbd = 0.3 * sqrt(fcu)  // MPa
        
        var Ld = fs * barDiameter / (4 * fbd.coerceAtLeast(0.1))
        
        // عوامل التعديل
        if (barLocation == BarLocation.TOP) Ld *= 1.3  // حديد علوي
        if (coating == CoatingType.EPOXY_COATED) Ld *= 1.2
        
        // حد أدنى
        Ld = max(Ld, 350.0)  // 350 مم كحد أدنى
        
        // تقريب لأعلى لأقرب 50 مم
        return ceil(Ld / 50) * 50
    }

    // ECP 203 §4-2-2-3: ρ_min = max(0.26 × fcu / fy, 0.0013)
    private fun getMinReinforcementRatioDynamic(fcu: Double, fy: Double): Double {
        return max(0.26 * (fcu / fy), 0.0013)
    }
    // ECP 203 §4-2-2-1: عندما K يقترب من K_bal، نحد ρ إلى ~75% من النسبة المتوازنة
    private fun getMaxReinforcementRatioDynamic(fcu: Double, fy: Double): Double {
        val kBal = calculateKBal(fcu, fy)
        val fs = fy / GAMMA_S
        val rhoBal = if (fs > 0) kBal * 1.25 * fcu / fs else 0.04
        return min(0.75 * rhoBal, 0.04)
    }
    override fun getMinReinforcementRatio(): Double = 0.0013  // حد أدنى آمن بدون fcu/fy
    override fun getMaxReinforcementRatio(): Double = 0.04   // 4%
    override fun getMinShearReinforcementRatio(): Double = 0.0015  // 0.15%
    override fun getMaxShearSpacing(): Double = 200.0  // 200 مم كحد أقصى للكانات
    override fun getMinCover(): Double = 40.0
    
    override fun getDeflectionLimit(span: Double): Double {
        // الانحراف المسموح: L/250 للأحمال الكلية (ECP 203 البند 6-3)
        return (span * 1000) / 250
    }
    
    /**
     * حساب K_bal ديناميكياً حسب fcu و fy
     * ECP 203 البند 4-2-2-1: يعتمد على نسبة المحور المحايد عند التوازن
     * K_bal = (0.67/γc) × (a/d) × (1 - a/(2d))
     * حيث a/d = 0.9 × εcu/(εcu + fy/(Es×γs))
     * التحقيق: fcu=25, fy=360 → K_bal=0.186
     */
    private fun calculateKBal(fcu: Double, fy: Double): Double {
        val Es = 200000.0  // معامل مرونة الحديد MPa
        val epsilonCu = 0.003  // إجهاد الخرسانة الأقصى عند التوازن
        val beta = 0.9  // معامل الكتلة الفعال في طريقة K (ECP 203)
        
        // إجهاد خضوع التصميم
        val epsilonY = fy / (Es * GAMMA_S)
        // نسبة عمق المحور المحايد عند التوازن
        val cOverD = epsilonCu / (epsilonCu + epsilonY)
        // نسبة عمق كتلة الإجهاد
        val aOverD = beta * cOverD
        // K_bal = (α/γc) × (a/d) × (1 - a/(2d))
        return (0.67 / GAMMA_C) * aOverD * (1.0 - aOverD / 2.0)
    }
    
    // دالة مساعدة لحساب قدرة العزم
    private fun calculateMomentCapacity(
        fcu: Double, fy: Double,
        width: Double, effectiveDepth: Double,
        ast: Double
    ): Double {
        val fc = 0.67 * fcu / GAMMA_C
        val fs = fy / GAMMA_S
        
        // عمق كتلة الإجهاد
        val a = (ast * fs) / (fc * width)
        val leverArm = effectiveDepth - a / 2
        
        // قدرة العزم
        val Mn = ast * fs * leverArm
        return Mn / 1e6  // kN.m
    }

    /**
     * تصميم كمرة مضاعفة التسليح (Doubly-Reinforced Beam) حسب ECP 203
     * يُستخدم عندما K > K_bal (المقطع يحتاج حديد ضغط)
     *
     * ECP 203 البند 4-2-2-2:
     * - Mu1 = K_bal × fcu × b × d² (العزم المتوازن)
     * - As1 = Mu1 / (fs × z1) (حديد الشد للعزم المتوازن)
     * - Mu2 = Mu - Mu1 (العزم الزائد)
     * - As' = Mu2 / (fs × (d - d')) (حديد الضغط)
     * - As2 = As' (حديد الشد الإضافي)
     * - As = As1 + As2
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
        
        // العمق الفعال (تقدير: الغطاء + كانة + نصف قطر سيخ)
        val d = depth - 60.0
        val b = width
        
        // تحويل العزم إلى نيوتن.مم
        val Mu = designMoment * 1e6
        
        // حساب K و K_bal حسب ECP 203 البند 4-2-2-1
        val K = Mu / (fcu * b * d * d)
        val kBal = calculateKBal(fcu, fy)
        
        notes.add("K = ${"%.4f".format(K)}")
        notes.add("K_bal = ${"%.4f".format(kBal)}")
        
        // عمق المحور المحايد
        val epsilonCu = 0.003
        val epsilonY = fy / (E_S * GAMMA_S)
        val cOverD = epsilonCu / (epsilonCu + epsilonY)
        val neutralAxisDepth = cOverD * d
        
        // إذا كان K ≤ K_bal: المقطع لا يحتاج حديد ضغط
        if (K <= kBal) {
            val leverArm = d * (0.5 + sqrt(max(0.0, 0.25 - K / 1.25)))
            val fs = fy / GAMMA_S
            val asReq = Mu / (fs * leverArm)
            
            // تطبيق الحد الأدنى
            val minSteel = max(0.26 * (fcu / fy), 0.0013) * b * d
            val asFinal = max(asReq, minSteel)
            
            notes.add("K ≤ K_bal → Singly reinforced section is sufficient")
            notes.add(CodeReference.ECP.BEAM_FLEXURE)
            
            return DoublyReinforcedResult(
                needsCompressionSteel = false,
                balancedMoment = designMoment,
                excessMoment = 0.0,
                tensionSteelArea = asFinal,
                compressionSteelArea = 0.0,
                tensionBars = selectECPBars(asFinal),
                compressionBars = "None",
                leverArm = leverArm,
                neutralAxisDepth = neutralAxisDepth,
                isSafe = true,
                utilizationRatio = K / kBal,
                codeNotes = notes.joinToString("\n")
            )
        }
        
        // ========== تصميم مضاعف التسليح ==========
        val fs = fy / GAMMA_S
        
        // Mu1 = العزم المتوازن (الذي يتحمله المقطع بالتسليح الأحادي)
        val Mu1 = kBal * fcu * b * d * d
        // ذراع العزم للجزء المتوازن
        val z1 = d * (0.5 + sqrt(max(0.0, 0.25 - kBal / 1.25)))
        // As1 = حديد الشد للعزم المتوازن
        val As1 = Mu1 / (fs * z1)
        
        // Mu2 = العزم الزائد
        val Mu2 = Mu - Mu1
        
        // التحقق من أن d - d' كافٍ
        val leverArmExcess = d - d_prime
        if (leverArmExcess < 30.0) {
            notes.add("WARNING: d - d' = ${"%.0f".format(leverArmExcess)} mm is too small!")
            notes.add("Increase section depth or reduce d'")
        }
        
        // As' = حديد الضغط للعزم الزائد
        val AsPrime = if (leverArmExcess > 0) Mu2 / (fs * leverArmExcess) else 0.0
        // As2 = حديد الشد الإضافي (نفس As' لأن fy' = fy)
        val As2 = AsPrime
        
        // مساحة حديد الشد الكلية
        val AsTotal = As1 + As2
        
        // تطبيق الحد الأدنى
        val minSteel = max(0.26 * (fcu / fy), 0.0013) * b * d
        val asFinal = max(AsTotal, minSteel)
        
        // اختيار الأسياخ
        val tensionBars = selectECPBars(asFinal)
        val compressionBars = if (AsPrime > 0) selectECPBars(AsPrime, compressionSteelDia) else "None"
        
        // حساب مساحة الحديد المقدمة للتحقق
        val tensionBarArea = parseBarArea(tensionBars)
        val compressionBarArea = if (AsPrime > 0) parseBarArea(compressionBars) else 0.0
        
        // حساب قدرة العزم الفعلية للمقطع المضاعف
        // φMn = As1_prov × fs × z1 + As'_prov × fs × (d - d')
        val capacityNmm = tensionBarArea * fs * z1 * 0.85 + compressionBarArea * fs * leverArmExcess
        val capacity = capacityNmm / 1e6
        val utilizationRatio = if (capacity > 0) designMoment / capacity else 2.0
        
        notes.add("K > K_bal → Compression steel required")
        notes.add("Mu_balanced = ${"%.1f".format(Mu1 / 1e6)} kN.m")
        notes.add("Mu_excess = ${"%.1f".format(Mu2 / 1e6)} kN.m")
        notes.add("As₁ (balanced) = ${"%.0f".format(As1)} mm²")
        notes.add("As₂ (excess) = ${"%.0f".format(As2)} mm²")
        notes.add("As (total) = ${"%.0f".format(asFinal)} mm²")
        notes.add("As' (compression) = ${"%.0f".format(AsPrime)} mm²")
        notes.add("Neutral axis depth c = ${"%.1f".format(neutralAxisDepth)} mm")
        notes.add(CodeReference.ECP.BEAM_FLEXURE)
        notes.add("ECP 203-2020: Section 4-2-2-2 (Doubly reinforced)")
        
        return DoublyReinforcedResult(
            needsCompressionSteel = true,
            balancedMoment = Mu1 / 1e6,
            excessMoment = Mu2 / 1e6,
            tensionSteelArea = asFinal,
            compressionSteelArea = AsPrime,
            tensionBars = tensionBars,
            compressionBars = compressionBars,
            leverArm = z1,
            neutralAxisDepth = neutralAxisDepth,
            isSafe = utilizationRatio <= 1.0,
            utilizationRatio = utilizationRatio,
            codeNotes = notes.joinToString("\n")
        )
    }

    /**
     * اختيار أسياخ مناسبة حسب المساحة المطلوبة (ECP - مقاسات مترية)
     */
    private fun selectECPBars(requiredArea: Double, preferredDia: Double? = null): String {
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0, 28.0, 32.0)
        val barDia = preferredDia ?: availableBars.firstOrNull {
            val area = PI * it * it / 4
            ceil(requiredArea / area) <= 6
        } ?: 16.0
        val barArea = PI * barDia * barDia / 4
        val numBars = ceil(requiredArea / barArea).toInt().coerceIn(2, 12)
        return "${numBars}Ø${barDia.toInt()}"
    }

    /**
     * تحليل نص الأسياخ واستخراج المساحة الإجمالية
     */
    private fun parseBarArea(barString: String): Double {
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
}
