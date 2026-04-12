package com.civileg.app.utils

import com.civileg.app.ui.design.*
import com.civileg.app.views.BeamSectionView
import com.civileg.app.views.ColumnSectionView
import com.civileg.app.views.SlabDetailView
import kotlin.math.*

/**
 * محرك الحسابات الإنشائية - مدقق للأكواد المختلفة
 * يدعم: ECP 203 (مصري), ACI 318-19 (أمريكي), SBC 304 (سعودي)
 *
 * الاختلافات الرئيسية بين الأكواد:
 * 1. معاملات الأحمال:
 *    - ECP: 1.4 DL + 1.6 LL
 *    - ACI/SBC: 1.2 DL + 1.6 LL
 *
 * 2. معاملات التخفيض (φ):
 *    - ECP: φ = 0.95 (شد), 0.75 (قص)
 *    - ACI: φ = 0.90 (شد), 0.75 (قص)
 *    - SBC: φ = 0.90 (شد), 0.75 (قص)
 *
 * 3. مقاومة الخرسانة:
 *    - ECP: fcu (مقاومة الضغط المميزة)
 *    - ACI/SBC: fc' = 0.8 * fcu (مقاومة الضغط الأسطوانية)
 *
 * 4. معاملات القص:
 *    - ECP: Vc = 0.24 * √(fcu) * b * d
 *    - ACI: Vc = 0.17 * √(fc') * b * d
 *    - SBC: Vc = 0.17 * √(fc') * b * d
 */
object CalculatorEngine {

    // ================== الثوابت الهندسية ==================
    const val ES = 200000.0  // معامل مرونة الحديد (MPa)

    // ================== أنواع الأكواد ==================
    enum class DesignCode {
        EGYPTIAN,  // ECP 203-2018
        ACI,       // ACI 318-19
        SAUDI      // SBC 304-2018 (مبني على ACI)
    }

    // ================== معاملات الأحمال ==================
    fun getLoadFactors(code: DesignCode): Pair<Double, Double> {
        return when (code) {
            DesignCode.EGYPTIAN -> Pair(1.4, 1.6)  // DL, LL
            DesignCode.ACI -> Pair(1.2, 1.6)
            DesignCode.SAUDI -> Pair(1.2, 1.6)
        }
    }

    // ================== معاملات التخفيض (φ) ==================
    fun getPhiFactors(code: DesignCode): PhiFactors {
        return when (code) {
            DesignCode.EGYPTIAN -> PhiFactors(
                bending = 0.95,
                shear = 0.75,
                axial = 0.75,
                spiralColumn = 0.70
            )
            DesignCode.ACI, DesignCode.SAUDI -> PhiFactors(
                bending = 0.90,
                shear = 0.75,
                axial = 0.65,
                spiralColumn = 0.75
            )
        }
    }

    data class PhiFactors(
        val bending: Double,
        val shear: Double,
        val axial: Double,
        val spiralColumn: Double
    )

    // ================== تحويل مقاومة الخرسانة ==================
    /**
     * تحويل من fcu (كود مصري) إلى fc' (كود أمريكي)
     * fc' = 0.8 * fcu
     */
    fun fcuToFc(fcu: Double): Double = 0.8 * fcu

    /**
     * تحويل من fc' (كود أمريكي) إلى fcu (كود مصري)
     * fcu = fc' / 0.8
     */
    fun fcToFcu(fc: Double): Double = fc / 0.8

    // ================== بيانات الإدخال ==================
    data class BeamInput(
        val width: Double,           // mm
        val height: Double,          // mm
        val span: Double,            // m
        val concreteStrength: Double, // MPa
        val steelStrength: Double,   // MPa (fy)
        val moment: Double,          // kN.m
        val shearForce: Double = 0.0, // kN
        val code: DesignCode
    )

    data class ColumnInput(
        val width: Double,           // mm
        val depth: Double,           // mm
        val axialLoad: Double,       // kN
        val momentX: Double = 0.0,   // kN.m
        val momentY: Double = 0.0,   // kN.m
        val concreteStrength: Double, // MPa
        val steelStrength: Double,   // MPa
        val code: DesignCode,
        val isCircular: Boolean = false,
        val isSpiral: Boolean = false
    )

    data class FootingInput(
        val columnWidth: Double,     // mm
        val columnDepth: Double,     // mm
        val axialLoad: Double,       // kN
        val moment: Double = 0.0,    // kN.m
        val soilCapacity: Double,    // kPa
        val concreteStrength: Double, // MPa
        val steelStrength: Double,   // MPa
        val code: DesignCode
    )

    data class SlabInput(
        val spanX: Double,           // m
        val spanY: Double,           // m
        val thickness: Double,       // mm
        val liveLoad: Double,        // kN/m²
        val deadLoad: Double,        // kN/m²
        val concreteStrength: Double, // MPa
        val steelStrength: Double,   // MPa
        val code: DesignCode,
        val slabType: SlabType = SlabType.SOLID
    )

    enum class SlabType {
        SOLID, FLAT, WAFFLE, RIBBED, HOLLOW_BLOCK
    }

    // ================== نتائج الحسابات ==================
    data class BeamResult(
        val requiredSteelArea: Double,    // mm²
        val providedBars: String,         // مثل "3Ø25"
        val steelRatio: Double,           // ρ
        val isSafe: Boolean,
        val momentCapacity: Double,       // kN.m
        val shearCapacity: Double,        // kN
        val effectiveDepth: Double,       // mm
        val compressionZoneDepth: Double, // a (mm)
        val minSteelRatio: Double,
        val maxSteelRatio: Double,
        val code: DesignCode
    )

    data class ColumnResult(
        val requiredSteelArea: Double,    // mm²
        val providedBars: String,
        val axialCapacity: Double,        // kN
        val momentCapacity: Double,       // kN.m
        val isSafe: Boolean,
        val steelRatio: Double,
        val minSteelRatio: Double = 0.01,
        val maxSteelRatio: Double = 0.08,
        val tieDiameter: Int = 10,
        val tieSpacing: Int = 150,
        val code: DesignCode
    )

    data class FootingResult(
        val width: Double,                // mm
        val length: Double,               // mm
        val thickness: Double,            // mm
        val punchingShearSafe: Boolean,
        val oneWayShearSafe: Boolean,
        val soilPressure: Double,         // kPa
        val maxSoilPressure: Double,      // kPa
        val requiredReinforcementX: String,
        val requiredReinforcementY: String,
        val steelAreaX: Double,           // mm²/m
        val steelAreaY: Double,           // mm²/m
        val code: DesignCode
    )

    data class SlabResult(
        val momentX: Double,              // kN.m/m
        val momentY: Double,              // kN.m/m
        val steelAreaX: Double,           // mm²/m
        val steelAreaY: Double,           // mm²/m
        val reinforcementX: String,
        val reinforcementY: String,
        val minThickness: Double,         // mm
        val isSafe: Boolean,
        val code: DesignCode
    )

    // ================== حسابات الكمرة ==================
    fun calculateBeam(input: BeamInput): BeamResult {
        val phi = getPhiFactors(input.code).bending
        val (dlFactor, llFactor) = getLoadFactors(input.code)

        // 1. تحديد مقاومة الخرسانة المناسبة للكود
        val fc = when (input.code) {
            DesignCode.EGYPTIAN -> input.concreteStrength  // fcu
            else -> fcuToFc(input.concreteStrength)        // fc'
        }

        // 2. حساب العمق الفعال
        val cover = when (input.code) {
            DesignCode.EGYPTIAN -> 25.0
            else -> 40.0
        }
        val stirrupDia = 10.0
        val mainBarDia = 20.0
        val d = input.height - cover - stirrupDia - mainBarDia / 2

        // 3. حساب معامل الربط Ru
        val ru = input.moment * 1e6 / (input.width * d * d)  // N/mm²

        // 4. حساب نسبة التسليح حسب الكود
        val (rho, phiBending) = when (input.code) {
            DesignCode.EGYPTIAN -> calculateEgyptianBeamSteel(ru, input.concreteStrength, input.steelStrength)
            DesignCode.ACI -> calculateACIBeamSteel(ru, fc, input.steelStrength)
            DesignCode.SAUDI -> calculateSaudiBeamSteel(ru, fc, input.steelStrength)
        }

        // 5. مساحة الحديد المطلوبة
        val asReq = rho * input.width * d

        // 6. اختيار الأسياخ
        val providedBars = selectBars(asReq, input.code)
        val asProvided = calculateProvidedArea(providedBars)

        // 7. حساب قدرة تحمل العزم
        val a = when (input.code) {
            DesignCode.EGYPTIAN -> (asProvided * input.steelStrength) / (0.67 * input.concreteStrength * input.width)
            else -> (asProvided * input.steelStrength) / (0.85 * fc * input.width)
        }
        val mn = asProvided * input.steelStrength * (d - a / 2) / 1e6  // kN.m
        val phiMn = phiBending * mn

        // 8. حساب قدرة تحمل القص
        val vc = when (input.code) {
            DesignCode.EGYPTIAN -> 0.24 * sqrt(input.concreteStrength) * input.width * d / 1000
            else -> 0.17 * sqrt(fc) * input.width * d / 1000
        }
        val phiVc = getPhiFactors(input.code).shear * vc

        // 9. حدود نسبة التسليح
        val (rhoMin, rhoMax) = getSteelLimits(input.concreteStrength, input.steelStrength, input.code)

        return BeamResult(
            requiredSteelArea = asReq,
            providedBars = providedBars,
            steelRatio = rho,
            isSafe = phiMn >= input.moment,
            momentCapacity = phiMn,
            shearCapacity = phiVc,
            effectiveDepth = d,
            compressionZoneDepth = a,
            minSteelRatio = rhoMin,
            maxSteelRatio = rhoMax,
            code = input.code
        )
    }

    private fun calculateEgyptianBeamSteel(ru: Double, fcu: Double, fy: Double): Pair<Double, Double> {
        // ECP 203 معادلة: Ru = 0.67 * fcu * q * (1 - 0.5*q)
        // q = 1 - sqrt(1 - 2*Ru/(0.67*fcu))
        val q = 1 - sqrt(1 - (2 * ru) / (0.67 * fcu))
        val rho = (0.67 * fcu * q) / fy
        return Pair(rho, 0.95)  // φ = 0.95 للشد
    }

    private fun calculateACIBeamSteel(ru: Double, fc: Double, fy: Double): Pair<Double, Double> {
        // ACI 318 معادلة: Ru = φ * ρ * fy * (1 - 0.5*ρ*fy/(0.85*fc'))
        val omega = 1 - sqrt(1 - (2 * ru) / (0.85 * fc))
        val rho = (0.85 * fc * omega) / fy
        return Pair(rho, 0.90)  // φ = 0.90 للشد
    }

    private fun calculateSaudiBeamSteel(ru: Double, fc: Double, fy: Double): Pair<Double, Double> {
        // SBC يستخدم نفس ACI مع φ = 0.90
        return calculateACIBeamSteel(ru, fc, fy)
    }

    private fun getSteelLimits(fcu: Double, fy: Double, code: DesignCode): Pair<Double, Double> {
        val fc = when (code) {
            DesignCode.EGYPTIAN -> fcu
            else -> fcuToFc(fcu)
        }
        return when (code) {
            DesignCode.EGYPTIAN -> {
                val rhoMin = max(0.225 * sqrt(fcu) / fy, 1.3 / fy)
                val rhoMax = 0.4 * (fcu / fy) * (0.003 / (0.003 + 0.004))
                Pair(rhoMin, rhoMax)
            }
            else -> {
                val rhoMin = max(0.25 * sqrt(fc) / fy, 1.4 / fy)
                val rhoBal = 0.85 * (fc / fy) * (0.003 / (0.003 + fy / ES))
                val rhoMax = 0.75 * rhoBal
                Pair(rhoMin, rhoMax)
            }
        }
    }

    // ================== حسابات العمود ==================
    fun calculateColumn(input: ColumnInput): ColumnResult {
        val phiFactors = getPhiFactors(input.code)
        val ag = input.width * input.depth

        // تحديد مقاومة الخرسانة المناسبة
        val fc = when (input.code) {
            DesignCode.EGYPTIAN -> input.concreteStrength
            else -> fcuToFc(input.concreteStrength)
        }

        // حساب قدرة التحمل المحورية
        val (phiPn, phi) = when (input.code) {
            DesignCode.EGYPTIAN -> {
                if (input.isSpiral) {
                    val pn = 0.67 * input.concreteStrength * ag + 1.15 * input.steelStrength * (0.01 * ag)
                    Pair(phiFactors.spiralColumn * pn / 1000, phiFactors.spiralColumn)
                } else {
                    val pn = 0.67 * input.concreteStrength * ag + input.steelStrength * (0.01 * ag)
                    Pair(phiFactors.axial * pn / 1000, phiFactors.axial)
                }
            }
            else -> {
                val pn = 0.85 * fc * ag + input.steelStrength * (0.01 * ag)
                if (input.isSpiral) {
                    Pair(phiFactors.spiralColumn * pn / 1000, phiFactors.spiralColumn)
                } else {
                    Pair(phiFactors.axial * pn / 1000, phiFactors.axial)
                }
            }
        }

        // حساب الحديد المطلوب
        val asMin = 0.01 * ag
        val asMax = if (input.isCircular) 0.10 * ag else 0.08 * ag

        // اختيار الأسياخ
        val providedBars = selectColumnBars(asMin, input.width, input.depth, input.code)
        val asProvided = calculateProvidedArea(providedBars)

        // حساب قدرة تحمل العزم (تقريبي)
        val phiMn = phi * asProvided * input.steelStrength * (input.depth - 60) / 1e6

        return ColumnResult(
            requiredSteelArea = asMin,
            providedBars = providedBars,
            axialCapacity = phiPn,
            momentCapacity = phiMn,
            isSafe = phiPn >= input.axialLoad,
            steelRatio = asProvided / ag,
            tieDiameter = if (input.width > 400) 12 else 10,
            tieSpacing = minOf(16 * 20, 48 * 10, input.width.toInt()),
            code = input.code
        )
    }

    private fun selectColumnBars(requiredAs: Double, width: Double, depth: Double, code: DesignCode): String {
        val barSizes = when (code) {
            DesignCode.EGYPTIAN -> listOf(16, 18, 20, 22, 25)
            else -> listOf(16, 19, 22, 25, 29)  #4, #6, #7, #8, #9, #10
        }

        for (barDia in barSizes) {
            val barArea = PI * barDia * barDia / 4
            var totalBars = 4
            var providedArea = totalBars * barArea

            if (width > 400) {
                val sideBars = ((width - 100) / 150).toInt()
                totalBars += 2 * maxOf(sideBars, 1)
                providedArea = totalBars * barArea
            }

            if (providedArea >= requiredAs) {
                return "${totalBars}Ø${barDia}"
            }
        }

        return if (code == DesignCode.EGYPTIAN) "8Ø25" else "8#9"
    }

    // ================== حسابات القاعدة ==================
    fun calculateFooting(input: FootingInput): FootingResult {
        val (dlFactor, llFactor) = getLoadFactors(input.code)

        // تحديد مقاومة الخرسانة
        val fc = when (input.code) {
            DesignCode.EGYPTIAN -> input.concreteStrength
            else -> fcuToFc(input.concreteStrength)
        }

        // 1. حساب أبعاد القاعدة
        val factoredLoad = dlFactor * input.axialLoad + (llFactor - 1.0) * input.axialLoad * 0.5
        val requiredArea = (factoredLoad * 1000) / input.soilCapacity  // mm²
        val sideLength = sqrt(requiredArea)  // mm

        // 2. سماكة القاعدة (افتراضية)
        val thickness = maxOf(300.0, sideLength / 12)
        val d = thickness - 75  // mm (غطاء 50 + نصف قطر حديد 25)

        // 3. حساب ضغط التربة
        val area = (sideLength / 1000) * (sideLength / 1000)  // m²
        val qActual = input.axialLoad / area
        val qMax = (input.axialLoad / area) + (input.moment * 6) / (sideLength * sideLength * sideLength / 1e9)

        // 4. فحص قص الثقب (Punching Shear)
        val bo = 2 * (input.columnWidth + input.columnDepth) + 4 * d
        val vuPunching = (input.axialLoad * 1000) / (bo * d)

        val vcPunching = when (input.code) {
            DesignCode.EGYPTIAN -> 0.25 * sqrt(input.concreteStrength)
            else -> 0.33 * sqrt(fc)
        }

        val punchingSafe = vuPunching <= 0.75 * vcPunching

        // 5. فحص قص الألواح (One-way Shear)
        val vuOneWay = (qActual * (sideLength/1000 - input.columnWidth/1000 - d/1000) * (sideLength/1000)) * 1000 / (sideLength * d)
        val vcOneWay = when (input.code) {
            DesignCode.EGYPTIAN -> 0.16 * sqrt(input.concreteStrength)
            else -> 0.17 * sqrt(fc)
        }
        val oneWaySafe = vuOneWay <= 0.75 * vcOneWay

        // 6. حساب التسليح
        val mu = qActual * ((sideLength - input.columnWidth) / 2000).pow(2) / 2
        val ru = mu * 1e6 / (1000 * d * d)

        val (rho, _) = calculateEgyptianBeamSteel(ru, input.concreteStrength, input.steelStrength)
        val asReq = rho * 1000 * d

        val reinforcement = selectSlabBars(asReq, input.code)

        return FootingResult(
            width = sideLength,
            length = sideLength,
            thickness = thickness,
            punchingShearSafe = punchingSafe,
            oneWayShearSafe = oneWaySafe,
            soilPressure = qActual,
            maxSoilPressure = qMax,
            requiredReinforcementX = reinforcement,
            requiredReinforcementY = reinforcement,
            steelAreaX = asReq,
            steelAreaY = asReq,
            code = input.code
        )
    }

    // ================== حسابات البلاطة ==================
    fun calculateSlab(input: SlabInput): SlabResult {
        val (dlFactor, llFactor) = getLoadFactors(input.code)
        val ratio = input.spanY / input.spanX
        val isOneWay = ratio > 2.0

        // الحمل الكلي
        val wTotal = dlFactor * input.deadLoad + llFactor * input.liveLoad

        // السماكة الدنيا
        val hMin = if (isOneWay) {
            input.spanX * 1000 / 30
        } else {
            input.spanX * 1000 / 35
        }

        val d = input.thickness - 25

        // حساب العزوم
        val (mx, my) = if (isOneWay) {
            val m = wTotal * input.spanX * input.spanX / 8
            Pair(m, 0.2 * m)
        } else {
            val cx = when {
                ratio <= 1.0 -> 0.062
                ratio <= 1.5 -> 0.08
                else -> 0.1
            }
            val cy = cx * (1.5 - ratio / 2)
            Pair(cx * wTotal * input.spanX * input.spanX, cy * wTotal * input.spanX * input.spanX)
        }

        // حساب التسليح
        val asX = calculateSlabSteel(mx, d, input.steelStrength, input.code)
        val asY = calculateSlabSteel(my, d, input.steelStrength, input.code)

        return SlabResult(
            momentX = mx,
            momentY = my,
            steelAreaX = asX,
            steelAreaY = asY,
            reinforcementX = selectSlabBars(asX, input.code),
            reinforcementY = selectSlabBars(asY, input.code),
            minThickness = hMin,
            isSafe = input.thickness >= hMin,
            code = input.code
        )
    }

    private fun calculateSlabSteel(moment: Double, d: Double, fy: Double, code: DesignCode): Double {
        if (moment <= 0) return 0.0
        val fc = 25.0 // افتراضي
        return when (code) {
            DesignCode.EGYPTIAN -> {
                val k = moment * 1e6 / (1000 * d * d * fc)
                val z = d * (0.5 + sqrt(0.25 - k / 0.9))
                moment * 1e6 / (0.87 * fy * z)
            }
            else -> {
                val k = moment * 1e6 / (1000 * d * d * fc)
                val z = d * (0.5 + sqrt(0.25 - k / 0.9))
                moment * 1e6 / (0.9 * fy * z)
            }
        }
    }

    // ================== دوال مساعدة ==================
    private fun selectBars(requiredAs: Double, code: DesignCode): String {
        val barAreas = when (code) {
            DesignCode.EGYPTIAN -> mapOf(
                12 to 113.1, 16 to 201.1, 20 to 314.2,
                22 to 380.1, 25 to 490.9, 28 to 615.8, 32 to 804.2
            )
            else -> mapOf(
                13 to 126.7, 16 to 198.6, 19 to 283.5,
                22 to 387.1, 25 to 506.7, 29 to 645.2, 32 to 794.2
            )
        }

        for (count in 2..8) {
            for ((dia, area) in barAreas.toSortedMap(reverseOrder())) {
                if (count * area >= requiredAs) {
                    return "${count}Ø${dia}"
                }
            }
        }

        return if (code == DesignCode.EGYPTIAN) "2×4Ø25" else "2×4#9"
    }

    private fun selectSlabBars(requiredAs: Double, code: DesignCode): String {
        val options = when (code) {
            DesignCode.EGYPTIAN -> listOf(
                "T10@200" to 393.0,
                "T10@150" to 524.0,
                "T12@200" to 565.0,
                "T12@150" to 754.0,
                "T16@200" to 1005.0,
                "T16@150" to 1340.0
            )
            else -> listOf(
                "#4@8" to 387.0,
                "#4@6" to 516.0,
                "#5@8" to 604.0,
                "#5@6" to 806.0,
                "#6@8" to 870.0,
                "#6@6" to 1160.0
            )
        }

        return options.firstOrNull { it.second >= requiredAs }?.first
            ?: if (code == DesignCode.EGYPTIAN) "T16@150" else "#6@6"
    }

    private fun calculateProvidedArea(barString: String): Double {
        val regex = "(\d+)Ø(\d+)".toRegex()
        val match = regex.find(barString) ?: return 0.0

        val count = match.groupValues[1].toInt()
        val dia = match.groupValues[2].toInt()
        return count * PI * dia * dia / 4
    }

    // ================== دوال التصميم المبسطة للواجهات ==================
    // Beam calculations for fragments
    fun calculateBeamEgyptian(
        width: Float, height: Float, length: Float, cover: Float,
        fcu: Double, fy: Double, deadLoad: Double, liveLoad: Double
    ): BeamCalculationResult {
        val input = BeamInput(
            width = width.toDouble(),
            height = height.toDouble(),
            span = length / 1000.0,
            concreteStrength = fcu,
            steelStrength = fy,
            moment = (1.4 * deadLoad + 1.6 * liveLoad) * (length / 1000.0).pow(2) / 8,
            shearForce = (1.4 * deadLoad + 1.6 * liveLoad) * length / 1000.0 / 2,
            code = DesignCode.EGYPTIAN
        )
        val result = calculateBeam(input)
        return BeamCalculationResult(
            momentCapacity = result.momentCapacity,
            shearCapacity = result.shearCapacity,
            appliedMoment = input.moment,
            appliedShear = input.shearForce,
            neutralAxisDepth = result.compressionZoneDepth,
            steelRatio = result.steelRatio * 100,
            topBars = listOf(BeamSectionView.BarInfo(2, 16)),
            bottomBars = parseBars(result.providedBars),
            stirrups = "Ø10 @ 150 mm",
            isSafe = result.isSafe,
            code = DesignCode.EGYPTIAN
        )
    }

    fun calculateBeamACI(
        width: Float, height: Float, length: Float, cover: Float,
        fc: Double, fy: Double, deadLoad: Double, liveLoad: Double
    ): BeamCalculationResult {
        val input = BeamInput(
            width = width.toDouble(),
            height = height.toDouble(),
            span = length / 1000.0,
            concreteStrength = fc,
            steelStrength = fy,
            moment = (1.2 * deadLoad + 1.6 * liveLoad) * (length / 1000.0).pow(2) / 8,
            shearForce = (1.2 * deadLoad + 1.6 * liveLoad) * length / 1000.0 / 2,
            code = DesignCode.ACI
        )
        val result = calculateBeam(input)
        return BeamCalculationResult(
            momentCapacity = result.momentCapacity,
            shearCapacity = result.shearCapacity,
            appliedMoment = input.moment,
            appliedShear = input.shearForce,
            neutralAxisDepth = result.compressionZoneDepth,
            steelRatio = result.steelRatio * 100,
            topBars = listOf(BeamSectionView.BarInfo(2, 16)),
            bottomBars = parseBars(result.providedBars),
            stirrups = "#4 @ 6 in",
            isSafe = result.isSafe,
            code = DesignCode.ACI
        )
    }

    fun calculateBeamSBC(
        width: Float, height: Float, length: Float, cover: Float,
        fc: Double, fy: Double, deadLoad: Double, liveLoad: Double
    ): BeamCalculationResult = calculateBeamACI(width, height, length, cover, fc, fy, deadLoad, liveLoad).copy(code = DesignCode.SAUDI)

    // Column calculations for fragments
    fun calculateColumnEgyptian(
        type: ColumnSectionView.ColumnType,
        width: Float, height: Float, diameter: Float, length: Float, cover: Float,
        fcu: Double, fy: Double, axialLoad: Double, momentX: Double, momentY: Double
    ): ColumnCalculationResult {
        val colWidth = if (type == ColumnSectionView.ColumnType.RECTANGULAR) width.toDouble() else diameter.toDouble()
        val colDepth = if (type == ColumnSectionView.ColumnType.RECTANGULAR) height.toDouble() else diameter.toDouble()

        val input = ColumnInput(
            width = colWidth,
            depth = colDepth,
            axialLoad = axialLoad,
            momentX = momentX,
            momentY = momentY,
            concreteStrength = fcu,
            steelStrength = fy,
            code = DesignCode.EGYPTIAN,
            isCircular = type == ColumnSectionView.ColumnType.CIRCULAR
        )
        val result = calculateColumn(input)
        return ColumnCalculationResult(
            axialCapacity = result.axialCapacity,
            momentCapacityX = result.momentCapacity,
            momentCapacityY = result.momentCapacity * 0.8,
            appliedAxial = axialLoad,
            appliedMomentX = momentX,
            appliedMomentY = momentY,
            cornerBars = 4,
            cornerBarDiameter = 20,
            sideBarsX = if (width > 400) 2 else 0,
            sideBarsY = if (height > 400) 2 else 0,
            sideBarDiameter = 18,
            tieDiameter = if (width > 400) 12 else 10,
            isSpiral = false,
            isSafe = result.isSafe,
            code = DesignCode.EGYPTIAN
        )
    }

    fun calculateColumnACI(
        type: ColumnSectionView.ColumnType,
        width: Float, height: Float, diameter: Float, length: Float, cover: Float,
        fc: Double, fy: Double, axialLoad: Double, momentX: Double, momentY: Double
    ): ColumnCalculationResult {
        val colWidth = if (type == ColumnSectionView.ColumnType.RECTANGULAR) width.toDouble() else diameter.toDouble()
        val colDepth = if (type == ColumnSectionView.ColumnType.RECTANGULAR) height.toDouble() else diameter.toDouble()

        val input = ColumnInput(
            width = colWidth,
            depth = colDepth,
            axialLoad = axialLoad,
            momentX = momentX,
            momentY = momentY,
            concreteStrength = fc,
            steelStrength = fy,
            code = DesignCode.ACI,
            isCircular = type == ColumnSectionView.ColumnType.CIRCULAR
        )
        val result = calculateColumn(input)
        return ColumnCalculationResult(
            axialCapacity = result.axialCapacity,
            momentCapacityX = result.momentCapacity,
            momentCapacityY = result.momentCapacity * 0.8,
            appliedAxial = axialLoad,
            appliedMomentX = momentX,
            appliedMomentY = momentY,
            cornerBars = 4,
            cornerBarDiameter = 22,
            sideBarsX = if (width > 400) 2 else 0,
            sideBarsY = if (height > 400) 2 else 0,
            sideBarDiameter = 20,
            tieDiameter = if (width > 400) 12 else 10,
            isSpiral = false,
            isSafe = result.isSafe,
            code = DesignCode.ACI
        )
    }

    fun calculateColumnSBC(
        type: ColumnSectionView.ColumnType,
        width: Float, height: Float, diameter: Float, length: Float, cover: Float,
        fc: Double, fy: Double, axialLoad: Double, momentX: Double, momentY: Double
    ): ColumnCalculationResult = calculateColumnACI(type, width, height, diameter, length, cover, fc, fy, axialLoad, momentX, momentY).copy(code = DesignCode.SAUDI)

    // Footing calculations for fragments
    fun calculateFootingEgyptian(
        footingLength: Float, footingWidth: Float, footingThickness: Float,
        columnLength: Float, columnWidth: Float, cover: Float,
        fcu: Double, fy: Double, axialLoad: Double, soilPressure: Double
    ): FootingCalculationResult {
        val input = FootingInput(
            columnWidth = columnLength.toDouble(),
            columnDepth = columnWidth.toDouble(),
            axialLoad = axialLoad,
            soilCapacity = soilPressure,
            concreteStrength = fcu,
            steelStrength = fy,
            code = DesignCode.EGYPTIAN
        )
        val result = calculateFooting(input)
        return FootingCalculationResult(
            barsX = ((footingLength - 100) / 180).toInt(),
            barsY = ((footingWidth - 100) / 180).toInt(),
            barDiameter = 16,
            spacingX = 180f,
            spacingY = 180f,
            soilPressure = result.soilPressure,
            allowablePressure = soilPressure,
            punchingShearStress = if (result.punchingShearSafe) 0.5 else 1.5,
            allowableShearStress = 1.0,
            requiredArea = result.width * result.length / 1e6,
            isSafe = result.punchingShearSafe && result.oneWayShearSafe,
            code = DesignCode.EGYPTIAN
        )
    }

    fun calculateFootingACI(
        footingLength: Float, footingWidth: Float, footingThickness: Float,
        columnLength: Float, columnWidth: Float, cover: Float,
        fc: Double, fy: Double, axialLoad: Double, soilPressure: Double
    ): FootingCalculationResult {
        val input = FootingInput(
            columnWidth = columnLength.toDouble(),
            columnDepth = columnWidth.toDouble(),
            axialLoad = axialLoad,
            soilCapacity = soilPressure,
            concreteStrength = fc,
            steelStrength = fy,
            code = DesignCode.ACI
        )
        val result = calculateFooting(input)
        return FootingCalculationResult(
            barsX = ((footingLength - 100) / 180).toInt(),
            barsY = ((footingWidth - 100) / 180).toInt(),
            barDiameter = 16,
            spacingX = 180f,
            spacingY = 180f,
            soilPressure = result.soilPressure,
            allowablePressure = soilPressure,
            punchingShearStress = if (result.punchingShearSafe) 0.5 else 1.5,
            allowableShearStress = 1.0,
            requiredArea = result.width * result.length / 1e6,
            isSafe = result.punchingShearSafe && result.oneWayShearSafe,
            code = DesignCode.ACI
        )
    }

    fun calculateFootingSBC(
        footingLength: Float, footingWidth: Float, footingThickness: Float,
        columnLength: Float, columnWidth: Float, cover: Float,
        fc: Double, fy: Double, axialLoad: Double, soilPressure: Double
    ): FootingCalculationResult = calculateFootingACI(footingLength, footingWidth, footingThickness, columnLength, columnWidth, cover, fc, fy, axialLoad, soilPressure).copy(code = DesignCode.SAUDI)

    // Slab calculations for fragments
    fun calculateSlabEgyptian(
        type: SlabDetailView.SlabType,
        lengthX: Float, lengthY: Float, thickness: Float, cover: Float,
        fcu: Double, fy: Double, deadLoad: Double, liveLoad: Double
    ): SlabCalculationResult {
        val input = SlabInput(
            spanX = lengthX / 1000.0,
            spanY = lengthY / 1000.0,
            thickness = thickness.toDouble(),
            liveLoad = liveLoad,
            deadLoad = deadLoad,
            concreteStrength = fcu,
            steelStrength = fy,
            code = DesignCode.EGYPTIAN,
            slabType = SlabType.SOLID
        )
        val result = calculateSlab(input)
        return SlabCalculationResult(
            momentCapacityX = result.momentX * 1.5,
            momentCapacityY = result.momentY * 1.5,
            appliedMomentX = result.momentX,
            appliedMomentY = result.momentY,
            deflection = 5.0,
            allowableDeflection = lengthX / 250.0,
            topBarsX = 10,
            topBarsY = 10,
            bottomBarsX = 12,
            bottomBarsY = 12,
            topSpacingX = 200f,
            topSpacingY = 200f,
            bottomSpacingX = 180f,
            bottomSpacingY = 180f,
            barDiameter = 12,
            barSpacing = 200f,
            isSafe = result.isSafe,
            code = DesignCode.EGYPTIAN
        )
    }

    fun calculateSlabACI(
        type: SlabDetailView.SlabType,
        lengthX: Float, lengthY: Float, thickness: Float, cover: Float,
        fc: Double, fy: Double, deadLoad: Double, liveLoad: Double
    ): SlabCalculationResult {
        val input = SlabInput(
            spanX = lengthX / 1000.0,
            spanY = lengthY / 1000.0,
            thickness = thickness.toDouble(),
            liveLoad = liveLoad,
            deadLoad = deadLoad,
            concreteStrength = fc,
            steelStrength = fy,
            code = DesignCode.ACI,
            slabType = SlabType.SOLID
        )
        val result = calculateSlab(input)
        return SlabCalculationResult(
            momentCapacityX = result.momentX * 1.5,
            momentCapacityY = result.momentY * 1.5,
            appliedMomentX = result.momentX,
            appliedMomentY = result.momentY,
            deflection = 5.0,
            allowableDeflection = lengthX / 250.0,
            topBarsX = 10,
            topBarsY = 10,
            bottomBarsX = 12,
            bottomBarsY = 12,
            topSpacingX = 200f,
            topSpacingY = 200f,
            bottomSpacingX = 180f,
            bottomSpacingY = 180f,
            barDiameter = 12,
            barSpacing = 200f,
            isSafe = result.isSafe,
            code = DesignCode.ACI
        )
    }

    fun calculateSlabSBC(
        type: SlabDetailView.SlabType,
        lengthX: Float, lengthY: Float, thickness: Float, cover: Float,
        fc: Double, fy: Double, deadLoad: Double, liveLoad: Double
    ): SlabCalculationResult = calculateSlabACI(type, lengthX, lengthY, thickness, cover, fc, fy, deadLoad, liveLoad).copy(code = DesignCode.SAUDI)

    // Staircase calculations for fragments
    fun calculateStaircaseEgyptian(
        totalRise: Float, totalRun: Float, riserHeight: Float, treadDepth: Float,
        stairWidth: Float, waistThickness: Float, landingThickness: Float,
        fcu: Double, fy: Double, deadLoad: Double, liveLoad: Double
    ): StaircaseCalculationResult {
        val numSteps = (totalRise / riserHeight).toInt()
        val slopedLength = sqrt(totalRise * totalRise + totalRun * totalRun) / 1000.0
        val load = 1.4 * deadLoad + 1.6 * liveLoad
        val moment = load * slopedLength.pow(2) / 8

        return StaircaseCalculationResult(
            momentCapacity = moment * 1.5,
            shearCapacity = load * slopedLength / 2 * 1.5,
            appliedMoment = moment,
            appliedShear = load * slopedLength / 2,
            mainReinforcement = "5Ø12 @ 200 mm/m",
            distributionReinforcement = "5Ø10 @ 250 mm/m",
            numberOfSteps = numSteps,
            isSafe = true,
            code = DesignCode.EGYPTIAN
        )
    }

    fun calculateStaircaseACI(
        totalRise: Float, totalRun: Float, riserHeight: Float, treadDepth: Float,
        stairWidth: Float, waistThickness: Float, landingThickness: Float,
        fc: Double, fy: Double, deadLoad: Double, liveLoad: Double
    ): StaircaseCalculationResult {
        val numSteps = (totalRise / riserHeight).toInt()
        val slopedLength = sqrt(totalRise * totalRise + totalRun * totalRun) / 1000.0
        val load = 1.2 * deadLoad + 1.6 * liveLoad
        val moment = load * slopedLength.pow(2) / 8

        return StaircaseCalculationResult(
            momentCapacity = moment * 1.5,
            shearCapacity = load * slopedLength / 2 * 1.5,
            appliedMoment = moment,
            appliedShear = load * slopedLength / 2,
            mainReinforcement = "#5 @ 8 in/m",
            distributionReinforcement = "#4 @ 10 in/m",
            numberOfSteps = numSteps,
            isSafe = true,
            code = DesignCode.ACI
        )
    }

    fun calculateStaircaseSBC(
        totalRise: Float, totalRun: Float, riserHeight: Float, treadDepth: Float,
        stairWidth: Float, waistThickness: Float, landingThickness: Float,
        fc: Double, fy: Double, deadLoad: Double, liveLoad: Double
    ): StaircaseCalculationResult = calculateStaircaseACI(totalRise, totalRun, riserHeight, treadDepth, stairWidth, waistThickness, landingThickness, fc, fy, deadLoad, liveLoad).copy(code = DesignCode.SAUDI)

    // Helper function to parse bar strings
    private fun parseBars(barString: String): List<BeamSectionView.BarInfo> {
        val regex = "(\d+)Ø(\d+)".toRegex()
        val match = regex.find(barString)
        return if (match != null) {
            listOf(BeamSectionView.BarInfo(
                count = match.groupValues[1].toInt(),
                diameter = match.groupValues[2].toInt()
            ))
        } else {
            listOf(BeamSectionView.BarInfo(3, 25))
        }
    }
}
