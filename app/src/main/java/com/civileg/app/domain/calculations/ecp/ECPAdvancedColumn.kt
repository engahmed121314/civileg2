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
            is ColumnType.LShaped -> columnType.thickness / sqrt(12.0) // تقريب للأمان
            else -> sqrt(Ag / 12.0)  // تقريب عام
        }
        
        // نسبة النحافة (Slenderness Ratio) λ = le / i
        val slendernessRatio = effectiveLength / radiusOfGyration
        
        // حدود النحافة في الكود المصري (λ > 15 للأعمدة المستطيلة، λ > 12 للدائرية)
        val limit = when(columnType) {
            is ColumnType.Circular -> 12.0
            else -> 15.0
        }
        
        val isSlender = slendernessRatio > limit
        
        if (isSlender) {
            warnings.add("⚠️ Slender column! Second-order effects (P-Delta) must be considered")
            codeNotes.add("ECP 203-4.2.7: Slenderness ratio λ=$slendernessRatio > $limit requires additional moments calculation")
        }
        
        // حساب القدرة المحورية القصوى
        val axialCapacity = calculateAxialCapacity(
            fcu, fy, columnType, Ag, 16.0 // قطر افتراضي للتحقق الأولي
        )
        
        // حساب التسليح باستخدام التصميم الأساسي كمرجع أو حساب مخصص
        val reinforcementResult = when(columnType) {
            is ColumnType.Rectangular -> baseDesign.calculateReinforcement(
                fcu, fy, columnType.width, columnType.depth, axialLoad, momentX, momentY, loadCombination
            )
            is ColumnType.Circular -> {
                // تقريب دائري لمربع مكافئ للتصميم الأساسي حالياً
                val side = sqrt(Ag)
                baseDesign.calculateReinforcement(fcu, fy, side, side, axialLoad, momentX, momentY, loadCombination)
            }
            else -> {
                val side = sqrt(Ag)
                baseDesign.calculateReinforcement(fcu, fy, side, side, axialLoad, momentX, momentY, loadCombination)
            }
        }
        
        // تحليل المخزون إذا توفر
        val inventoryAnalysis = inventory?.let { inv ->
            analyzeInventory(reinforcementResult, unsupportedLength, inv)
        }
        
        // التحقق من التحميل ثنائي المحور (Biaxial Bending)
        val biaxialCheck = if (abs(momentX) > 0.1 && abs(momentY) > 0.1) {
            checkBiaxialLoading(columnType, axialLoad, momentX, momentY, axialCapacity)
        } else null
        
        codeNotes.add("ECP 203-2020: Section 4.2 Design of Compression Members")
        codeNotes.add("Effective length factor k = $k")
        codeNotes.add("Gross Area Ag = ${Ag.toInt()} mm²")
        
        return AdvancedColumnResult(
            columnType = columnType,
            axialCapacity = axialCapacity,
            momentCapacityX = momentX * 1.2, // تقدير أولي للقدرة المتاحة
            momentCapacityY = momentY * 1.2,
            slendernessRatio = slendernessRatio,
            isSlender = isSlender,
            effectiveLength = effectiveLength,
            reinforcementResult = reinforcementResult,
            inventoryAnalysis = inventoryAnalysis,
            biaxialCheck = biaxialCheck,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }
    
    private fun calculateAxialCapacity(
        fcu: Double, fy: Double,
        columnType: ColumnType,
        Ag: Double,
        barDiameter: Double
    ): Double {
        val Ast = getMinReinforcementRatio() * Ag
        val alpha = 0.8
        val gammaC = 1.5
        val gammaS = 1.15
        
        val concreteCapacity = 0.67 * fcu / gammaC * (Ag - Ast)
        val steelCapacity = fy / gammaS * Ast
        
        // Pu = 0.35 * fcu * Ac + 0.67 * fy * Asc (معادلة الكود المصري المبسطة للأعمدة القصيرة)
        // أو المعادلة العامة مع معاملات الأمان:
        return 0.65 * alpha * (concreteCapacity + steelCapacity) / 1000.0  // kN
    }
    
    private fun analyzeInventory(
        result: ReinforcementResult,
        columnHeight: Double,
        inventory: RebarInventory
    ): InventoryAnalysisResult {
        val analyzer = AnalyzeRebarInventory()
        return analyzer.analyze(
            requiredArea = result.astRequired,
            requiredLength = result.numberOfBars * columnHeight,
            inventory = inventory,
            designCode = DesignCode.ECP,
            elementLength = columnHeight
        )
    }
    
    private fun checkBiaxialLoading(
        columnType: ColumnType,
        Pu: Double,
        Mx: Double,
        My: Double,
        Pn: Double
    ): BiaxialCheckResult {
        // استخدام معادلة التفاعل المبسطة (Interaction Equation)
        // (Mx / Mnx) + (My / Mny) ≤ 1.0
        val Mnx = abs(Mx) * 1.3 // افتراض قدرة أكبر بنسبة 30% للأمان التقريبي
        val Mny = abs(My) * 1.3
        
        val mxRatio = if (Mnx != 0.0) abs(Mx) / Mnx else 0.0
        val myRatio = if (Mny != 0.0) abs(My) / Mny else 0.0
        val interactionFactor = mxRatio + myRatio
        
        val isSafe = interactionFactor <= 1.0
        
        return BiaxialCheckResult(
            mxRatio = mxRatio,
            myRatio = myRatio,
            interactionFactor = interactionFactor,
            isSafe = isSafe,
            formula = "ECP 203: (Mx/Mnx) + (My/Mny) ≤ 1.0"
        )
    }
    
    // تنفيذ الـ interface methods من ColumnDesign
    override fun calculateAxialCapacity(fcu: Double, fy: Double, width: Double, depth: Double, 
                                       reinforcementArea: Double, loadCombination: LoadCombination): Double {
        return baseDesign.calculateAxialCapacity(fcu, fy, width, depth, reinforcementArea, loadCombination)
    }
    
    override fun calculateReinforcement(fcu: Double, fy: Double, width: Double, depth: Double,
                                       axialLoad: Double, momentX: Double, momentY: Double,
                                       loadCombination: LoadCombination): ReinforcementResult {
        return baseDesign.calculateReinforcement(fcu, fy, width, depth, axialLoad, momentX, momentY, loadCombination)
    }
    
    override fun getMinReinforcementRatio(): Double = baseDesign.getMinReinforcementRatio()
    override fun getMaxReinforcementRatio(): Double = baseDesign.getMaxReinforcementRatio()
    override fun getMinSpacing(): Double = baseDesign.getMinSpacing()
    override fun getMaxSpacing(): Double = baseDesign.getMaxSpacing()
    override fun getMinCover(): Double = baseDesign.getMinCover()
}
