package com.civileg.app.domain.calculations.sbc

import com.civileg.app.domain.calculations.aci.ACIBeam
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تنفيذ الكود السعودي للكمرات
 * يعتمد على ACI 318 مع تعديلات سعودية محددة
 */
class SBCBeam : BeamDesign {
    
    // SBC 304 يعتمد بشكل كبير على ACI 318 مع بعض التعديلات
    private val aciBeam = ACIBeam()
    
    override fun calculateFlexureReinforcement(
        fcu: Double,
        fy: Double,
        width: Double,
        effectiveDepth: Double,
        totalDepth: Double,
        designMoment: Double,
        loadCombination: LoadCombination
    ): ReinforcementResult {
        // معظم الحسابات مثل ACI
        val result = aciBeam.calculateFlexureReinforcement(
            fcu, fy, width, effectiveDepth, totalDepth, designMoment, loadCombination
        )
        
        // إضافة ملاحظات الكود السعودي
        val updatedNotes = result.codeNotes.toMutableList().apply {
            add(CodeReference.SBC.BEAM_FLEXURE)
        }
        val updatedWarnings = result.warnings.toMutableList()
        
        // تعديل خاص للكود السعودي: حد أدنى إضافي في المناطق الزلزالية
        // (يمكن تفعيله حسب إعدادات المشروع)
        
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
        
        // SBC قد يكون أكثر تحفظاً في مناطق الزلازل
        val updatedNotes = result.codeNotes.toMutableList().apply {
            add(CodeReference.SBC.BEAM_SHEAR)
        }
        
        return result.copy(
            codeNotes = updatedNotes
        )
    }

    override fun checkDeflection(
        span: Double,
        totalDepth: Double,
        reinforcementRatio: Double,
        supportCondition: SupportCondition
    ): DeflectionCheckResult {
        // SBC يستخدم نفس نسب الانحراف لـ ACI
        return aciBeam.checkDeflection(span, totalDepth, reinforcementRatio, supportCondition)
    }

    override fun calculateDevelopmentLength(
        barDiameter: Double,
        fy: Double,
        fcu: Double,
        barLocation: BarLocation,
        coating: CoatingType
    ): Double {
        // SBC 304 Section 12 يتبع ACI 318 Chapter 25
        return aciBeam.calculateDevelopmentLength(barDiameter, fy, fcu, barLocation, coating)
    }

    override fun getMinReinforcementRatio(): Double = aciBeam.getMinReinforcementRatio()
    override fun getMaxReinforcementRatio(): Double = aciBeam.getMaxReinforcementRatio()
    override fun getMinShearReinforcementRatio(): Double = aciBeam.getMinShearReinforcementRatio()
    override fun getMaxShearSpacing(): Double = aciBeam.getMaxShearSpacing()
    override fun getMinCover(): Double = 50.0  // SBC أكثر تحفظاً في الغطاء
    
    override fun getDeflectionLimit(span: Double): Double {
        return aciBeam.getDeflectionLimit(span)
    }
}
