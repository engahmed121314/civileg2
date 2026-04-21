package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.usecases.AnalyzeRebarInventory
import kotlin.math.*

class ECPAdvancedColumn : ColumnDesign {
    
    private val baseDesign = ECPColumn()

    /**
     * تصميم متقدم للأعمدة بجميع أنواعها حسب الكود المصري ECP 203
     */
    fun designAdvancedColumn(
        columnType: ColumnType,
        fcu: Double,
        fy: Double,
        axialLoad: Double,
        momentX: Double,
        momentY: Double,
        unsupportedLength: Double,  // m
        endConditions: ColumnEndConditions,
        connectedSlab: ConnectedSlabType,
        hasCap: Boolean,
        inventory: RebarInventory?,
        loadCombination: LoadCombination
    ): AdvancedColumnResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // المساحة الكلية
        val Ag = columnType.getGrossArea()
        
        // طول العمود الفعال
        val k = max(endConditions.topCondition.effectiveLengthFactor,
                   endConditions.bottomCondition.effectiveLengthFactor)
        val effectiveLength = k * unsupportedLength * 1000  // mm
        
        // نصف القطر الدوراني (Radius of Gyration)
        val radiusOfGyration = when (columnType) {
            is ColumnType.Rectangular -> min(columnType.width, columnType.depth) / sqrt(12.0)
            is ColumnType.Circular -> columnType.diameter / 4
            is ColumnType.LShaped -> columnType.thickness / sqrt(12.0)
            else -> sqrt(Ag / 12.0)
        }
        
        val slendernessRatio = effectiveLength / radiusOfGyration
        val limit = when(columnType) {
            is ColumnType.Circular -> 12.0
            else -> 15.0
        }
        
        val isSlender = slendernessRatio > limit
        if (isSlender) {
            warnings.add("⚠️ Slender column! Second-order effects (P-Delta) must be considered")
            codeNotes.add("ECP 203-4.2.7: Slenderness ratio λ=$slendernessRatio > $limit")
        }
        
        // حساب القدرة المحورية القصوى
        val axialCapacity = calculateAxialCapacity(fcu, fy, columnType, Ag)
        
        // حساب التسليح
        val reinforcementResult = when(columnType) {
            is ColumnType.Rectangular -> baseDesign.calculateReinforcement(
                fcu, fy, columnType.width, columnType.depth, axialLoad, momentX, momentY, loadCombination
            )
            is ColumnType.Circular -> {
                // تقريب دائري لمربع مكافئ
                val side = sqrt(Ag)
                baseDesign.calculateReinforcement(fcu, fy, side, side, axialLoad, momentX, momentY, loadCombination)
            }
            else -> {
                val side = sqrt(Ag)
                baseDesign.calculateReinforcement(fcu, fy, side, side, axialLoad, momentX, momentY, loadCombination)
            }
        }
        
        // فحص القص الاختراقي (Punching Shear Check)
        val punchingResult = if (connectedSlab == ConnectedSlabType.FLAT) {
            checkPunching(columnType, axialLoad, fcu, hasCap)
        } else null

        if (punchingResult?.isSafe == false) {
            warnings.add("❌ Punching shear failure! Increase section or add column head/cap.")
        }

        // تحليل المخزون وحساب الهالك
        val inventoryAnalysis = inventory?.let { inv ->
            analyzeInventory(reinforcementResult, unsupportedLength, inv)
        }
        
        val biaxialCheck = if (abs(momentX) > 0.1 && abs(momentY) > 0.1) {
            checkBiaxialLoading(columnType, axialLoad, momentX, momentY, axialCapacity)
        } else null
        
        codeNotes.add("ECP 203-2020: Section 4.2 Design of Compression Members")
        codeNotes.add("Slab Connection: ${connectedSlab.displayName}")
        if (hasCap) codeNotes.add("Column Cap provided - improved punching resistance")
        
        val steelWeight = (inventoryAnalysis?.totalWeight ?: (Ag * 0.01 * 7850 / 1e6)) * unsupportedLength
        
        return AdvancedColumnResult(
            columnType = columnType,
            axialCapacity = axialCapacity,
            momentCapacityX = momentX * 1.2,
            momentCapacityY = momentY * 1.2,
            slendernessRatio = slendernessRatio,
            isSlender = isSlender,
            effectiveLength = effectiveLength,
            reinforcementResult = reinforcementResult,
            inventoryAnalysis = inventoryAnalysis,
            biaxialCheck = biaxialCheck,
            punchingCheck = punchingResult,
            warnings = warnings,
            codeNotes = codeNotes,
            steelWeightPerMeter = steelWeight / unsupportedLength,
            concreteVolumePerMeter = Ag / 1e6
        )
    }
    
    private fun checkPunching(type: ColumnType, pu: Double, fcu: Double, hasCap: Boolean): PunchingCheckResult {
        val d = 200.0 // assume slab d=200mm for check
        val perimeter = when (type) {
            is ColumnType.Rectangular -> 2 * (type.width + d + type.depth + d)
            is ColumnType.Circular -> PI * (type.diameter + d)
            else -> 4 * (sqrt(type.getGrossArea()) + d)
        }
        
        val punchingStress = (pu * 1000.0) / (perimeter * d)
        val capacity = (if (hasCap) 1.5 else 1.0) * 0.316 * sqrt(fcu / 1.5)
        
        return PunchingCheckResult(
            appliedShear = pu,
            capacity = capacity * perimeter * d / 1000.0,
            isSafe = punchingStress <= capacity,
            hasCap = hasCap,
            criticalPerimeter = perimeter
        )
    }

    private fun calculateAxialCapacity(fcu: Double, fy: Double, columnType: ColumnType, Ag: Double): Double {
        val Ast = 0.008 * Ag // Minimum steel
        return (0.35 * fcu * (Ag - Ast) + 0.67 * fy * Ast) / 1000.0
    }
    
    private fun analyzeInventory(result: ReinforcementResult, height: Double, inventory: RebarInventory): InventoryAnalysisResult {
        val analyzer = AnalyzeRebarInventory()
        return analyzer.analyze(result.astRequired, result.numberOfBars * height, inventory, DesignCode.ECP, height)
    }
    
    private fun checkBiaxialLoading(type: ColumnType, Pu: Double, Mx: Double, My: Double, Pn: Double): BiaxialCheckResult {
        val interactionFactor = (abs(Mx) / (Mx * 1.3).coerceAtLeast(1.0)) + (abs(My) / (My * 1.3).coerceAtLeast(1.0))
        return BiaxialCheckResult(abs(Mx)/100, abs(My)/100, interactionFactor, interactionFactor <= 1.0, "Simplified interaction")
    }
    
    override fun calculateAxialCapacity(fcu: Double, fy: Double, width: Double, depth: Double, 
                                       reinforcementArea: Double, loadCombination: LoadCombination): Double {
        return baseDesign.calculateAxialCapacity(fcu, fy, width, depth, reinforcementArea, loadCombination)
    }
    
    override fun calculateReinforcement(fcu: Double, fy: Double, width: Double, depth: Double,
                                       axialLoad: Double, momentX: Double, momentY: Double,
                                       loadCombination: LoadCombination): ReinforcementResult {
        return baseDesign.calculateReinforcement(fcu, fy, width, depth, axialLoad, momentX, momentY, loadCombination)
    }
    
    override fun getMinReinforcementRatio(): Double = 0.008
    override fun getMaxReinforcementRatio(): Double = 0.04
    override fun getMinSpacing(): Double = 100.0
    override fun getMaxSpacing(): Double = 200.0
    override fun getMinCover(): Double = 40.0
}
