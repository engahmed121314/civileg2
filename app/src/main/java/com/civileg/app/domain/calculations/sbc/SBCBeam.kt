package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.aci.ACIBeam
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تنفيذ الكود السعودي SBC 304-2018 للكمرات
 * SBC 304 يعتمد على ACI 318 مع تعديلات سعودية محددة:
 * - فحص العرض الأدنى للكمرات في المناطق الزلزالية
 * - متطلبات الغطاء الخرساني للبيئة المالحة
 * - نسب تسليح أدنى مختلفة للمناطق الزلزالية
 * - أطوال التثبيت المعدلة
 *
 * المراجع:
 * - SBC 304-2018 البند 10 (الكمرات)
 * - SBC 304-2018 البند 18 (المناطق الزلزالية)
 * - SBC 304-2018 البند 4 (متطلبات عامة)
 */
class SBCBeam : BeamDesign {
    
    private val aciBeam = ACIBeam()
    
    // SBC 304-2018 معاملات محددة
    companion object {
        private const val SBC_LAMBDA = 1.0       // عامل الوزن للخرسانة العادية (ب Same as ACI)
        private const val SBC_MIN_WIDTH_SEISMIC = 250.0  // mm - أقل عرض للكمرات الزلزالية
        private const val SBC_COVER_NORMAL = 40.0  // mm
        private const val SBC_COVER_CORROSIVE = 50.0  // mm - للبيئة المالحة (السعودية)
        private const val SBC_COVER_SEVERE = 65.0  // mm - للبيئة شديدة التآكل
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
        // استخدام حسابات ACI كأساس
        val result = aciBeam.calculateFlexureReinforcement(
            fcu, fy, width, effectiveDepth, totalDepth, designMoment, loadCombination
        )
        
        val updatedNotes = result.codeNotes.toMutableList()
        val updatedWarnings = result.warnings.toMutableList()
        
        // SBC 304-2018 البند 10.5: فحص العرض الأدنى للكمرات الزلزالية
        if (loadCombination == LoadCombination.DEAD_LIVE_EARTHQUAKE || 
            loadCombination == LoadCombination.DEAD_EARTHQUAKE) {
            if (width < SBC_MIN_WIDTH_SEISMIC) {
                updatedWarnings.add("SBC 304-18.4: عرض الكمرة ${width}مم < ${SBC_MIN_WIDTH_SEISMIC}مم في المنطقة الزلزالية")
            }
            // SBC 304-18.4.2: نسبة التسليح الأدنى في المناطق الزلزالية
            val rho_min_seismic = 0.25 * sqrt(0.8 * fcu) / fy
            val rho_actual = result.astProvided / (width * effectiveDepth)
            if (rho_actual < rho_min_seismic) {
                updatedWarnings.add("SBC 304-18: التسليح أقل من النسبة الزلزالية الدنيا ${String.format("%.4f", rho_min_seismic)}")
            }
            updatedNotes.add("SBC 304-2018: Seismic zone provisions applied")
        }
        
        // SBC 304-2018 البند 10.6: أطول مسافة بين الأسياخ
        val maxSpacing = min(3.0 * totalDepth, 450.0)
        
        updatedNotes.add(CodeReference.SBC.BEAM_FLEXURE)
        updatedNotes.add("SBC 304-2018: Section 10 (Beams)")
        
        return result.copy(
            codeNotes = updatedNotes,
            warnings = updatedWarnings
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
        val result = aciBeam.calculateShearReinforcement(
            fcu, fy, width, effectiveDepth, designShear, axialLoad, loadCombination
        )
        
        val updatedNotes = result.codeNotes.toMutableList()
        val updatedWarnings = result.warnings.toMutableList()
        
        // SBC 304-2018 البند 18.5: متطلبات القص في المناطق الزلزالية
        if (loadCombination == LoadCombination.DEAD_LIVE_EARTHQUAKE || 
            loadCombination == LoadCombination.DEAD_EARTHQUAKE) {
            // SBC 304-18.5.3: الحد الأقصى لتباعد الكانات في المناطق الزلزالية
            val seismicMaxSpacing = min(effectiveDepth / 4.0, 100.0)  // d/4 أو 100 مم
            if (result.stirrupSpacing > seismicMaxSpacing) {
                updatedWarnings.add("SBC 304-18.5: تباعد الكانات ${result.stirrupSpacing}مم > ${seismicMaxSpacing}مم في المنطقة الزلزالية")
            }
            // SBC 304-18.5.2: نسبة القص الأدنى في المناطق الزلزالية
            val Av_min_seismic = 0.062 * sqrt(0.8 * fcu) * width / fy * 1000  // mm²/m
            if (result.providedShearReinforcement < Av_min_seismic * 0.8) {
                updatedWarnings.add("SBC 304-18: تسليح القص أقل من النسبة الزلزالية الدنيا")
            }
            updatedNotes.add("SBC 304: Seismic shear provisions (Section 18.5)")
        }
        
        updatedNotes.add(CodeReference.SBC.BEAM_SHEAR)
        
        return result.copy(
            codeNotes = updatedNotes,
            warnings = updatedWarnings
        )
    }

    override fun checkDeflection(
        span: Double,
        totalDepth: Double,
        reinforcementRatio: Double,
        supportCondition: SupportCondition
    ): DeflectionCheckResult {
        val result = aciBeam.checkDeflection(span, totalDepth, reinforcementRatio, supportCondition)
        // SBC 304 يتبع ACI في نسب الانحراف مع تعديل بسيط
        return result.copy(
            recommendation = result.recommendation.replace("ACI", "SBC 304")
        )
    }

    override fun calculateDevelopmentLength(
        barDiameter: Double,
        fy: Double,
        fcu: Double,
        barLocation: BarLocation,
        coating: CoatingType
    ): Double {
        // SBC 304 البند 25: أطوال التثبيت
        // أساساً مثل ACI مع تعديل الغطاء السعودي
        val baseLength = aciBeam.calculateDevelopmentLength(barDiameter, fy, fcu, barLocation, coating)
        
        // SBC 304-2018: في البيئة المالحة (شائعة في المملكة)
        // يُفضل زيادة طول التثبيت 20% للأسياك المطليمة بالإيبوكسي
        val environmentFactor = when (coating) {
            CoatingType.EPOXY_COATED -> 1.0  // ACI يعالج الإيبوكسي بالفعل
            CoatingType.UNCOATED -> 1.0
            CoatingType.GALVANIZED -> 1.1  // SBC يضيف 10% للمجلفن
        }
        
        return (baseLength * environmentFactor).let { ceil(it / 25) * 25 }
    }

    // ========== حدود الكود السعودي ==========
    
    override fun getMinReinforcementRatio(): Double {
        // SBC 304-2018: نفس ACI 318 كحد أدنى عام
        return aciBeam.getMinReinforcementRatio()
    }
    
    override fun getMaxReinforcementRatio(): Double {
        // SBC 304-2018: نفس ACI 318
        return aciBeam.getMaxReinforcementRatio()
    }
    
    override fun getMinShearReinforcementRatio(): Double {
        // SBC 304-2018: نفس ACI 318
        return aciBeam.getMinShearReinforcementRatio()
    }
    
    override fun getMaxShearSpacing(): Double {
        // SBC 304-2018: نفس ACI 318
        return aciBeam.getMaxShearSpacing()
    }
    
    override fun getMinCover(): Double {
        // SBC 304-2018 البند 4: الغطاء السعودي أكثر تحفظاً
        // 50 مم للكمرات الداخلية (البيئة المالحة شائعة في المملكة)
        return SBC_COVER_CORROSIVE
    }
    
    override fun getDeflectionLimit(span: Double): Double {
        return aciBeam.getDeflectionLimit(span)
    }

    /**
     * تصميم كمرة مضاعفة التسليح (Doubly-Reinforced Beam) حسب SBC 304-2018
     * SBC 304 يعتمد على ACI 318 مع تعديل: f'c = 0.67 × fcu / 1.5
     *
     * طريقة Rn-ρ (مثل ACI لكن مع مقاومة خرسانة SBC):
     * - fc' = 0.67 × fcu / 1.5
     * - Rn = Mu / (φ × b × d²)
     * - ρ_bal = 0.85β₁(fc'/fy) × 600/(600+fy)
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
        
        // SBC 304: f'c = 0.67 × fcu / 1.5 (مقاومة الأسطوانة حسب SBC)
        val fc = 0.67 * fcu / 1.5
        val phi = 0.9
        
        // β₁ حسب مقاومة الخرسانة
        val beta1 = when {
            fc <= 28 -> 0.85
            fc >= 55 -> 0.65
            else -> 0.85 - (0.05 * (fc - 28) / 7)
        }
        
        // Rn = Mu / (φ × b × d²)
        val denominator = phi * b * d * d
        val Rn = if (denominator > 0) Mu / denominator else 0.0
        
        // ρ_bal (نسبة التسليح المتوازنة)
        val epsilonCu = 0.003
        val epsilonY = fy / 200000.0
        val rhoBal = 0.85 * beta1 * (fc / fy) * (epsilonCu / (epsilonCu + epsilonY))
        
        // Rn_bal
        val RnBal = if (fc > 0) {
            rhoBal * fy * (1.0 - 0.5 * rhoBal * fy / (0.85 * fc))
        } else 0.0
        
        // Neutral axis depth at balanced condition
        val cOverD = epsilonCu / (epsilonCu + epsilonY)
        val neutralAxisDepth = cOverD * d
        
        notes.add("SBC 304: f'c = 0.67 × fcu / 1.5 = ${String.format("%.1f", fc)} MPa")
        notes.add("Rn = ${String.format("%.2f", Rn)} MPa")
        notes.add("Rn_bal = ${String.format("%.2f", RnBal)} MPa")
        notes.add("ρ_bal = ${String.format("%.4f", rhoBal)}")
        
        // If Rn ≤ Rn_bal: singly reinforced is sufficient
        if (Rn <= RnBal) {
            val m = if (fc > 0) fy / (0.85 * fc) else 0.0
            val rho = if (m > 0 && (1 - 2 * m * Rn / fy) >= 0) {
                (1 - sqrt(1 - 2 * m * Rn / fy)) / m
            } else 0.0
            val asReq = rho * b * d
            
            // Minimum reinforcement per SBC 304
            val minSteel = max(0.25 * sqrt(fc) / fy, 1.4 / fy) * b * d
            val asFinal = max(asReq, minSteel)
            
            val a = if (fc > 0) asFinal * fy / (0.85 * fc * b) else 0.0
            val leverArm = d - a / 2
            
            notes.add("Rn ≤ Rn_bal → Singly reinforced section is sufficient")
            notes.add(CodeReference.SBC.BEAM_FLEXURE)
            
            return DoublyReinforcedResult(
                needsCompressionSteel = false,
                balancedMoment = designMoment,
                excessMoment = 0.0,
                tensionSteelArea = asFinal,
                compressionSteelArea = 0.0,
                tensionBars = selectSBCBars(asFinal),
                compressionBars = "None",
                leverArm = leverArm,
                neutralAxisDepth = neutralAxisDepth,
                isSafe = true,
                utilizationRatio = Rn / max(RnBal, 0.001),
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
        
        // As1 = ρ_bal × b × d
        val As1 = rhoBal * b * d
        val AsTotal = As1 + AsPrime
        
        // Minimum reinforcement check
        val minSteel = max(0.25 * sqrt(fc) / fy, 1.4 / fy) * b * d
        val asFinal = max(AsTotal, minSteel)
        
        // Select bars (same sizes as ACI for Saudi market)
        val tensionBars = selectSBCBars(asFinal)
        val compressionBars = if (AsPrime > 0) selectSBCBars(AsPrime, compressionSteelDia) else "None"
        
        // Calculate provided areas for capacity verification
        val tensionBarArea = parseSBCBarArea(tensionBars)
        val compressionBarArea = if (AsPrime > 0) parseSBCBarArea(compressionBars) else 0.0
        
        // Capacity: φMn = φ × [As_prov × fy × (d - a/2) + As'_prov × fy × (d - d')]
        val a1 = if (fc > 0) As1 * fy / (0.85 * fc * b) else 0.0
        val Mn = tensionBarArea * fy * (d - a1 / 2) + compressionBarArea * fy * leverArmExcess
        val capacity = phi * Mn / 1e6
        val utilizationRatio = if (capacity > 0) designMoment / capacity else 2.0
        
        notes.add("Rn > Rn_bal → Compression steel required")
        notes.add("Rn_excess = ${String.format("%.2f", RnExcess)} MPa")
        notes.add("As₁ (balanced) = ${String.format("%.0f", As1)} mm²")
        notes.add("As (total) = ${String.format("%.0f", asFinal)} mm²")
        notes.add("As' (compression) = ${String.format("%.0f", AsPrime)} mm²")
        notes.add("Neutral axis depth c = ${String.format("%.1f", neutralAxisDepth)} mm")
        notes.add("φ = $phi for tension-controlled sections")
        notes.add(CodeReference.SBC.BEAM_FLEXURE)
        notes.add("SBC 304-2018: Section 9.3.3.2 (Doubly reinforced)")
        
        return DoublyReinforcedResult(
            needsCompressionSteel = true,
            balancedMoment = phi * RnBal * b * d * d / 1e6,
            excessMoment = phi * RnExcess * b * d * d / 1e6,
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
     * اختيار أسياخ مناسبة حسب المساحة المطلوبة (SBC - مقاسات السوق السعودي)
     */
    private fun selectSBCBars(requiredArea: Double, preferredDia: Double? = null): String {
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
    private fun parseSBCBarArea(barString: String): Double {
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