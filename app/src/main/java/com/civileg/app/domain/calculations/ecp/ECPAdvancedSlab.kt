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
        val totalLoad = (deadLoad + liveLoad) * loadCombination.getFactorForCode(DesignCode.ECP)
        
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
        val deflectionCheck = checkSlabDeflection(slabType, fy, fcu)
        
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
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val ribSpacing = slab.ribSpacing / 1000.0  // m
        val loadPerRib = totalLoad * ribSpacing     // kN/m (الحمولة التصميمية لكل كمرتة)
        
        // عزم الانحناء الأقصى - ECP 203 البند 6-4
        val maxMoment = loadPerRib * slab.span * slab.span / 8.0 // kN.m/rib
        val effectiveDepth = slab.totalThickness - 30.0  // d = h - cover - bar/2
        
        // K-method حسب ECP 203 البند 4-2-2-1
        val Mu_Nmm = maxMoment * 1e6  // N.mm
        val K = Mu_Nmm / (fcu * slab.ribWidth * effectiveDepth * effectiveDepth)
        val K_bal = 0.186
        
        if (K > K_bal) {
            warnings.add("K=%.3f > K_bal=%.3f - زِد سماكة البلاطة الهوردية".format(K, K_bal))
        }
        
        // z = d × (0.5 + √(0.25 - K/1.25))
        val leverArm = effectiveDepth * (0.5 + sqrt(max(0.001, 0.25 - K / 1.25)))
        val fs = fy / 1.15
        val astRequired = Mu_Nmm / (fs * leverArm)
        
        // الحد الأدنى للتسليح للكمرات (0.15% Ac أو 0.13% للكمراتة)
        val Ac = slab.ribWidth * slab.totalThickness
        val asMin = 0.0015 * Ac
        val asFinal = max(astRequired, asMin)
        
        // اختيار التسليح
        val barArea = PI * 144.0 / 4.0  // 12mm
        val numBars = max(ceil(asFinal / barArea).toInt(), 1)
        val asProvided = numBars * barArea
        
        codeNotes.add("ECP 203: Hordi Slab (Section 6.4)")
        codeNotes.add("Ribs: %.0fmm c/c, Load/rib: %.1f kN/m".format(slab.ribSpacing, loadPerRib))
        codeNotes.add("Mu = %.1f kN.m, K = %.3f".format(maxMoment, K))
        
        return SlabDesignResult(
            requiredReinforcement = asFinal,
            providedReinforcement = asProvided,
            barDiameter = 12.0,
            barSpacing = ribSpacing * 1000.0,
            minThickness = slab.totalThickness,
            shearCapacity = 0.0,
            isSafe = K <= K_bal,
            utilizationRatio = K / K_bal,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    private fun designWaffleSlab(
        slab: SlabType.Waffle,
        fcu: Double,
        fy: Double,
        totalLoad: Double
    ): SlabDesignResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val ribSpacing = slab.ribSpacing / 1000.0
        val loadPerRib = totalLoad * ribSpacing
        // عزم البلاطة ذات الاتجاهين (معامل 10 للبلاطات المستمرة)
        val maxMoment = loadPerRib * slab.shortSpan * slab.shortSpan / 10.0
        
        val effectiveDepth = slab.totalThickness - 30.0
        
        // K-method حسب ECP 203
        val Mu_Nmm = maxMoment * 1e6
        val K = Mu_Nmm / (fcu * slab.ribWidth * effectiveDepth * effectiveDepth)
        val K_bal = 0.186
        
        if (K > K_bal) {
            warnings.add("K=%.3f > K_bal=%.3f".format(K, K_bal))
        }
        
        val leverArm = effectiveDepth * (0.5 + sqrt(max(0.001, 0.25 - K / 1.25)))
        val fs = fy / 1.15
        val astRequired = Mu_Nmm / (fs * leverArm)
        
        val Ac = slab.ribWidth * slab.totalThickness
        val asMin = 0.0015 * Ac
        val asFinal = max(astRequired, asMin)
        
        val barArea = PI * 144.0 / 4.0
        val numBars = max(ceil(asFinal / barArea).toInt(), 1)
        val asProvided = numBars * barArea
        
        codeNotes.add("ECP 203: Waffle Slab (Section 6.4)")
        codeNotes.add("Mu = %.1f kN.m/rib, K = %.3f".format(maxMoment, K))
        
        return SlabDesignResult(
            requiredReinforcement = asFinal,
            providedReinforcement = asProvided,
            barDiameter = 12.0,
            barSpacing = ribSpacing * 1000.0,
            minThickness = slab.totalThickness,
            shearCapacity = 0.0,
            isSafe = K <= K_bal,
            utilizationRatio = K / K_bal,
            warnings = warnings,
            codeNotes = codeNotes
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
        val thickness = when (slab) {
            is SlabType.FlatPlate -> slab.thickness
            is SlabType.FlatSlab -> slab.thickness + slab.dropPanelThickness
            else -> 200.0
        }
        val d = thickness - 20.0 - 10.0  // effective depth
        
        val columnSide = when (slab) {
            is SlabType.FlatPlate -> slab.columnSize
            is SlabType.FlatSlab -> slab.columnSize
            else -> 400.0
        }
        
        // محيط الاختراق عند بعد d/2 من وجه العمود (ECP 203 البند 4-3-2)
        val criticalPerimeter = 2.0 * (columnSide + columnSide) + 4.0 * d  // bo = 2(c1+c2) + 4d
        
        // القوة القاطعة: Vu = qu × (المنطقة - منطقة الاختراق) ≈ qu × panel area
        val panelArea = when (slab) {
            is SlabType.FlatPlate -> slab.panelLength * slab.panelWidth
            is SlabType.FlatSlab -> slab.panelLength * slab.panelWidth
            else -> 36.0  // 6m × 6m تقريبي
        }
        val qu = totalLoad  // kN/m² (حمولة التصميم لكل م²)
        val punchingArea = (columnSide + d) * (columnSide + d) / 1e6  // m²
        val netArea = panelArea / 1e6 - punchingArea  // m²
        val Vu = qu * netArea  // kN
        
        // ضغط القص المطبق: vp = Vu / (bo × d)
        val shearStress = (Vu * 1000.0) / (criticalPerimeter * d)  // N/mm² = MPa
        
        // قدرة قص الاختراق: qp = 0.316 × √(fcu/γc) (ECP 203 البند 4-3-2)
        val capacity = 0.316 * sqrt(fcu / 1.5)  // MPa
        
        val isSafe = shearStress <= capacity
        
        return PunchingShearCheckResult(
            appliedShear = Vu,
            shearCapacity = capacity * criticalPerimeter * d / 1000.0,  // kN
            utilizationRatio = shearStress / capacity,
            isSafe = isSafe,
            criticalPerimeter = criticalPerimeter,
            shearHeadsRequired = shearStress > capacity,
            codeReference = "ECP 203-2020: Section 4-3-2 (Punching Shear)",
            warnings = if (!isSafe) listOf("قص الاختراق %.2f MPa > %.2f MPa - زِد السمك أو أضف رأس عمود".format(shearStress, capacity)) else emptyList()
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
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // تصميم البلاطة المسبقة الصب - ECP 203 البند 8 (العناصر المسبقة الصب)
        // السمك الكلي = سمك البلاطة + سمك التوبينج
        val totalThickness = slab.thickness + slab.toppingThickness
        val effectiveDepth = totalThickness - 20.0 - 6.0  // cover + bar/2

        // عزم الانحناء (بسيط: بسيط الدعم أو مستمر)
        val span_m = slab.length / 1000.0
        val width_m = slab.width / 1000.0
        val loadPerMeter = totalLoad * width_m  // kN/m

        // عزم للبساطة: M = wL²/8
        val maxMoment = loadPerMeter * span_m * span_m / 8.0  // kN.m
        val Mu_Nmm = maxMoment * 1e6  // N.mm

        // K-method حسب ECP 203
        val b = 1000.0  // mm per meter
        val K = Mu_Nmm / (fcu * b * effectiveDepth * effectiveDepth)
        val K_bal = 0.186

        if (K > K_bal) {
            warnings.add("K=%.3f > K_bal=%.3f - زِد سمك البلاطة المسبقة".format(K, K_bal))
        }

        // ذراع القوة
        val leverArm = effectiveDepth * (0.5 + sqrt(max(0.001, 0.25 - K / 1.25)))
        val fs = fy / 1.15
        val astRequired = Mu_Nmm / (fs * leverArm)

        // الحد الأدنى للتسليح للعناصر المسبقة: 0.15% Ag (ECP 203 جدول 4-8)
        val asMin = 0.0015 * b * totalThickness
        val asFinal = max(astRequired, asMin)

        // اختيار القضبان
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0)
        val barDiameter = availableBars.firstOrNull { dia ->
            val area = PI * dia * dia / 4.0
            val spacing = area * 1000.0 / asFinal
            spacing <= 200.0 && spacing >= 75.0
        } ?: 12.0

        val barArea = PI * barDiameter * barDiameter / 4.0
        val barSpacing = if (asFinal > 0) {
            (barArea * 1000.0 / asFinal).coerceIn(75.0, 200.0)
        } else 200.0
        val astProvided = barArea * 1000.0 / barSpacing

        // فحص القص - ECP 203 البند 4-3-1-2
        val qcu = 0.24 * sqrt(fcu / 1.5)
        val shearCapacity = qcu * b * effectiveDepth / 1000.0  // kN/m
        val Vu = loadPerMeter * span_m / 2.0

        if (Vu > shearCapacity) {
            warnings.add("قص الخرسانة يتجاوز القدرة - زِد السمك")
        }

        // فحص طول التماسك للعناصر المسبقة (أهم من البلاطات المصبوبة موقعياً)
        val Ld = max(12.0 * barDiameter, (fs * barDiameter) / (3.5 * sqrt(fcu)))  // mm (ECP 203 البند 5-2)
        val availableLength = span_m * 1000.0 * 0.8  // 80% من البحر

        codeNotes.add("ECP 203-2020: Precast Slab (Section 8)")
        codeNotes.add("Total: %.0f + %.0f = %.0f mm".format(slab.thickness, slab.toppingThickness, totalThickness))
        codeNotes.add("Mu = %.1f kN.m, K = %.3f".format(maxMoment, K))
        codeNotes.add("Ld = %.0f mm, Available = %.0f mm".format(Ld, availableLength))
        if (Ld > availableLength) {
            warnings.add("طول التماسك المطلوب %.0f مم يتجاوز المتاح - استخدم هوك أو تعشيقة".format(Ld))
        }

        return SlabDesignResult(
            requiredReinforcement = asFinal,
            providedReinforcement = astProvided,
            barDiameter = barDiameter,
            barSpacing = barSpacing,
            minThickness = totalThickness,
            shearCapacity = shearCapacity,
            isSafe = K <= K_bal && Vu <= shearCapacity,
            utilizationRatio = K / K_bal,
            warnings = warnings,
            codeNotes = codeNotes
        )
    }

    private fun checkSlabShear(slab: SlabType, fcu: Double, totalLoad: Double, loadCombination: LoadCombination): ShearCheckResult {
        val span = when (slab) {
            is SlabType.OneWay -> slab.span
            is SlabType.Solid -> slab.shortSpan
            is SlabType.FlatPlate -> slab.panelLength
            is SlabType.FlatSlab -> slab.panelLength
            is SlabType.TwoWay -> slab.shortSpan
            is SlabType.Hordi -> slab.span
            is SlabType.Waffle -> slab.shortSpan
            else -> 5.0
        }
        val thickness = when (slab) {
            is SlabType.OneWay -> slab.thickness
            is SlabType.Solid -> slab.thickness
            is SlabType.FlatPlate -> slab.thickness
            is SlabType.FlatSlab -> slab.thickness
            is SlabType.TwoWay -> slab.thickness
            else -> 200.0
        }
        val d = thickness - 20.0 - 6.0  // effective depth (cover 20mm + bar/2)
        val Vu = totalLoad * span / 2.0  // kN/m (القوة القصية التصميمية)
        // ECP 203 البند 4-3-1-2: qcu = 0.24 × √(fcu/γc) MPa
        val qcu = 0.24 * sqrt(fcu / 1.5)
        val capacity = qcu * 1000.0 * d / 1000.0  // kN/m
        return ShearCheckResult(
            appliedShear = Vu,
            shearCapacity = capacity,
            isSafe = Vu <= capacity,
            utilizationRatio = Vu / capacity.coerceAtLeast(0.1),
            warnings = if (Vu > capacity) listOf("قص الخرسانة %.2f kN/m يتجاوز القدرة %.2f kN/m".format(Vu, capacity)) else emptyList()
        )
    }

    private fun checkSlabDeflection(slab: SlabType, fy: Double, fcu: Double = 25.0): DeflectionCheckResult {
        // حساب الانحراف حسب ECP 203 البند 6-3
        // δ = 5 × w × L⁴ / (384 × Ec × I)
        // Ec = 4400 × √(fcu) حسب ECP 203 البند 4-1-1-1
        val Ec = 4400.0 * sqrt(fcu)  // MPa
        
        val thickness = when (slab) {
            is SlabType.OneWay -> slab.thickness
            is SlabType.Solid -> slab.thickness
            is SlabType.TwoWay -> slab.thickness
            is SlabType.FlatPlate -> slab.thickness
            is SlabType.FlatSlab -> slab.thickness
            is SlabType.Hordi -> slab.totalThickness
            is SlabType.Waffle -> slab.totalThickness
            is SlabType.PostTensioned -> slab.thickness
            is SlabType.Precast -> slab.thickness
        }
        
        val span = when (slab) {
            is SlabType.OneWay -> slab.span
            is SlabType.Solid -> slab.shortSpan
            is SlabType.TwoWay -> slab.shortSpan
            is SlabType.FlatPlate -> slab.panelLength
            is SlabType.FlatSlab -> slab.panelLength
            is SlabType.Hordi -> slab.span
            is SlabType.Waffle -> slab.shortSpan
            is SlabType.PostTensioned -> slab.shortSpan
            is SlabType.Precast -> slab.length
        }
        
        val span_m = span / 1000.0
        val d = thickness - 26.0  // cover + bar/2
        
        // عزم القصور للوحدة العرضية (1m × h)
        // باستخدام نسبة التسليح التقريبية ρ = 0.005
        val rho = 0.005
        val n = 200000.0 / (4400.0 * sqrt(fcu))  // معامل النسبة
        val kd = (2.0 * n * rho + sqrt(4.0 * n * n * rho * rho + 4.0 * n * rho)) / 2.0
        val Icr = (1000.0 * (kd * d).pow(3) / 3.0 + n * rho * 1000.0 * d * (d - kd * d).pow(2)).coerceAtLeast(1000.0 * thickness.pow(3) / 12.0 * 0.5)
        
        // الحمل الحي التقريبي (5 kN/m²)
        val w_LL = 5.0  // kN/m²
        // الانحراف الفوري: δ_i = 5 × w × L⁴ / (384 × Ec × Icr)
        val deltaImmediate = 5.0 * w_LL * span_m.pow(4) * 1e12 / (384.0 * Ec * Icr)  // mm
        
        // الانحراف طويل المدى: δ_LT = δ_i × (1 + λ) حيث λ ≈ 2.0 للبلاطات الداخلية
        val longTermMultiplier = 2.0  // تقريبي للرطوبة المتوسطة
        val deltaLongTerm = deltaImmediate * (1.0 + longTermMultiplier)
        
        // الحد المسموح: L/250 للانحراف الكلي
        val allowableDeflection = span_m * 1000.0 / 250.0
        
        val ratio = deltaLongTerm / allowableDeflection
        
        return DeflectionCheckResult(
            immediateDeflection = deltaImmediate,
            longTermDeflection = deltaLongTerm,
            calculatedDeflection = deltaLongTerm,
            allowableDeflection = allowableDeflection,
            ratio = ratio,
            isSafe = ratio <= 1.0,
            message = "δ_i=%.1fmm, δ_LT=%.1fmm, δ_allow=%.1fmm".format(deltaImmediate, deltaLongTerm, allowableDeflection),
            recommendation = if (ratio > 1.0) "زِد سماكة البلاطة أو نخفض نسبة التسليح" else "الانحراف مقبول"
        )
    }

    private fun calculatePostTension(slab: SlabType.PostTensioned, fcu: Double, fy: Double): PostTensionCalculations {
        // حساب خسائر ما قبل الشد حسب ECP 203 البند 9-5
        val Pj = slab.prestressForce  // kN - قوة الشد الأولية
        val fpi = 1400.0  // MPa - إجهاد الشد الأولي (تقريبي لأسلاك 7-طن)
        val L = slab.shortSpan / 1000.0  // m
        
        // 1. خسارة الانضغاط المرن (Elastic Shortening) ≈ 2%
        val elasticShortening = 0.02 * Pj
        
        // 2. خسارة الزحف (Creep) ≈ 3-4%
        val creep = 0.035 * Pj
        
        // 3. خسارة الانكماش (Shrinkage) ≈ 2-3%
        val shrinkage = 0.025 * Pj
        
        // 4. خسارة الاسترخاء (Relaxation) ≈ 3-5%
        val relaxation = 0.04 * Pj
        
        // 5. خسارة الاحتكاك (Friction) = Pj × (KL + μθ)
        // K = 0.004/m, μ = 0.2, θ ≈ 0.05 rad/m
        val K_friction = 0.004
        val mu_friction = 0.20
        val theta = 0.05 * L
        val friction = Pj * (1.0 - exp(-(K_friction * L + mu_friction * theta)))
        
        // 6. خسارة التثبيت (Anchorage) ≈ 5-10mm × (Ap × Ep / L)
        val anchorage = 0.005 * Pj
        
        // إجمالي الخسائر
        val totalLoss = elasticShortening + creep + shrinkage + relaxation + friction + anchorage
        val totalLossPercentage = (totalLoss / Pj) * 100.0
        
        // القوة الفعالة بعد الخسائر
        val Pe = Pj - totalLoss
        
        // الحمل المكافئ من الشد المسبق
        val eccentricity = slab.eccentricity / 1000.0  // m
        val equivalentLoad = 8.0 * Pe * eccentricity / (L * L)  // kN/m (تحميل مكافئ موزع)
        
        // السهم (Camber)
        val Ec = 4400.0 * sqrt(fcu)  // MPa
        val I = 1000.0 * (slab.thickness / 1000.0).pow(3) / 12.0 * 1e12  // mm⁴
        val camber = Pe * 1000.0 * eccentricity * 1000.0 * L * L * 1e6 / (8.0 * Ec * I)  // mm
        
        val losses = PrestressLosses(
            elasticShortening = elasticShortening,
            creep = creep,
            shrinkage = shrinkage,
            relaxation = relaxation,
            friction = friction,
            anchorage = anchorage,
            totalLoss = totalLoss,
            totalLossPercentage = totalLossPercentage
        )
        
        // إجهاد عند التحميل والخدمة
        val A = 1000.0 * slab.thickness  // mm²/m
        val stressAtTransfer = (Pj * 1000.0 / A) + (Pj * 1000.0 * slab.eccentricity / (A * slab.thickness / 6.0))  // MPa
        val stressAtService = (Pe * 1000.0 / A) + (Pe * 1000.0 * slab.eccentricity / (A * slab.thickness / 6.0))  // MPa
        
        return PostTensionCalculations(
            prestressForce = Pe,
            losses = losses,
            equivalentLoad = equivalentLoad,
            camber = camber,
            stressAtTransfer = stressAtTransfer,
            stressAtService = stressAtService,
            isSafe = totalLossPercentage < 30.0
        )
    }
}
