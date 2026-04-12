package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.usecases.AnalyzeRebarInventory
import kotlin.math.*

class ECPAdvancedBeam {
    
    private val baseBeam = ECPBeam()

    /**
     * تصميم متقدم للكمرات بجميع أنواعها حسب الكود المصري ECP 203
     */
    fun designBeam(
        beamType: BeamType,
        sectionType: BeamSectionType,
        fcu: Double,
        fy: Double,
        deadLoad: Double,     // kN/m
        liveLoad: Double,     // kN/m
        width: Double,        // mm
        depth: Double,        // mm
        inventory: RebarInventory?,
        loadCombination: LoadCombination
    ): AdvancedBeamResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // الحمل الكلي الموزع (Ultimate Load)
        val totalLoad = (deadLoad + liveLoad) * loadCombination.factor
        
        // حساب القوى القصوى بناءً على نوع الكمرة
        val maxMoment = beamType.getMaxMoment(totalLoad)
        val maxShear = beamType.getMaxShear(totalLoad)
        
        // تصميم الانحناء (Flexure Design)
        val flexureResult = baseBeam.calculateFlexureReinforcement(
            fcu, fy, width, depth - 50.0, depth, maxMoment, loadCombination
        )
        
        // تصميم القص (Shear Design)
        val shearResult = baseBeam.calculateShearReinforcement(
            fcu, fy, width, depth - 50.0, maxShear, 0.0, loadCombination
        )
        
        // التحقق من الانحراف (Deflection Check)
        val span = when (beamType) {
            is BeamType.SimplySupported -> beamType.span
            is BeamType.Fixed -> beamType.span
            is BeamType.Cantilever -> beamType.length
            is BeamType.Continuous -> beamType.spans.maxOrNull() ?: 1.0
            else -> 5.0
        }
        val deflectionCheck = checkDeflection(span, depth, fcu, totalLoad)
        
        // تحليل المخزون (Inventory Analysis)
        val inventoryAnalysis = inventory?.let { inv ->
            analyzeBeamInventory(flexureResult, span, inv)
        }
        
        // التحقق من عرض الشروخ (Crack Width)
        val crackWidth = checkCrackWidth(flexureResult, fcu, maxMoment)
        
        // التحقق من طول التماسك (Development Length)
        val devLength = checkDevelopmentLength(flexureResult, fcu, fy)
        
        codeNotes.add(CodeReference.ECP.BEAM_FLEXURE)
        codeNotes.add(CodeReference.ECP.BEAM_SHEAR)
        codeNotes.add("Beam Type: ${beamType.displayName}")
        
        return AdvancedBeamResult(
            beamType = beamType,
            sectionType = sectionType,
            flexureResult = flexureResult,
            shearResult = shearResult,
            deflectionCheck = deflectionCheck,
            momentDiagram = generateMomentDiagram(beamType, totalLoad),
            shearDiagram = generateShearDiagram(beamType, totalLoad),
            inventoryAnalysis = inventoryAnalysis,
            crackWidthCheck = crackWidth,
            developmentLengthCheck = devLength,
            warnings = warnings + flexureResult.warnings,
            codeNotes = codeNotes
        )
    }
    
    private fun analyzeBeamInventory(result: ReinforcementResult, span: Double, inventory: RebarInventory): InventoryAnalysisResult {
        val analyzer = AnalyzeRebarInventory()
        return analyzer.analyze(
            requiredArea = result.astRequired,
            requiredLength = result.numberOfBars * span,
            inventory = inventory,
            designCode = DesignCode.ECP,
            elementLength = span
        )
    }
    
    private fun checkDeflection(span: Double, depth: Double, fcu: Double, load: Double): DeflectionCheckResult {
        // حسابات تقريبية للانحراف حسب الكود المصري (L/250 limit)
        val immediate = (5 * load * (span * 1000).pow(4)) / (384 * 4400 * sqrt(fcu) * (300.0 * depth.pow(3) / 12))
        val allowable = (span * 1000) / 250.0
        return DeflectionCheckResult(
            immediateDeflection = immediate,
            longTermDeflection = immediate * 2.0, // Factor for long term
            calculatedDeflection = immediate * 2.0,
            allowableDeflection = allowable,
            isSafe = immediate * 2.0 <= allowable
        )
    }
    
    private fun checkCrackWidth(result: ReinforcementResult, fcu: Double, moment: Double): CrackWidthCheckResult {
        // تقريب لعرض الشروخ (Category 1: Interior)
        val stress = (moment * 1e6) / (result.astProvided * 0.85 * (600 - 50)) // تقريب للإجهاد
        val width = 0.0001 * stress // معامل تقريبي
        return CrackWidthCheckResult(width, 0.3, width <= 0.3, "ECP 203-4.3")
    }
    
    private fun checkDevelopmentLength(result: ReinforcementResult, fcu: Double, fy: Double): DevelopmentLengthCheckResult {
        // Ld = (fy/gammaS) * diameter / (4 * fbu)
        val fbu = 0.3 * sqrt(fcu / 1.5)
        val ld = (fy / 1.15) * result.barDiameter / (4 * fbu)
        return DevelopmentLengthCheckResult(ld, 1000.0, ld <= 1000.0, CodeReference.ECP.BEAM_DEVELOPMENT_LENGTH)
    }
    
    private fun generateMomentDiagram(beamType: BeamType, load: Double): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val length = when(beamType) {
            is BeamType.SimplySupported -> beamType.span
            is BeamType.Cantilever -> beamType.length
            else -> 5.0
        }
        for (i in 0..10) {
            val x = length * i / 10.0
            val m = if (beamType is BeamType.SimplySupported) (load * x / 2) * (length - x) else 0.0
            points.add(x to m)
        }
        return points
    }

    private fun generateShearDiagram(beamType: BeamType, load: Double): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        val length = when(beamType) {
            is BeamType.SimplySupported -> beamType.span
            else -> 5.0
        }
        for (i in 0..10) {
            val x = length * i / 10.0
            val v = if (beamType is BeamType.SimplySupported) load * (length / 2 - x) else 0.0
            points.add(x to v)
        }
        return points
    }
}
