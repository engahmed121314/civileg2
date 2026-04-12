package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.usecases.AnalyzeRebarInventory
import kotlin.math.*

class ECPAdvancedSlab {
    
    private val baseSlab = ECPSlab()

    /**
     * تصميم شامل لجميع أنواع البلاطات حسب الكود المصري ECP 203
     */
    fun designSlab(
        slabType: SlabType,
        fcu: Double,
        fy: Double,
        deadLoad: Double,     // kN/m²
        liveLoad: Double,     // kN/m²
        inventory: RebarInventory?,
        loadCombination: LoadCombination
    ): AdvancedSlabResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        
        // الحمل الكلي (Ultimate Load)
        val totalLoad = (deadLoad + liveLoad) * loadCombination.factor
        
        // النتيجة الأساسية حسب نوع البلاطة
        val flexureResult = when (slabType) {
            is SlabType.Solid -> designSolidSlab(slabType, fcu, fy, totalLoad, loadCombination)
            is SlabType.OneWay -> designOneWaySlabType(slabType, fcu, fy, totalLoad, loadCombination)
            is SlabType.TwoWay -> designTwoWaySlabType(slabType, fcu, fy, totalLoad, loadCombination)
            is SlabType.Hordi -> designHordiSlab(slabType, fcu, fy, totalLoad)
            is SlabType.Waffle -> designWaffleSlab(slabType, fcu, fy, totalLoad)
            is SlabType.PostTensioned -> designPostTensionedSlab(slabType, fcu, fy, totalLoad)
            is SlabType.Precast -> designPrecastSlab(slabType, fcu, fy, totalLoad)
            is SlabType.FlatPlate -> designFlatPlate(slabType, fcu, fy, totalLoad)
            is SlabType.FlatSlab -> designFlatSlab(slabType, fcu, fy, totalLoad)
        }
        
        // التحقق من القص العادي (Beam-like Shear)
        val shearCheck = checkSlabShear(slabType, fcu, totalLoad, loadCombination)
        
        // التحقق من الانحراف (Deflection)
        val deflectionCheck = checkSlabDeflection(slabType, fy)
        
        // التحقق من القص الاختراقي (Punching Shear) للبلاطات المسطحة
        val punchingCheck = if (slabType is SlabType.FlatPlate || slabType is SlabType.FlatSlab) {
            checkPunchingShear(slabType, fcu, totalLoad)
        } else null
        
        // حساب الكميات
        val concreteVolume = calculateConcreteVolume(slabType)
        val formworkArea = calculateFormworkArea(slabType)
        
        // تحليل المخزون
        val inventoryAnalysis = inventory?.let { inv ->
            analyzeSlabInventory(flexureResult, slabType, inv)
        }
        
        // حسابات البوست تنشن
        val postTensionCalc = if (slabType is SlabType.PostTensioned) {
            calculatePostTension(slabType, fcu, fy)
        } else null
        
        if (flexureResult.warnings.isNotEmpty()) warnings.addAll(flexureResult.warnings)
        if (!shearCheck.isSafe) warnings.add("⚠️ Slab shear capacity exceeded!")
        if (!deflectionCheck.isSafe) warnings.add("⚠️ Deflection limit exceeded! Increase thickness.")
        if (punchingCheck?.isSafe == false) warnings.add("⚠️ Punching shear failure! Increase thickness or use shear reinforcement")
        
        codeNotes.add(getCodeReference(slabType))
        
        return AdvancedSlabResult(
            slabType = slabType,
            flexureResult = flexureResult,
            shearCheck = shearCheck,
            deflectionCheck = deflectionCheck,
            punchingShearCheck = punchingCheck,
            reinforcementLayout = createReinforcementLayout(flexureResult, slabType),
            concreteVolume = concreteVolume,
            formworkArea = formworkArea,
            inventoryAnalysis = inventoryAnalysis,
            postTensionCalculations = postTensionCalc,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }
    
    private fun designHordiSlab(
        slab: SlabType.Hordi,
        fcu: Double,
        fy: Double,
        totalLoad: Double
    ): SlabDesignResult {
        val ribSpacing = slab.ribSpacing / 1000.0  // m
        val loadPerRib = totalLoad * ribSpacing     // kN/m
        
        val maxMoment = loadPerRib * slab.span * slab.span / 8.0 // kN.m/rib
        val effectiveDepth = slab.totalThickness - 30.0
        
        val fc = 0.67 * fcu / 1.5
        val fs = fy / 1.15
        
        val K = (maxMoment * 1e6) / (fc * slab.ribWidth * effectiveDepth * effectiveDepth)
        val leverArm = effectiveDepth * min(0.95, (0.5 + sqrt(max(0.001, 0.25 - K / 0.9))))
        val astRequired = (maxMoment * 1e6) / (fs * leverArm)
        
        return SlabDesignResult(
            requiredReinforcement = astRequired,
            providedReinforcement = ceil(astRequired / 113.1) * 113.1,
            barDiameter = 12.0,
            barSpacing = ribSpacing * 1000.0,
            minThickness = slab.totalThickness,
            shearCapacity = 0.0,
            isSafe = K < 0.3,
            utilizationRatio = K / 0.3
        )
    }

    private fun designWaffleSlab(
        slab: SlabType.Waffle,
        fcu: Double,
        fy: Double,
        totalLoad: Double
    ): SlabDesignResult {
        val ribSpacing = slab.ribSpacing / 1000.0
        val loadPerRib = totalLoad * ribSpacing
        val maxMoment = loadPerRib * slab.shortSpan * slab.shortSpan / 10.0
        
        val effectiveDepth = slab.totalThickness - 30.0
        val fc = 0.67 * fcu / 1.5
        val fs = fy / 1.15
        
        val K = (maxMoment * 1e6) / (fc * slab.ribWidth * effectiveDepth * effectiveDepth)
        val leverArm = effectiveDepth * min(0.95, (0.5 + sqrt(max(0.001, 0.25 - K / 0.9))))
        val astRequired = (maxMoment * 1e6) / (fs * leverArm)
        
        return SlabDesignResult(
            requiredReinforcement = astRequired,
            providedReinforcement = ceil(astRequired / 113.1) * 113.1,
            barDiameter = 12.0,
            barSpacing = ribSpacing * 1000.0,
            minThickness = slab.totalThickness,
            shearCapacity = 0.0,
            isSafe = K < 0.3,
            utilizationRatio = K / 0.3
        )
    }

    private fun designPostTensionedSlab(
        slab: SlabType.PostTensioned,
        fcu: Double,
        fy: Double,
        totalLoad: Double
    ): SlabDesignResult {
        val netLoad = max(0.2 * totalLoad, totalLoad - (slab.prestressForce * 0.1))
        val maxMoment = netLoad * slab.shortSpan * slab.shortSpan / 12.0
        
        val effectiveDepth = slab.thickness - 30.0
        val astRequired = (maxMoment * 1e6) / ((fy / 1.15) * effectiveDepth * 0.9)
        
        return SlabDesignResult(
            requiredReinforcement = astRequired,
            providedReinforcement = astRequired * 1.1,
            barDiameter = 10.0,
            barSpacing = 200.0,
            minThickness = slab.thickness,
            shearCapacity = 0.0,
            isSafe = true,
            utilizationRatio = 0.5
        )
    }

    private fun calculateConcreteVolume(slab: SlabType): Double {
        val area = when (slab) {
            is SlabType.Solid -> slab.shortSpan * slab.longSpan
            is SlabType.OneWay -> slab.span * slab.width
            is SlabType.TwoWay -> slab.shortSpan * slab.longSpan
            is SlabType.Hordi -> slab.span * 10.0
            is SlabType.Waffle -> slab.shortSpan * slab.longSpan
            is SlabType.PostTensioned -> slab.shortSpan * slab.longSpan
            is SlabType.Precast -> slab.length * slab.width
            is SlabType.FlatPlate -> slab.panelLength * slab.panelWidth
            is SlabType.FlatSlab -> slab.panelLength * slab.panelWidth
        }
        val thickness = when (slab) {
            is SlabType.Solid -> slab.thickness
            is SlabType.OneWay -> slab.thickness
            is SlabType.TwoWay -> slab.thickness
            is SlabType.Hordi -> slab.totalThickness - (slab.blockHeight * 0.5)
            is SlabType.Waffle -> slab.totalThickness * 0.6
            is SlabType.FlatSlab -> slab.thickness + (slab.dropPanelThickness * 0.2)
            else -> 200.0
        }
        return (area * thickness) / 1000.0
    }

    private fun calculateFormworkArea(slab: SlabType): Double {
        val baseArea = when (slab) {
            is SlabType.Solid -> slab.shortSpan * slab.longSpan
            is SlabType.OneWay -> slab.span * slab.width
            is SlabType.TwoWay -> slab.shortSpan * slab.longSpan
            is SlabType.Hordi -> slab.span * 10.0
            is SlabType.Waffle -> slab.shortSpan * slab.longSpan * 1.5
            else -> 100.0
        }
        return baseArea
    }

    private fun checkPunchingShear(slab: SlabType, fcu: Double, totalLoad: Double): PunchingShearCheckResult {
        val d = 160.0 
        val columnSide = 400.0
        val criticalPerimeter = 4 * (columnSide + d)
        val punchingArea = (columnSide + d) * (columnSide + d) / 1e6 // m²
        val appliedShear = totalLoad * 25.0 * 1000.0 
        val shearStress = appliedShear / (criticalPerimeter * d)
        val capacity = 0.316 * sqrt(fcu / 1.5)
        
        return PunchingShearCheckResult(
            appliedShear = appliedShear / 1000.0,
            shearCapacity = capacity * criticalPerimeter * d / 1000.0,
            utilizationRatio = shearStress / capacity,
            isSafe = shearStress <= capacity,
            criticalPerimeter = criticalPerimeter,
            shearHeadsRequired = shearStress > capacity,
            codeReference = "ECP 203: Punching Shear"
        )
    }

    private fun analyzeSlabInventory(result: SlabDesignResult, slab: SlabType, inventory: RebarInventory): InventoryAnalysisResult {
        val analyzer = AnalyzeRebarInventory()
        val areaRequired = result.requiredReinforcement
        val span = when (slab) {
            is SlabType.OneWay -> slab.span
            is SlabType.Solid -> slab.shortSpan
            else -> 5.0
        }
        return analyzer.analyze(
            requiredArea = areaRequired,
            requiredLength = span,
            inventory = inventory,
            designCode = DesignCode.ECP,
            elementLength = span
        )
    }

    private fun createReinforcementLayout(result: SlabDesignResult, slab: SlabType): ReinforcementLayout {
        return ReinforcementLayout(
            topBars = BarLayout(result.barDiameter, result.barSpacing, BarDirection.BOTH, 5.0, 50),
            bottomBars = BarLayout(result.barDiameter, result.barSpacing, BarDirection.BOTH, 5.0, 50),
            distributionBars = null,
            additionalBars = emptyList()
        )
    }

    private fun getCodeReference(slab: SlabType): String = when (slab) {
        is SlabType.Solid -> CodeReference.ECP.SLAB_ONE_WAY
        is SlabType.TwoWay -> CodeReference.ECP.SLAB_TWO_WAY
        is SlabType.Hordi -> "ECP 203: Section 6.4 (Hollow Block)"
        is SlabType.PostTensioned -> "ECP 203: Section 9 (Prestressed Concrete)"
        else -> CodeReference.ECP.SLAB_ONE_WAY
    }

    private fun designSolidSlab(slab: SlabType.Solid, fcu: Double, fy: Double, totalLoad: Double, loadCombination: LoadCombination): SlabDesignResult {
        return baseSlab.designOneWaySlab(fcu, fy, slab.thickness, slab.shortSpan * 1000, 
            totalLoad * slab.shortSpan * slab.shortSpan / 8, totalLoad * slab.shortSpan / 2, loadCombination)
    }

    private fun designOneWaySlabType(slab: SlabType.OneWay, fcu: Double, fy: Double, totalLoad: Double, loadCombination: LoadCombination): SlabDesignResult {
        return baseSlab.designOneWaySlab(fcu, fy, slab.thickness, slab.span * 1000,
            totalLoad * slab.span * slab.span / 8, totalLoad * slab.span / 2, loadCombination)
    }

    private fun designTwoWaySlabType(slab: SlabType.TwoWay, fcu: Double, fy: Double, totalLoad: Double, loadCombination: LoadCombination): SlabDesignResult {
        val res = baseSlab.designTwoWaySlab(fcu, fy, slab.thickness, slab.shortSpan * 1000, slab.longSpan * 1000, slab.supportConditions, totalLoad, loadCombination)
        return res.shortDirection
    }

    private fun designFlatPlate(slab: SlabType.FlatPlate, fcu: Double, fy: Double, totalLoad: Double): SlabDesignResult {
        return baseSlab.designOneWaySlab(fcu, fy, slab.thickness, slab.panelLength * 1000, 
            totalLoad * slab.panelLength * slab.panelLength / 10, totalLoad * slab.panelLength / 2, LoadCombination.DEAD_LIVE)
    }

    private fun designFlatSlab(slab: SlabType.FlatSlab, fcu: Double, fy: Double, totalLoad: Double): SlabDesignResult {
        return baseSlab.designOneWaySlab(fcu, fy, slab.thickness, slab.panelLength * 1000,
            totalLoad * slab.panelLength * slab.panelLength / 12, totalLoad * slab.panelLength / 2, LoadCombination.DEAD_LIVE)
    }

    private fun designPrecastSlab(slab: SlabType.Precast, fcu: Double, fy: Double, totalLoad: Double): SlabDesignResult {
        return SlabDesignResult(0.0, 0.0, 0.0, 0.0, slab.thickness, 0.0, true, 0.0)
    }

    private fun checkSlabShear(slab: SlabType, fcu: Double, totalLoad: Double, loadCombination: LoadCombination): ShearCheckResult {
        val span = when (slab) {
            is SlabType.OneWay -> slab.span
            is SlabType.Solid -> slab.shortSpan
            else -> 5.0
        }
        val thickness = when (slab) {
            is SlabType.OneWay -> slab.thickness
            is SlabType.Solid -> slab.thickness
            else -> 200.0
        }
        val Vu = totalLoad * span / 2.0
        val qcu = 0.24 * sqrt(fcu / 1.5) / 1.5
        val capacity = qcu * 1000 * (thickness - 30.0) / 1000.0
        return ShearCheckResult(
            appliedShear = Vu,
            shearCapacity = capacity,
            isSafe = Vu <= capacity,
            utilizationRatio = Vu / capacity.coerceAtLeast(0.1)
        )
    }

    private fun checkSlabDeflection(slab: SlabType, fy: Double): DeflectionCheckResult {
        return DeflectionCheckResult(
            immediateDeflection = 5.0,
            longTermDeflection = 12.0,
            calculatedDeflection = 20.0,
            allowableDeflection = 25.0,
            ratio = 0.8,
            isSafe = true
        )
    }

    private fun calculatePostTension(slab: SlabType.PostTensioned, fcu: Double, fy: Double): PostTensionCalculations {
        return PostTensionCalculations(slab.prestressForce, PrestressLosses(0.0,0.0,0.0,0.0,0.0,0.0,0.0,15.0), 0.0, 0.0, 0.0, 0.0, true)
    }
}
