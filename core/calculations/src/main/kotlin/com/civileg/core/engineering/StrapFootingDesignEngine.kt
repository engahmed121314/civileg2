package com.civileg.core.engineering

import kotlin.math.*

/**
 * محرك تصميم القواعد الشريطية (Strap / Cantilever Footing) — Unified Engine
 *
 * يُستخدم عندما تكون القاعدة الخارجية عند حد الجار (property line) وتنتقل محصّلتها
 * عبر الكاميرا الرابطة (Strap Beam) إلى القاعدة الداخلية.
 *
 * المنهجية المعتمدة (طريقة الضغط المنتظم / resultant alignment):
 *  1. تحديد موقع محصلة الأحمال، ثم تحقيق توازن العزوم حول العمود الداخلي لإنتاج
 *     ردَّي فعل منتظمَي التوزيع تحت كل قاعدة.
 *  2. تصميم أبعاد كل قاعدة على ضغط التربة المسموح (service loads).
 *  3. تحديد سماكة القواعد من قص الاختراق (punching shear) عند العمود.
 *  4. تصميم تسليح الانحناء للقواعد عند وجه العمود.
 *  5. تصميم الكاميرا الرابطة للحمل الحيوي (العزم الناتج من لا-مركزية رد الفعل + القص).
 *
 * الوحدات: mm للأبعاد، m للبحور، kN/kN.m، kPa (kN/m²)، MPa، mm².
 *
 * ملاحظة: الأحمال المُدخلة تُعتبر أحمال خدمة (service) لتقدير مساحات القواعد، ويُطبَّق
 * معامل تحميل واحد محافظ `γ = max(γD, γL)` لتصميم القطاعات الخرسانية.
 */
object StrapFootingDesignEngine {

    data class Inputs(
        val column1Load: Double,             // kN (External/Edge column)
        val column2Load: Double,             // kN (Internal column)
        val distanceBetweenColumns: Double,  // mm (Center to center)
        val column1Width: Double,            // mm
        val column1Depth: Double,            // mm
        val column2Width: Double,            // mm
        val column2Depth: Double,            // mm
        val soilBearingCapacity: Double,     // kPa (kN/m²)
        val fcu: Double,                     // MPa
        val fy: Double,                      // MPa
        val strapBeamWidth: Double = 400.0,  // mm
        val deadLoadFactor: Double = 1.4,
        val liveLoadFactor: Double = 1.6
    )

    data class ReinforcementResult(
        val numberOfBars: Int,
        val barDiameter: Double,
        val isSafe: Boolean = true,
        val areaRequired: Double = 0.0,   // mm²
        val areaProvided: Double = 0.0,   // mm²
        val spacing: Double = 0.0         // mm
    )

    data class FootingDimension(
        val width: Double,       // mm
        val length: Double,      // mm
        val thickness: Double,   // mm
        val reinforcement: ReinforcementResult
    )

    data class StrapBeamResult(
        val width: Double,       // mm
        val depth: Double,       // mm
        val topReinforcement: ReinforcementResult,
        val bottomReinforcement: ReinforcementResult,
        val maxMoment: Double,   // kN.m
        val maxShear: Double,    // kN
        val stirrupSpacing: Double = 0.0,
        val stirrupDiameter: Double = 8.0
    )

    data class Result(
        val footing1: FootingDimension,
        val footing2: FootingDimension,
        val strapBeam: StrapBeamResult,
        val reactions: Pair<Double, Double>, // R1, R2 (kN)
        val isSafe: Boolean,
        val warnings: List<String> = emptyList(),
        val codeNotes: List<String> = emptyList(),
        // ── تفاصيل الفحوصات (تُستخدم لبناء safetyChecks في الواجهة) ──
        val soilPressure1: Double = 0.0,        // kPa
        val soilPressure2: Double = 0.0,        // kPa
        val allowableSoilPressure: Double = 0.0, // kPa
        val punchingShear1: Double = 0.0,       // kN
        val punchingCapacity1: Double = 0.0,    // kN
        val punchingShear2: Double = 0.0,       // kN
        val punchingCapacity2: Double = 0.0,    // kN
        val strapMoment: Double = 0.0,          // kN.m
        val strapShear: Double = 0.0,           // kN
        val strapAsRequired: Double = 0.0,      // mm²
        val strapAsProvided: Double = 0.0,      // mm²
        val strapShearCapacity: Double = 0.0,   // kN
        val calculationSteps: List<String> = emptyList()
    )

    private const val PI = Math.PI
    private const val PHI_FLEXURE = 0.90
    private const val COVER = 60.0 // mm (foundations exposed to earth)

    private fun barArea(dia: Double): Double = PI / 4.0 * dia * dia
    private fun roundUp(value: Double, step: Double): Double = ceil(value / step) * step
    private fun roundDown(value: Double, step: Double): Double = floor(value / step) * step

    /** تصميم تسليح الانحناء لشريط عرضه b (mm) وعمقه الفعّال d (mm). */
    private fun flexureSteel(mu: Double, b: Double, d: Double, h: Double, fy: Double, fcu: Double): Double {
        val asMin = 0.0015 * b * h
        if (mu <= 0.0) return asMin
        val rn = mu * 1e6 / (PHI_FLEXURE * b * d * d) // MPa
        val term = 2.0 * rn / (0.85 * fcu)
        if (term >= 1.0) return b * d * 0.04 // above balanced: caller will flag via isSafe
        val rho = (0.85 * fcu / fy) * (1.0 - sqrt(1.0 - term))
        return max(rho * b * d, asMin)
    }

    /** يختار عدد وقطر الأسياخ لتغطية مساحة الحديد المطلوبة على عرض معيّن. */
    private fun chooseBars(
        asRequired: Double, widthMm: Double, dia: Double,
        minSpacing: Double, maxSpacing: Double
    ): ReinforcementResult {
        val total = asRequired * widthMm / 1000.0 // As مطلوب على العرض الكامل
        var n = ceil(total / barArea(dia)).toInt()
        n = max(n, ceil(widthMm / maxSpacing).toInt())
        n = max(n, 2)
        val spacing = widthMm / n
        if (spacing < minSpacing) {
            n = ceil(widthMm / minSpacing).toInt()
        }
        val effSpacing = widthMm / n
        val provided = n * barArea(dia)
        return ReinforcementResult(
            numberOfBars = n,
            barDiameter = dia,
            isSafe = provided >= total,
            areaRequired = total,
            areaProvided = provided,
            spacing = effSpacing
        )
    }

    /** السماكة المطلوبة من قص الاختراق (punching) عند العمود. */
    private fun requiredThickness(
        pu: Double, colB: Double, colT: Double, qu: Double, fcu: Double
    ): Double {
        var d = 150.0
        while (d <= 2000.0) {
            val perimeter = 2.0 * ((colB + d) + (colT + d)) / 1000.0 // m
            val vu = pu - qu * ((colB + d) * (colT + d)) / 1e6       // kN
            val vc = 0.33 * sqrt(fcu) * perimeter * d / 1000.0       // kN
            if (vc >= vu) break
            d += 10.0
        }
        return roundUp(d + COVER, 25.0).coerceAtLeast(300.0)
    }

    fun design(inputs: Inputs): Result {
        val warnings = mutableListOf<String>()
        val codeNotes = mutableListOf<String>()
        val steps = mutableListOf<String>()

        val p1 = inputs.column1Load
        val p2 = inputs.column2Load
        val qAllow = inputs.soilBearingCapacity
        val fcu = inputs.fcu
        val fy = inputs.fy
        val loadFactor = max(inputs.deadLoadFactor, inputs.liveLoadFactor)

        // ── 1. الهندسة ──
        val dcc = inputs.distanceBetweenColumns / 1000.0     // m, c-c
        val x1 = inputs.column1Depth / 2000.0                // m, property line → col1 center
        val x2 = x1 + dcc                                    // m, property line → col2 center
        val col1Half = inputs.column1Depth / 2000.0
        val proj1 = max(0.60, x1)
        val l1 = roundUp(max(x1 + col1Half + proj1, 1.50), 0.05) // m, footing 1 length
        val c1 = l1 / 2.0                                    // m, footing 1 centroid

        // ── 2. ردود الفعل (توازن العزوم حول العمود الداخلي) ──
        val denom = x2 - c1
        var r1: Double
        if (denom > 0.10) {
            r1 = p1 * dcc / denom
        } else {
            warnings.add("تباعد الأعمدة صغير جدًا مقارنة بأبعاد القاعدة الخارجية — تحقق من المدخلات.")
            r1 = p1 * 3.0
        }
        if (r1 <= 0.0 || !r1.isFinite()) {
            warnings.add("ردّ فعل القاعدة الخارجية غير منطقي — تم فرض قيمة حدّية.")
            r1 = p1
        }
        var r2 = (p1 + p2) - r1
        if (r2 <= 0.0) {
            warnings.add("ردّ فعل القاعدة الداخلية سالب — راجع أبعاد القاعدة الخارجية.")
            r2 = kotlin.math.max(p2, 1.0)
            r1 = (p1 + p2) - r2
        }

        steps.add("R1 = P1·L/(x2−c1) = ${"%.1f".format(p1)}·${"%.2f".format(dcc)}/(${"%.2f".format(x2)}−${"%.2f".format(c1)}) = ${"%.1f".format(r1)} kN")
        steps.add("R2 = (P1+P2) − R1 = ${"%.1f".format(p1 + p2)} − ${"%.1f".format(r1)} = ${"%.1f".format(r2)} kN")

        // ── 3. أبعاد القواعد (service) ──
        val a1 = roundUp(max(r1 / qAllow / l1, 1.00), 0.05)   // m width
        val area1 = l1 * a1
        val q1 = r1 / area1                                  // kPa

        val a2 = roundUp(max(sqrt(r2 / qAllow) * 1.10, 1.00), 0.05)
        val b2 = a2
        val area2 = a2 * b2
        val q2 = r2 / area2                                  // kPa

        steps.add("Footing 1: A = R1/q = ${"%.1f".format(r1)}/${"%.1f".format(qAllow)} = ${"%.2f".format(r1 / qAllow)} m² → ${"%.2f".format(l1)}×${"%.2f".format(a1)} m (q = ${"%.1f".format(q1)} kPa)")
        steps.add("Footing 2: A = R2/q = ${"%.2f".format(r2 / qAllow)} m² → ${"%.2f".format(a2)}×${"%.2f".format(b2)} m (q = ${"%.1f".format(q2)} kPa)")

        // ── 4. السماكة من قص الاختراق ──
        val pu1 = p1 * loadFactor
        val pu2 = p2 * loadFactor
        val qu1 = pu1 / area1
        val qu2 = pu2 / area2
        val h1 = requiredThickness(pu1, inputs.column1Width, inputs.column1Depth, qu1, fcu)
        val h2 = requiredThickness(pu2, inputs.column2Width, inputs.column2Depth, qu2, fcu)
        val d1 = h1 - COVER
        val d2 = h2 - COVER

        steps.add("Thickness 1 (punching) = ${"%.0f".format(h1)} mm ; Thickness 2 = ${"%.0f".format(h2)} mm")

        // ── 5. فحص الاختراق الفعلي ──
        val punch1 = punching(pu1, inputs.column1Width, inputs.column1Depth, h1, qu1, fcu)
        val punch2 = punching(pu2, inputs.column2Width, inputs.column2Depth, h2, qu2, fcu)

        // ── 6. تسليح القواعد (انحناء عند وجه العمود) ──
        val pOut1 = max(x1 - col1Half, 0.05)
        val pIn1 = max(l1 - x1 - col1Half, 0.05)
        val mu1 = qu1 * max(pOut1, pIn1).pow(2) / 2.0          // kN.m/m
        val as1 = flexureSteel(mu1, 1000.0, d1, h1, fy, fcu)
        val bar1 = chooseBars(as1, a1 * 1000.0, 16.0, 100.0, 200.0)

        val p2Proj = max((a2 - inputs.column2Depth / 1000.0) / 2.0, 0.05)
        val mu2 = qu2 * p2Proj.pow(2) / 2.0
        val as2 = flexureSteel(mu2, 1000.0, d2, h2, fy, fcu)
        val bar2 = chooseBars(as2, b2 * 1000.0, 16.0, 100.0, 200.0)

        steps.add("As footing 1 = ${"%.0f".format(as1)} mm²/m → ${bar1.numberOfBars}Ø${bar1.barDiameter.toInt()}@${"%.0f".format(bar1.spacing)}")
        steps.add("As footing 2 = ${"%.0f".format(as2)} mm²/m → ${bar2.numberOfBars}Ø${bar2.barDiameter.toInt()}@${"%.0f".format(bar2.spacing)}")

        // ── 7. تصميم الكاميرا الرابطة ──
        val e1 = abs(c1 - x1)
        val ms = r1 * e1 * loadFactor            // kN.m
        val vs = abs(p1 - r1) * loadFactor       // kN
        val strapW = inputs.strapBeamWidth
        val strapDepth = 1000.0
        val ds = strapDepth - 75.0
        val asStrap = flexureSteel(ms, strapW, ds, strapDepth, fy, fcu)
        val strapTop = chooseBars(asStrap, strapW, 20.0, 60.0, 200.0)
        val asStrapBottom = max(0.2 * asStrap, 0.0015 * strapW * strapDepth)
        val strapBottom = chooseBars(asStrapBottom, strapW, 16.0, 60.0, 200.0)

        val vcStrap = 0.17 * sqrt(fcu) * strapW * ds / 1000.0 // kN
        val stirrupDia = 8.0
        val stirrupSpacing: Double = if (vs > vcStrap) {
            val sReq = 0.87 * fy * 2.0 * barArea(stirrupDia) * ds / ((vs - vcStrap) * 1000.0)
            roundDown(min(sReq, 200.0), 25.0).coerceAtLeast(75.0)
        } else 200.0

        steps.add("Strap: M = R1·|c1−x1|·γ = ${"%.1f".format(r1)}×${"%.2f".format(e1)}×${"%.1f".format(loadFactor)} = ${"%.1f".format(ms)} kN.m")
        steps.add("Strap: V = |P1−R1|·γ = ${"%.1f".format(vs)} kN ; Asₜ = ${"%.0f".format(asStrap)} mm² → ${strapTop.numberOfBars}Ø${strapTop.barDiameter.toInt()}")
        steps.add("Strap stirrups: Ø${stirrupDia.toInt()}@${"%.0f".format(stirrupSpacing)} mm")

        // ── 8. التقييم ──
        val bearingOk = q1 <= qAllow * 1.001 && q2 <= qAllow * 1.001
        val punchingOk = punch1.first <= punch1.second && punch2.first <= punch2.second
        val flexureOk = bar1.isSafe && bar2.isSafe && strapTop.isSafe && strapBottom.isSafe
        val shearOk = vs <= vcStrap || stirrupSpacing < 200.0
        val isSafe = bearingOk && punchingOk && flexureOk && shearOk

        if (!bearingOk) warnings.add("ضغط التربة يتجاوز المسموح — زد أبعاد القواعد.")
        if (!punchingOk) warnings.add("قص الاختراق يتجاوز المقاومة — زد سماكة القاعدة.")
        if (!flexureOk) warnings.add("تسليح الانحناء غير كافٍ — زد التسليح أو العمق.")

        codeNotes.add("Strap Footing — Unified Engine (uniform soil pressure method)")
        codeNotes.add("ردود الفعل: R1 = ${"%.1f".format(r1)} kN, R2 = ${"%.1f".format(r2)} kN")

        return Result(
            footing1 = FootingDimension(
                width = a1 * 1000.0, length = l1 * 1000.0, thickness = h1, reinforcement = bar1
            ),
            footing2 = FootingDimension(
                width = b2 * 1000.0, length = a2 * 1000.0, thickness = h2, reinforcement = bar2
            ),
            strapBeam = StrapBeamResult(
                width = strapW, depth = strapDepth,
                topReinforcement = strapTop, bottomReinforcement = strapBottom,
                maxMoment = ms, maxShear = vs,
                stirrupSpacing = stirrupSpacing, stirrupDiameter = stirrupDia
            ),
            reactions = r1 to r2,
            isSafe = isSafe,
            warnings = warnings,
            codeNotes = codeNotes,
            soilPressure1 = q1,
            soilPressure2 = q2,
            allowableSoilPressure = qAllow,
            punchingShear1 = punch1.first,
            punchingCapacity1 = punch1.second,
            punchingShear2 = punch2.first,
            punchingCapacity2 = punch2.second,
            strapMoment = ms,
            strapShear = vs,
            strapAsRequired = asStrap,
            strapAsProvided = strapTop.areaProvided,
            strapShearCapacity = vcStrap,
            calculationSteps = steps
        )
    }

    private fun punching(
        pu: Double, colB: Double, colT: Double, h: Double, qu: Double, fcu: Double
    ): Pair<Double, Double> {
        val d = h - COVER
        val perimeter = 2.0 * ((colB + d) + (colT + d)) / 1000.0 // m
        val vu = pu - qu * ((colB + d) * (colT + d)) / 1e6       // kN
        val vc = 0.33 * sqrt(fcu) * perimeter * d / 1000.0       // kN
        return vu to vc
    }
}
