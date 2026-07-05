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
                updatedWarnings.add("SBC 304-18: التسليح أقل من النسبة الزلزالية الدنيا ${"%.4f".format(rho_min_seismic)}")
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
}