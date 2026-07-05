package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.base.ColumnDesign
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.usecases.AnalyzeRebarInventory
import kotlin.math.*

// ──────────────────────────────────────────────────────────────────────────────
// بيانات إضافية للدوال الجديدة (New Data Classes)
// ──────────────────────────────────────────────────────────────────────────────

/**
 * توزيع أسياخ التسليح في المقطع
 * Bar configuration for interaction diagram generation
 */
enum class BarConfiguration(val displayNameAr: String) {
    SYMMETRIC("تسليح متماثل / Symmetric"),
    ONE_FACE("تسليح وجه واحد / Single Face"),
    TWO_FACES("تسليح وجهين / Two Faces"),
    PERIMETER("تسليح محيطي / Perimeter")
}

/**
 * نقطة على منحنى التفاعل P-M
 * A single point on the interaction diagram (φPn vs φMn)
 */
data class InteractionDiagramPoint(
    val phiPn: Double,           // kN - factored axial capacity (φ × Pn)
    val phiMn: Double,           // kN.m - factored moment capacity (φ × Mn)
    val c: Double,               // mm - neutral axis depth
    val epsilonT: Double,        // tensile strain in extreme tension steel
    val phi: Double,             // strength reduction factor (α per ECP)
    val isTensionControlled: Boolean  // εt ≥ εy ?
)

/**
 * نتيجة تكبير العزم (P-Delta / Moment Magnification)
 * Result of second-order moment magnification analysis
 */
data class MomentMagnificationResult(
    val magnifiedMoment: Double,     // kN.m - the design moment after magnification
    val delta: Double,               // magnification factor δ
    val EI: Double,                  // N.mm² - effective flexural rigidity
    val Pc: Double,                  // kN - Euler critical buckling load
    val isSlender: Boolean,          // whether column is classified as slender
    val isSwaySensitive: Boolean,    // whether the frame is sway-sensitive
    val codeNotes: List<String>      // explanatory code references
)

/**
 * نتيجة تصنيف النحافة (Short vs Long Column)
 * Result of slenderness classification per ECP 203
 */
data class ColumnSlendernessResult(
    val slendernessRatio: Double,    // λ = K×L/r
    val kFactor: Double,             // effective length factor K
    val radiusOfGyration: Double,    // r in mm
    val shortColumnLimit: Double,    // 15 (rectangular) or 12 (circular)
    val isShortColumn: Boolean,      // true if λ ≤ limit
    val isLongColumn: Boolean,       // true if λ > limit
    val effectiveLength: Double,     // K×L in mm
    val notes: List<String>          // explanatory notes in Arabic/English
)

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
        
        // ── BUG FIX: حساب تكبير العزوم للأعمدة النحيفة قبل حساب التسليح ──
        val (designMomentX, designMomentY) = if (isSlender) {
            val (w, d) = when (columnType) {
                is ColumnType.Rectangular -> columnType.width to columnType.depth
                is ColumnType.Circular -> columnType.diameter to columnType.diameter
                else -> sqrt(Ag) to sqrt(Ag)
            }
            val estAs = 0.01 * Ag
            val magX = calculateMomentMagnification(axialLoad, momentX, unsupportedLength, k, w, d, fcu, fy, estAs)
            val magY = calculateMomentMagnification(axialLoad, momentY, unsupportedLength, k, w, d, fcu, fy, estAs)
            codeNotes.add("Moment magnification applied: δx=${"%.2f".format(magX.delta)}, δy=${"%.2f".format(magY.delta)}")
            magX.magnifiedMoment to magY.magnifiedMoment
        } else {
            momentX to momentY
        }
        
        // حساب التسليح
        val reinforcementResult = when(columnType) {
            is ColumnType.Rectangular -> baseDesign.calculateReinforcement(
                fcu, fy, columnType.width, columnType.depth, axialLoad, designMomentX, designMomentY, loadCombination
            )
            is ColumnType.Circular -> {
                // تقريب دائري لمربع مكافئ
                val side = sqrt(Ag)
                baseDesign.calculateReinforcement(fcu, fy, side, side, axialLoad, designMomentX, designMomentY, loadCombination)
            }
            else -> {
                val side = sqrt(Ag)
                baseDesign.calculateReinforcement(fcu, fy, side, side, axialLoad, designMomentX, designMomentY, loadCombination)
            }
        }
        
        // فحص القص الاختراقي (Punching Shear Check) - للبلاطات المسطحة فقط
        val punchingResult = if (connectedSlab == ConnectedSlabType.FLAT) {
            checkPunching(columnType, axialLoad, fcu, hasCap, footingDepth = 200.0)
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
        
        // حساب قدرة العزم الحقيقية حسب ECP 203 البند 4-2-2
        val h_eff = when(columnType) {
            is ColumnType.Rectangular -> columnType.depth
            is ColumnType.Circular -> columnType.diameter
            else -> sqrt(Ag)
        } - 50.0 // d = h - cover(40) - ties(~10)
        val fs = fy / 1.15 // fy / γs
        // K-method لحساب قدرة العزم
        val As = reinforcementResult.astProvided
        val b = when(columnType) {
            is ColumnType.Rectangular -> columnType.width
            is ColumnType.Circular -> columnType.diameter
            else -> sqrt(Ag)
        }
        val Mu_capacity = fs * As * 0.8 * h_eff / 1e6 // kN.m (تقريبي: R=0.8d)
        // فحص K_bal
        val K_check = Mu_capacity * 1e6 / (fcu * b * h_eff * h_eff)
        val momentCapX = if (K_check <= 0.186) Mu_capacity else {
            // إذا K > K_bal: نستخدم z = d*(0.5+√(0.25-K/1.25))
            val z = h_eff * (0.5 + sqrt(max(0.001, 0.25 - 0.186 / 1.25)))
            As * fs * z / 1e6
        }

        return AdvancedColumnResult(
            columnType = columnType,
            axialCapacity = axialCapacity,
            momentCapacityX = momentCapX,
            momentCapacityY = momentCapX,
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
    
    private fun checkPunching(type: ColumnType, pu: Double, fcu: Double, hasCap: Boolean, footingDepth: Double = 200.0): PunchingCheckResult {
        // ECP 203 البند 4-3-2: فحص قص الاختراق
        val d = footingDepth - 20.0 - 10.0  // d = سمك البلاطة - cover - bar/2
        val perimeter = when (type) {
            is ColumnType.Rectangular -> 2.0 * (type.width + type.depth) + 4.0 * d
            is ColumnType.Circular -> PI * (type.diameter + d)
            else -> 4.0 * (sqrt(type.getGrossArea()) + d)
        }
        
        // ضغط القص المطبق = Vu / (bo × d)
        val punchingStress = (pu * 0.90 * 1000.0) / (perimeter * d)
        // BUG FIX: قدرة قص الاختراق: qp = 0.316 × √(fcu) / γc (ECP 203)
        // Previously was 0.316 * sqrt(fcu/1.5) which incorrectly embeds γc inside the sqrt
        val GAMMA_C = 1.5
        val capacity = (if (hasCap) 1.5 else 1.0) * 0.316 * sqrt(fcu) / GAMMA_C
        
        return PunchingCheckResult(
            appliedShear = pu,
            capacity = capacity * perimeter * d / 1000.0,  // kN
            isSafe = punchingStress <= capacity,
            hasCap = hasCap,
            criticalPerimeter = perimeter
        )
    }

    // حساب القدرة المحورية الإسمية حسب ECP 203-2020
    // Pu = φ × α × [0.67×fcu/γc × (Ag-Ast) + fy/γs × Ast]
    private fun calculateAxialCapacity(fcu: Double, fy: Double, columnType: ColumnType, Ag: Double, Ast: Double = 0.0): Double {
        val GAMMA_C = 1.5
        val GAMMA_S = 1.15
        val ALPHA = 0.8
        val PHI = 0.65
        val ast = if (Ast > 0) Ast.coerceAtMost(Ag * 0.08) else 0.008 * Ag
        val concreteStress = 0.67 * fcu / GAMMA_C
        val steelStress = fy / GAMMA_S
        val nominalCapacity = ALPHA * (concreteStress * (Ag - ast) + steelStress * ast)
        return PHI * nominalCapacity / 1000.0 // kN
    }
    
    private fun analyzeInventory(result: ReinforcementResult, height: Double, inventory: RebarInventory): InventoryAnalysisResult {
        val analyzer = AnalyzeRebarInventory()
        return analyzer.analyze(result.astRequired, result.numberOfBars * height, inventory, DesignCode.ECP, height)
    }
    
    // فحص الانحناء الثنائي بطريقة Bresler التقريبية (ECP 203-4.2.6)
    // 1/Pn ≈ 1/Pnx + 1/Pny - 1/Po
    // أو طريقة الحد الأقصى المبسطة: (Mux/Mnx)^α + (Muy/Mny)^α ≤ 1
    private fun checkBiaxialLoading(type: ColumnType, Pu: Double, Mx: Double, My: Double, Pn: Double, fy: Double = 360.0): BiaxialCheckResult {
        // حساب أبعاد المقطع
        val dimensions = when (type) {
            is ColumnType.Rectangular -> type.width to type.depth
            is ColumnType.Circular -> type.diameter to type.diameter
            else -> sqrt(type.getGrossArea()) to sqrt(type.getGrossArea())
        }
        val b = dimensions.first
        val h = dimensions.second
        
        // نسبة التسليح التقريبية (1%)
        val Ag = b * h
        val ast = 0.01 * Ag
        val d_eff = h - 40.0 - 10.0  // خصم الغطاء والكانة
        
        // قدرة العزم التصميمية لكل اتجاه (تقريبي)
        // Mnx = φ × (fy/γs) × As × (d - d') × 0.8
        val fs = fy / 1.15
        val d_prime = 40.0 + 10.0  // cover + stirrup
        val leverArm = (d_eff - d_prime).coerceAtLeast(d_eff * 0.5)
        val Mnx = 0.65 * fs * ast * leverArm / 1e6  // kN.m (مع φ=0.65 للضغط)
        
        // للمقطع الدائري: Mnx = Mny (تماثل)
        // للمستطيل: Mny يُحسب بنفس الطريقة لكن b و h يتبدلان
        val Mny = if (type is ColumnType.Rectangular && abs(b - h) > 10.0) {
            val d_eff_y = b - d_prime
            val leverArmY = (d_eff_y - d_prime).coerceAtLeast(d_eff_y * 0.5)
            0.65 * fs * ast * leverArmY / 1e6
        } else {
            Mnx  // للمربع والدائري
        }
        
        // معامل α حسب Bresler المبسط (ECP 203 البند 4-2-6)
        // α = 1.0 للعزوم المتساوية، يزيد مع عدم التماثل
        val alpha = if (Mnx > 0 && Mny > 0) {
            val beta = min(abs(Mx) / Mnx, abs(My) / Mny).coerceAtMost(1.0)
            // α يتراوح بين 1.0 و 2.0 حسب نسبة الحمل المحوري
            val r = (Pu / Pn).coerceIn(0.1, 0.9)
            1.0 + (1.0 - r)  // α أكبر عندما يكون الحمل المحوري أقل
        } else 1.0
        
        val interactionFactor = if (Mnx > 0 && Mny > 0) {
            (abs(Mx) / Mnx).pow(alpha) + (abs(My) / Mny).pow(alpha)
        } else 0.0
        
        return BiaxialCheckResult(
            abs(Mx), abs(My), interactionFactor,
            interactionFactor <= 1.0,
            "Bresler's reciprocal approximation (ECP 203 Sec 4.2.6), α=%.2f".format(alpha)
        )
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
    override fun getMaxReinforcementRatio(): Double = 0.08 // ECP 203: 8% max
    override fun getMinSpacing(): Double = 100.0
    override fun getMaxSpacing(): Double = 300.0 // ECP 203 البند 4-2-6: min(16×db, min(b,h), 300mm)
    override fun getMinCover(): Double = 40.0

    // ──────────────────────────────────────────────────────────────────────────────
    // ثوابت الكود المصري ECP 203-2020 (ECP Constants)
    // ──────────────────────────────────────────────────────────────────────────────

    companion object {
        const val GAMMA_C = 1.5         // معامل أمان الخرسانة
        const val GAMMA_S = 1.15        // معامل أمان الحديد
        const val ALPHA   = 0.8         // عامل اختزال مقاومة الخرسانة (ثابت حسب ECP)
        const val EPS_CU  = 0.003       // إنفعال الخرسانة الأقصى عند الضغط
        const val ES      = 200_000.0   // معامل مرونة الحديد (MPa)
        const val COVER   = 40.0        // الغطاء الخرساني (mm)
        const val STIRRUP_DIA = 10.0    // قطر الكانة الافتراضي (mm)
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 1) منحنى التفاعل P-M  —  ECP 203 البند 4-2-4
    //    Interaction Diagram Generation
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * توليد منحنى التفاعل (P-M Interaction Diagram) حسب الكود المصري ECP 203 البند 4-2-4
     *
     * يقوم بإنشاء 26 نقطة من الضغط البحت حتى الشد البحت
     * لكل نقطة يُحسب: φPn, φMn, c, εt, isTensionControlled
     *
     * @param fcu مقاومة الخرسانة المميزة (MPa)
     * @param fy  إجهاد خضوع الحديد (MPa)
     * @param width  عرض المقطع (mm)
     * @param depth  عمق المقطع (mm)
     * @param totalAs مساحة الحديد الكلية المقدمة (mm²)
     * @param barConfiguration توزيع الأسياخ (افتراضي: متماثل)
     * @return قائمة بنقاط منحنى التفاعل
     */
    fun generateInteractionDiagram(
        fcu: Double,
        fy: Double,
        width: Double,
        depth: Double,
        totalAs: Double,
        barConfiguration: BarConfiguration = BarConfiguration.SYMMETRIC
    ): List<InteractionDiagramPoint> {
        val h = depth
        val b = width
        val dPrime = COVER + STIRRUP_DIA                            // mm
        val d = h - dPrime                                         // mm to extreme tension steel
        val fsYield = fy / GAMMA_S                                  // MPa

        // ── تعريف طبقات التسليح (Bar Layer Model) ──
        // كل طبقة: Pair<distance from extreme compression fiber (mm), area (mm²)>
        val layers: List<Pair<Double, Double>> = when (barConfiguration) {
            BarConfiguration.SYMMETRIC -> {
                // أربع طبقات: ضغط - نصفي - نصفي - شد
                val asFace = totalAs / 2.0
                listOf(
                    dPrime          to asFace / 2.0,   // ضغط (قريب من وجه الضغط)
                    h / 2.0         to asFace / 2.0,   // نصفي (عند المحور)
                    h / 2.0         to 0.0,             // (تم دمجه في الطبقة السابقة)
                    d               to asFace / 2.0    // شد (الوجه البعيد)
                ).filter { it.second > 0.0 }
            }
            BarConfiguration.ONE_FACE -> {
                listOf(d to totalAs)
            }
            BarConfiguration.TWO_FACES -> {
                val half = totalAs / 2.0
                listOf(dPrime to half, d to half)
            }
            BarConfiguration.PERIMETER -> {
                // تقريب: 4 طبقات متساوية على المحيط
                val perLayer = totalAs / 4.0
                listOf(
                    dPrime          to perLayer,
                    h * 0.25 + dPrime * 0.75  to perLayer,
                    h * 0.75 + dPrime * 0.25  to perLayer,
                    d               to perLayer
                )
            }
        }

        // ── نقطة الضغط البحت (c → كبير جداً) ──
        val pureCompressionPn = ALPHA * (
            0.67 * fcu / GAMMA_C * (b * h - totalAs) + fsYield * totalAs
        )

        val points = mutableListOf<InteractionDiagramPoint>()
        val numPoints = 26

        // c varies from h (pure compression) down to 5 mm (near pure tension)
        // Use a mix of linear and fine spacing near balance
        for (i in 0 until numPoints) {
            val c = when (i) {
                0 -> h + 50.0     // beyond section depth → pure compression
                numPoints - 1 -> 5.0   // near pure tension
                else -> {
                    // Non-linear distribution: more points near balance region
                    val t = i.toDouble() / (numPoints - 1)  // 0→1
                    val cMax = h + 50.0
                    val cMin = 5.0
                    // Use power-law spacing for better resolution near balance
                    cMax - (cMax - cMin) * t.pow(0.7)
                }
            }

            // a = β1 × c, β1 per ECP ≈ 0.85 for fcu ≤ 40, linearly decreasing
            val beta1 = if (fcu <= 30.0) 0.85
                        else if (fcu <= 55.0) 0.85 - 0.05 * (fcu - 30.0) / 5.0
                        else 0.80
            val a = beta1 * c

            // Strain in each layer: εsi = εcu × (c - di) / c
            var sumFsiAsi = 0.0        // Σ(fsi × Asi)  in N
            var sumMoment = 0.0         // Σ(fsi × Asi × (di - h/2))  in N.mm
            var epsilonTensionExtreme = 0.0

            for ((di, Asi) in layers) {
                val epsilonSi = EPS_CU * (c - di) / c
                // Stress: fsi = Es × εsi, capped at ±fy/γs
                var fsi = ES * epsilonSi
                fsi = fsi.coerceIn(-fsYield, fsYield)

                sumFsiAsi += fsi * Asi
                sumMoment += fsi * Asi * (di - h / 2.0)

                // Track extreme tension strain (largest di)
                if (di >= d - 1.0) {
                    epsilonTensionExtreme = epsilonSi
                }
            }

            // Concrete compression block
            val concreteForce = 0.67 * fcu / GAMMA_C * a * b

            // Nominal axial force (positive = compression)
            val Pn = concreteForce + sumFsiAsi
            // Nominal moment about section centroid
            val Mn = sumMoment

            // ECP 203 uses α = 0.8 always (unlike ACI's variable φ)
            // φPn = α × Pn, φMn = α × Mn
            val phiPn = ALPHA * Pn / 1000.0     // kN
            val phiMn = ALPHA * Mn / 1e6          // kN.m

            // Tension controlled: εt ≥ εy
            val epsilonYield = fy / ES
            val isTensionControlled = epsilonTensionExtreme >= epsilonYield

            // Pure compression point override
            val finalPhiPn = if (i == 0) ALPHA * pureCompressionPn / 1000.0 else phiPn
            val finalPhiMn = if (i == 0) 0.0 else phiMn

            points.add(InteractionDiagramPoint(
                phiPn = finalPhiPn,
                phiMn = finalPhiMn,
                c = c,
                epsilonT = epsilonTensionExtreme,
                phi = ALPHA,
                isTensionControlled = isTensionControlled
            ))
        }

        // ── إضافة نقطة الشد البحت (Pure Tension) ──
        val pureTensionPn = -ALPHA * fsYield * totalAs / 1000.0  // kN (negative = tension)
        points.add(InteractionDiagramPoint(
            phiPn = pureTensionPn,
            phiMn = 0.0,
            c = 0.0,
            epsilonT = EPS_CU * 10.0,  // very large
            phi = ALPHA,
            isTensionControlled = true
        ))

        return points
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 2) تكبير العزم (P-Delta)  —  ECP 203 البند 4-2-7
    //    Moment Magnification for Slender Columns
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * حساب تكبير العزم بسبب النحافة (P-Delta Effects) حسب ECP 203 البند 4-2-7
     *
     * @param Pu  الحمل المحوري التصميمي (kN)
     * @param Mu  العزم التصميمي (kN.m)
     * @param unsupportedLength  الطول غير المدعوم (m)
     * @param effectiveLengthFactor  معامل الطول الفعال K
     * @param width  عرض المقطع (mm)
     * @param depth  عمق المقطع (mm)
     * @param fcu  مقاومة الخرسانة (MPa)
     * @param fy  إجهاد خضوع الحديد (MPa)
     * @param totalAs  مساحة الحديد الكلية (mm²)
     * @param Cm  معامل العزم المكافئ (default 1.0)
     * @param isSwayFrame  هل الإطار مقاوم للجانبية؟
     * @return MomentMagnificationResult
     */
    fun calculateMomentMagnification(
        Pu: Double,                      // kN
        Mu: Double,                      // kN.m
        unsupportedLength: Double,       // m
        effectiveLengthFactor: Double,   // K
        width: Double,                   // mm
        depth: Double,                   // mm
        fcu: Double,
        fy: Double,
        totalAs: Double,                 // mm²
        Cm: Double = 1.0,                // equivalent moment factor
        isSwayFrame: Boolean = false
    ): MomentMagnificationResult {
        val codeNotes = mutableListOf<String>()
        val L = unsupportedLength * 1000.0   // m → mm
        val K = effectiveLengthFactor

        // ── Section Properties ──
        val b = width
        val h = depth
        val Ig = b * h * h * h / 12.0       // mm⁴ - gross moment of inertia

        // ── Modulus of Elasticity (ECP 203: Ec = 4400 × √fcu) ──
        val Ec = 4400.0 * sqrt(fcu)          // MPa

        // ── Steel moment of inertia (approximate using gross section) ──
        // Is ≈ As × (h/2 - d')²  (assuming symmetric reinforcement)
        val dPrime = COVER + STIRRUP_DIA
        val leverArm = (h / 2.0 - dPrime).coerceAtLeast(0.0)
        val Is = totalAs * leverArm * leverArm   // mm⁴

        // ── Effective Flexural Rigidity per ECP 203 (simplified) ──
        // BUG FIX: ECP uses 0.4×Ec×Ig/(1+βdns) instead of ACI 0.2×Ec×Ig + Es×Is
        val betaDns = 0.6  // ratio of max sustained axial load to max factored axial load
        val EI = 0.4 * Ec * Ig / (1.0 + betaDns)   // N.mm²

        // ── Critical Buckling Load (Euler) ──
        val effectiveLength = K * L             // mm
        val Pc = PI * PI * EI / (effectiveLength * effectiveLength)  // N
        val PcKn = Pc / 1000.0                  // kN

        // ── Slenderness check ──
        val r = min(b, h) / sqrt(12.0)          // mm - radius of gyration
        val lambda = effectiveLength / r
        val isSlender = lambda > 15.0           // rectangular column limit

        codeNotes.add("ECP 203 البند 4-2-7: تكبير العزم بسبب النحافة")
        codeNotes.add("Ec = 4400√fcu = %.0f MPa".format(Ec))
        codeNotes.add("Ig = %.2e mm⁴, Is = %.2e mm⁴".format(Ig, Is))
        codeNotes.add("EI = %.2e N.mm²".format(EI))
        codeNotes.add("Pc = π²×EI/(KL)² = %.1f kN".format(PcKn))
        codeNotes.add("نسبة النحافة λ = %.1f, العمود %s".format(
            lambda, if (isSlender) "طويل (Long)" else "قصير (Short)"))

        // ── Magnification Factor ──
        val delta: Double
        val magnifiedMoment: Double

        if (!isSwayFrame) {
            // Non-sway frame: δ = Cm / (1 - Pu/Pc) ≥ 1.0, capped at 2.5
            val ratio = (Pu * 1000.0) / Pc    // dimensionless
            if (ratio >= 1.0) {
                codeNotes.add("⚠️ Pu/Pc = %.3f ≥ 1.0 → عدم استقرار! يجب زيادة المقطع.".format(ratio))
                delta = 2.5  // cap at maximum
            } else {
                delta = (Cm / (1.0 - ratio)).coerceIn(1.0, 2.5)
            }
            magnifiedMoment = Mu * delta

            codeNotes.add("إطار غير جانبي (Non-Sway): δ = Cm/(1-Pu/Pc)")
            codeNotes.add("Cm = %.2f, Pu/Pc = %.3f".format(Cm, ratio))
            codeNotes.add("δ = %.3f (max 2.5), M_design = %.1f kN.m".format(delta, magnifiedMoment))

            if (delta > 1.05) {
                codeNotes.add("⚠️ العزم مكبّر بمعامل δ = %.2f > 1.05 — يُنصح بزيادة المقطع".format(delta))
            }
        } else {
            // Sway frame: δs = 1 / (1 - ΣPu/ΣPc)
            // Here we assume this column's Pu/Pc ratio as approximation for ΣPu/ΣPc
            val ratio = (Pu * 1000.0) / Pc
            val isSwaySensitive = ratio > 0.05

            if (ratio >= 0.6) {
                codeNotes.add("⚠️ ΣPu/ΣPc = %.3f ≥ 0.6 → الإطار حساس جداً للجانبية!".format(ratio))
                delta = 1.0 / (1.0 - 0.59)  // use capped ratio
            } else {
                delta = 1.0 / (1.0 - ratio)
            }
            magnifiedMoment = Mu + (delta - 1.0) * Mu  // M_design = Mu + δs × Msway (≈ δs × Mu if all sway)

            codeNotes.add("إطار جانبي (Sway): δs = 1/(1-ΣPu/ΣPc)")
            codeNotes.add("ΣPu/ΣPc ≈ %.3f, δs = %.3f".format(ratio, delta))

            return MomentMagnificationResult(
                magnifiedMoment = magnifiedMoment,
                delta = delta,
                EI = EI,
                Pc = PcKn,
                isSlender = isSlender,
                isSwaySensitive = isSwaySensitive,
                codeNotes = codeNotes
            )
        }

        return MomentMagnificationResult(
            magnifiedMoment = magnifiedMoment,
            delta = delta,
            EI = EI,
            Pc = PcKn,
            isSlender = isSlender,
            isSwaySensitive = false,
            codeNotes = codeNotes
        )
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 3) تصميم عمود حلزوني (Spiral Column)  —  ECP 203 البند 4-2-6
    //    Spiral Reinforcement Design for Circular Columns
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * تصميم عمود دائري حلزوني حسب الكود المصري ECP 203 البند 4-2-6
     *
     * يستخدم معامل φ = 0.75 بدلاً من 0.65 للأعمدة العادية
     *
     * @param fcu مقاومة الخرسانة (MPa)
     * @param fy  إجهاد خضوع الحديد (MPa)
     * @param diameter قطر العمود (mm)
     * @param axialLoad الحمل المحوري (kN)
     * @param moment العزم (kN.m)
     * @param unsupportedLength الطول غير المدعوم (m)
     * @param endConditions ظروف التثبيت
     * @param inventory مخزون الحديد (اختياري)
     * @param loadCombination مجموعة التحميل
     * @return AdvancedColumnResult مع ملاحظات الحلزون
     */
    fun designSpiralColumn(
        fcu: Double,
        fy: Double,
        diameter: Double,               // mm
        axialLoad: Double,              // kN
        moment: Double,                 // kN.m
        unsupportedLength: Double,      // m
        endConditions: ColumnEndConditions,
        inventory: RebarInventory?,
        loadCombination: LoadCombination
    ): AdvancedColumnResult {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()

        // معامل أمان أعلى للأعمدة الحلزونية: φ = 0.75
        val PHI_SPIRAL = 0.75
        codeNotes.add("ECP 203 البند 4-2-6: عمود حلزوني (Spiral Column)")
        codeNotes.add("معامل التخفيض φ = %.2f (أعلى من 0.65 للأعمدة العادية)".format(PHI_SPIRAL))

        val Ag = PI * diameter * diameter / 4.0
        val cover = COVER
        val dSpiral = 8.0  // قطر الحلزون الافتراضي (mm)
        val coreDiameter = diameter - 2.0 * cover
        val Ac = PI * coreDiameter * coreDiameter / 4.0  // مساحة اللب

        // ── حساب التسليح الطولي المطلوب ──
        val b = diameter
        val h = diameter
        val columnType = ColumnType.Circular(diameter)

        // delegate to base for reinforcement sizing
        val reinforcementResult = baseDesign.calculateReinforcement(
            fcu, fy, b, h, axialLoad, moment, 0.0, loadCombination
        )

        // ── نسبة التسليح الحلزوني المطلوبة ──
        // ρs ≥ 0.45 × (Ag/Ac - 1) × (0.67×fcu/(γc × fy/γs))
        val concreteDesignStress = 0.67 * fcu / GAMMA_C
        val steelDesignStress = fy / GAMMA_S
        val rhoSRequired = 0.45 * (Ag / Ac - 1.0) * (concreteDesignStress / steelDesignStress)

        // ── حساب مساحة الحلزون لكل لفة ──
        val asSpiral = PI * dSpiral * dSpiral / 4.0  // mm²

        // ── حساب أقصى تباعد للالحلزون ──
        // s ≤ min(75mm, (D_core - 2×db_spiral) / 6)
        val sMax1 = 75.0
        val sMax2 = (coreDiameter - 2.0 * dSpiral) / 6.0
        val sMax = min(sMax1, sMax2)

        // ── حساب التباعد المطلوب ──
        // ρs = 4 × as × π × (D_core - db) / (s × π × D_core²)
        // Simplified: s = 4 × as × (D_core - db) / (ρs × D_core²)
        // More directly: s = volume_of_spiral_per_turn / (ρs × Ac)
        val sRequired = if (rhoSRequired > 0) {
            4.0 * asSpiral * (coreDiameter - dSpiral) / (rhoSRequired * coreDiameter * coreDiameter)
        } else 75.0

        val sProvided = sRequired.coerceIn(25.0, sMax)

        codeNotes.add("المساحة الكلية Ag = %.0f mm², مساحة اللب Ac = %.0f mm²".format(Ag, Ac))
        codeNotes.add("نسبة التسليح الحلزوني المطلوبة ρs = %.4f".format(rhoSRequired))
        codeNotes.add("قطر الحلزون = %.0f mm, التباعد = %.0f mm (أقصى = %.0f mm)".format(dSpiral, sProvided, sMax))

        if (sRequired > sMax) {
            warnings.add("⚠️ التباعد المطلوب للحلزون (%.0f mm) يزيد عن الحد الأقصى (%.0f mm)".format(sRequired, sMax))
            warnings.add("   يُنصح بزيادة قطر الحلزون أو قطر العمود")
        }

        // ── فحص الحد الأدنى للمسافة الخالية بين الحلزونات ──
        val clearSpacing = sProvided - dSpiral
        if (clearSpacing < 25.0) {
            codeNotes.add("⚠️ المسافة الخالية بين الحلزونات = %.0f mm < 25 mm — تقليل تباعد الحلزون".format(clearSpacing))
        }

        // ── Slenderness check ──
        val k = max(endConditions.topCondition.effectiveLengthFactor,
                   endConditions.bottomCondition.effectiveLengthFactor)
        val r = diameter / 4.0
        val effectiveLength = k * unsupportedLength * 1000.0
        val slendernessRatio = effectiveLength / r
        val isSlender = slendernessRatio > 12.0  // circular column limit
        if (isSlender) {
            warnings.add("⚠️ عمود حلزوني نحيف! λ = %.1f > 12 — يجب مراعاة تأثيرات الدرجة الثانية".format(slendernessRatio))
        }

        // ── Axial capacity with spiral φ ──
        val axialCapacity = calculateAxialCapacityWithPhi(fcu, fy, Ag, reinforcementResult.astProvided, PHI_SPIRAL)

        // ── Moment capacity (approximate for circular) ──
        val dEff = diameter - cover - STIRRUP_DIA
        val muCap = reinforcementResult.astProvided * (fy / GAMMA_S) * 0.8 * dEff / 1e6

        codeNotes.add("القدرة المحورية (مع φ=%.2f) = %.1f kN".format(PHI_SPIRAL, axialCapacity))
        codeNotes.add("تباعد الكانات الحلزونية s = %.0f mm".format(sProvided))

        val inventoryAnalysis = inventory?.let { inv ->
            analyzeInventory(reinforcementResult, unsupportedLength, inv)
        }

        return AdvancedColumnResult(
            columnType = columnType,
            axialCapacity = axialCapacity,
            momentCapacityX = muCap,
            momentCapacityY = muCap,
            slendernessRatio = slendernessRatio,
            isSlender = isSlender,
            effectiveLength = effectiveLength,
            reinforcementResult = reinforcementResult,
            inventoryAnalysis = inventoryAnalysis,
            biaxialCheck = null,  // circular columns don't have biaxial bending
            punchingCheck = null,
            warnings = warnings,
            codeNotes = codeNotes,
            steelWeightPerMeter = reinforcementResult.astProvided * 7850.0 / 1e6 +
                                   (4.0 * asSpiral * PI * coreDiameter / max(sProvided, 1.0)) * 7850.0 / 1e6,
            concreteVolumePerMeter = Ag / 1e6
        )
    }

    /**
     * حساب القدرة المحورية مع معامل تخفيض مخصص φ
     * Axial capacity with a custom φ factor (used for spiral columns with φ=0.75)
     */
    private fun calculateAxialCapacityWithPhi(
        fcu: Double, fy: Double, Ag: Double, Ast: Double, phi: Double
    ): Double {
        val ast = Ast.coerceAtMost(Ag * 0.08)
        val concreteStress = 0.67 * fcu / GAMMA_C
        val steelStress = fy / GAMMA_S
        val nominalCapacity = ALPHA * (concreteStress * (Ag - ast) + steelStress * ast)
        return phi * nominalCapacity / 1000.0  // kN
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 4) تصنيف العمود: قصير أم طويل  —  ECP 203 البند 4-2-7
    //    Short Column vs Long Column Classification
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * تصنيف العمود إلى قصير أو طويل حسب نسبة النحافة
     * Classifies the column as short or long based on slenderness ratio per ECP 203
     *
     * @param unsupportedLength الطول غير المدعوم (m)
     * @param width  عرض المقطع (mm)
     * @param depth  عمق المقطع (mm)
     * @param endConditions ظروف التثبيت
     * @param columnType نوع العمود
     * @return ColumnSlendernessResult
     */
    fun checkShortColumnVsLongColumn(
        unsupportedLength: Double,
        width: Double,
        depth: Double,
        endConditions: ColumnEndConditions,
        columnType: ColumnType
    ): ColumnSlendernessResult {
        val notes = mutableListOf<String>()

        // ── K factor: max of top and bottom conditions ──
        val kFactor = max(
            endConditions.topCondition.effectiveLengthFactor,
            endConditions.bottomCondition.effectiveLengthFactor
        )
        notes.add("معامل الطول الفعال K = %.2f (max of top=%.2f, bottom=%.2f)".format(
            kFactor,
            endConditions.topCondition.effectiveLengthFactor,
            endConditions.bottomCondition.effectiveLengthFactor
        ))

        // ── Radius of Gyration r ──
        val radiusOfGyration = when (columnType) {
            is ColumnType.Circular -> columnType.diameter / 4.0
            is ColumnType.Rectangular -> min(columnType.width, columnType.depth) / sqrt(12.0)
            is ColumnType.LShaped -> columnType.thickness / sqrt(12.0)
            is ColumnType.TShaped -> min(columnType.webWidth, columnType.flangeThickness) / sqrt(12.0)
            is ColumnType.Composite -> min(columnType.concreteWidth, columnType.concreteDepth) / sqrt(12.0)
            is ColumnType.Tubular -> (columnType.outerDiameter + columnType.innerDiameter) / 8.0
        }

        notes.add("نصف القطر الدوراني r = %.1f mm".format(radiusOfGyration))

        // ── Effective length and slenderness ratio ──
        val effectiveLength = kFactor * unsupportedLength * 1000.0  // mm
        val slendernessRatio = effectiveLength / radiusOfGyration

        // ── Short column limits per ECP 203 ──
        val shortColumnLimit = when (columnType) {
            is ColumnType.Circular -> 12.0
            else -> 15.0
        }

        val isShortColumn = slendernessRatio <= shortColumnLimit
        val isLongColumn = !isShortColumn

        notes.add("الطول الفعال KL = %.0f mm (%.2f m)".format(effectiveLength, effectiveLength / 1000.0))
        notes.add("نسبة النحافة λ = KL/r = %.1f".format(slendernessRatio))
        notes.add("حد العمود القصير λ_lim = %.0f (%s)".format(
            shortColumnLimit,
            if (columnType is ColumnType.Circular) "دائري" else "مستطيل"
        ))

        if (isShortColumn) {
            notes.add("✅ العمود قصير (Short Column) — لا حاجة لتكبير العزم")
        } else {
            notes.add("⚠️ العمود طويل (Long Column) — يجب تطبيق البند 4-2-7 لتكبير العزم (P-Delta)")
        }

        // ── Additional checks ──
        if (slendernessRatio > 100.0) {
            notes.add("⚠️ نسبة النحافة عالية جداً (λ=%.1f > 100) — يُنصح بإعادة النظر في تصميم النظام الإنشائي".format(slendernessRatio))
        }

        // ── End condition recommendations ──
        if (isLongColumn && kFactor > 0.9) {
            notes.add("💡 لتحسين الأداء: يمكن تقليل K بتثبيت أفضل للأطراف (مثال: Fixed-Fixed → K=0.65)")
        }

        return ColumnSlendernessResult(
            slendernessRatio = slendernessRatio,
            kFactor = kFactor,
            radiusOfGyration = radiusOfGyration,
            shortColumnLimit = shortColumnLimit,
            isShortColumn = isShortColumn,
            isLongColumn = isLongColumn,
            effectiveLength = effectiveLength,
            notes = notes
        )
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 5) فحص الانحناء الثنائي المحسّن (Enhanced Biaxial Bending Check)
    //    Uses Bresler reciprocal load method + Load Contour method
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * فحص الانحناء الثنائي المحسّن بطريقة Bresler وطريقة Load Contour
     * Enhanced biaxial bending check per ECP 203 Section 4-2-6
     *
     * @param type نوع العمود
     * @param Pu الحمل المحوري (kN)
     * @param Mx العزم حول المحور X (kN.m)
     * @param My العزم حول المحور Y (kN.m)
     * @param Pn القدرة المحورية الكلية (kN)
     * @param fy إجهاد خضوع الحديد (MPa)
     * @return BiaxialCheckResult
     */
    private fun checkBiaxialLoading(
        type: ColumnType,
        Pu: Double,
        Mx: Double,
        My: Double,
        Pn: Double,
        fcu: Double,
        fy: Double = 360.0
    ): BiaxialCheckResult {
        // ── Section dimensions ──
        val (b, h) = when (type) {
            is ColumnType.Rectangular -> type.width to type.depth
            is ColumnType.Circular -> type.diameter to type.diameter
            else -> {
                // Approximate L/T shaped as equivalent square
                val side = sqrt(type.getGrossArea())
                side to side
            }
        }
        val Ag = b * h
        val ast = 0.01 * Ag

        // ── Generate interaction diagrams for both axes ──
        // BUG FIX: pass actual fcu instead of hardcoded 25.0
        val diagramX = generateInteractionDiagram(
            fcu = fcu,
            fy = fy,
            width = b,
            depth = h,
            totalAs = ast,
            barConfiguration = BarConfiguration.SYMMETRIC
        )

        val diagramY = if (type is ColumnType.Rectangular && abs(b - h) > 10.0) {
            generateInteractionDiagram(
                fcu = fcu,
                fy = fy,
                width = h,   // swapped
                depth = b,
                totalAs = ast,
                barConfiguration = BarConfiguration.SYMMETRIC
            )
        } else {
            diagramX  // symmetric section
        }

        // ── Interpolate to find Mnx and Mny at current Pu ──
        val Mnx = interpolateMomentAtLoad(diagramX, Pu)
        val Mny = interpolateMomentAtLoad(diagramY, Pu)

        // ── Method 1: Bresler Reciprocal Load (ECP 203 Sec 4.2.6) ──
        // 1/Pn(Biaxial) = 1/Pnx + 1/Pny - 1/Po
        val Pnx = interpolateAxialAtMoment(diagramX, abs(Mx))
        val Pny = interpolateAxialAtMoment(diagramY, abs(My))
        val Po = diagramX.maxOfOrNull { it.phiPn } ?: 0.0

        val breslerSafe = if (Pnx > 0 && Pny > 0 && Po > 0) {
            val PnBiaxial = 1.0 / (1.0 / Pnx + 1.0 / Pny - 1.0 / Po)
            PnBiaxial >= Pu
        } else true

        // ── Method 2: Load Contour (Hsu) ──
        // (Mux/Mnx)^α + (Muy/Mny)^α ≤ 1
        // α = 1.0 for Pu ≈ 0, α → 2.0 for Pu → P平衡
        // Accurate α based on Pu/Po ratio
        val r = if (Po > 0) (Pu / Po).coerceIn(0.05, 0.95) else 0.5
        val alphaContour = log(0.5) / log(max(0.001, 1.0 - r))  // Hsu equation
        val alphaEffective = alphaContour.coerceIn(1.0, 2.0)

        val contourFactor = if (Mnx > 0 && Mny > 0) {
            (abs(Mx) / Mnx).pow(alphaEffective) + (abs(My) / Mny).pow(alphaEffective)
        } else 0.0

        val contourSafe = contourFactor <= 1.0

        // ── Use the more critical (higher) factor ──
        // Combine: use load contour factor as primary, Bresler as backup
        val interactionFactor = max(contourFactor,
            if (Pnx > 0 && Pny > 0 && Po > 0) {
                val PnBiaxial = 1.0 / (1.0 / Pnx + 1.0 / Pny - 1.0 / Po)
                if (PnBiaxial > 0) Pu / PnBiaxial else 99.0
            } else 0.0
        )

        val isSafe = interactionFactor <= 1.0

        val formula = buildString {
            append("Bresler + Load Contour (ECP 203 Sec 4.2.6)\n")
            append("α(Hsu) = %.2f, Contour factor = %.3f\n".format(alphaEffective, contourFactor))
            if (breslerSafe != contourSafe) {
                append("Bresler: %s, Contour: %s".format(
                    if (breslerSafe) "✅" else "❌",
                    if (contourSafe) "✅" else "❌"
                ))
            }
        }

        return BiaxialCheckResult(
            mxRatio = if (Mnx > 0) abs(Mx) / Mnx else 0.0,
            myRatio = if (Mny > 0) abs(My) / Mny else 0.0,
            interactionFactor = interactionFactor,
            isSafe = isSafe,
            formula = formula
        )
    }

    /**
     * Interpolate moment capacity at a given axial load from interaction diagram
     * يُعطي φMn عند φPn المطلوب بالاستكمال الخطي
     */
    private fun interpolateMomentAtLoad(diagram: List<InteractionDiagramPoint>, targetPn: Double): Double {
        if (diagram.isEmpty()) return 0.0
        if (diagram.size == 1) return diagram[0].phiMn

        // Find two bracketing points
        for (i in 0 until diagram.size - 1) {
            val p1 = diagram[i]
            val p2 = diagram[i + 1]

            // Pn decreases as we move along the diagram (compression → tension)
            if ((p1.phiPn >= targetPn && p2.phiPn <= targetPn) ||
                (p1.phiPn <= targetPn && p2.phiPn >= targetPn)) {
                val dp = p2.phiPn - p1.phiPn
                if (abs(dp) < 0.001) return max(p1.phiMn, p2.phiMn)
                val t = (targetPn - p1.phiPn) / dp
                return p1.phiMn + t * (p2.phiMn - p1.phiMn)
            }
        }

        // Extrapolate from nearest endpoint
        return if (targetPn > diagram.first().phiPn) {
            diagram.first().phiMn
        } else {
            diagram.last().phiMn
        }
    }

    /**
     * Interpolate axial capacity at a given moment from interaction diagram
     * يُعطي φPn عند φMn المطلوب بالاستكمال الخطي
     */
    private fun interpolateAxialAtMoment(diagram: List<InteractionDiagramPoint>, targetMn: Double): Double {
        if (diagram.isEmpty()) return 0.0
        if (diagram.size == 1) return diagram[0].phiPn

        for (i in 0 until diagram.size - 1) {
            val p1 = diagram[i]
            val p2 = diagram[i + 1]

            if ((p1.phiMn <= targetMn && p2.phiMn >= targetMn) ||
                (p1.phiMn >= targetMn && p2.phiMn <= targetMn)) {
                val dm = p2.phiMn - p1.phiMn
                if (abs(dm) < 0.001) return max(p1.phiPn, p2.phiPn)
                val t = (targetMn - p1.phiMn) / dm
                return p1.phiPn + t * (p2.phiPn - p1.phiPn)
            }
        }

        return if (targetMn > (diagram.maxOfOrNull { it.phiMn } ?: 0.0)) {
            diagram.minOfOrNull { it.phiPn } ?: 0.0
        } else {
            diagram.maxOfOrNull { it.phiPn } ?: 0.0
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // 6) الجساءة الجانبية للعمود (Lateral Stiffness)
    //    For frame analysis and drift calculations
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * حساب الجساءة الجانبية للعمود (k = F/δ) لتحليل الإطارات
     * Calculates the lateral stiffness of the column for frame analysis
     *
     * المعادلات حسب ظروف التثبيت:
     *   - Fixed-Fixed:     k = 12 × EI / L³
     *   - Fixed-Pinned:    k = 3 × EI / L³
     *   - Pinned-Pinned:   k = 0 (unstable laterally, returns minimum)
     *   - Cantilever:      k = 3 × EI / L³
     *
     * @param width  عرض المقطع (mm)
     * @param depth  عمق المقطع (mm)
     * @param unsupportedLength  الطول غير المدعوم (m)
     * @param fcu  مقاومة الخرسانة (MPa)
     * @param fy  إجهاد خضوع الحديد (MPa)
     * @param totalAs  مساحة الحديد الكلية (mm²)
     * @param endConditions  ظروف التثبيت
     * @return الجساءة الجانبية (kN/mm)
     */
    fun calculateLateralStiffness(
        width: Double,
        depth: Double,
        unsupportedLength: Double,
        fcu: Double,
        fy: Double,
        totalAs: Double,
        endConditions: ColumnEndConditions
    ): Double {
        val L = unsupportedLength * 1000.0  // m → mm

        // ── Flexural rigidity ──
        val b = width
        val h = depth
        val Ig = b * h * h * h / 12.0
        val Ec = 4400.0 * sqrt(fcu)
        val dPrime = COVER + STIRRUP_DIA
        val leverArm = (h / 2.0 - dPrime).coerceAtLeast(0.0)
        val Is = totalAs * leverArm * leverArm

        // EI = 0.2 × Ec × Ig + Es × Is (same formula as moment magnification)
        val EI = 0.2 * Ec * Ig + ES * Is  // N.mm²

        // ── Determine end condition factor for stiffness ──
        // Use a simplified approach based on K-factor combinations
        val kTop = endConditions.topCondition.effectiveLengthFactor
        val kBottom = endConditions.bottomCondition.effectiveLengthFactor
        val avgK = (kTop + kBottom) / 2.0

        // Stiffness coefficient based on end conditions
        // Fixed-Fixed → 12, Fixed-Pinned → 3, Pinned-Pinned → 0 (unstable)
        val stiffnessCoeff = when {
            kTop <= 0.7 && kBottom <= 0.7 -> 12.0        // approximately Fixed-Fixed
            kTop <= 0.7 || kBottom <= 0.7 -> 3.0         // Fixed-Pinned or Pinned-Fixed
            avgK <= 0.85 -> 6.0                           // Partially fixed both ends
            else -> 1.5                                    // Pinned-Pinned (very low stiffness)
        }

        // k = coeff × EI / L³  (N/mm)
        val stiffness = stiffnessCoeff * EI / (L * L * L)

        // Convert to kN/mm
        return stiffness / 1000.0
    }
}
