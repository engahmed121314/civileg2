package com.civileg.app.domain.calculations.ecp

import com.civileg.app.domain.calculations.InputGuard
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import kotlin.math.*

/**
 * تصميم السلالم حسب الكود المصري ECP 203-2025
 *
 * المراجع (تم التحقق منها على نسخة ممسوحة من ECP 203-2025):
 * - الانحناء: ECP 203-2025 البند 4-2-1-2 وجدول (4-1) بدون إعادة توزيع (مطبوع 4-6/4-7)
 * - القص: ECP 203-2025 معادلة 4-27 (مطبوع 4-18) ومعادلة 4-29 (مطبوع 4-19)
 *   والبند 4-2-2-2-1 (مطبوع 4-25): البلاطات والقواعد تقاوم القص بالخرسانة فقط
 * - الترخيم: ECP 203-2025 البند 4-3 وجدول 4-10 (مطبوع 4-65) ومعادلة 4-81
 * - السمك الأدنى للبلاطات: معادلة 6-6a (مطبوع 6-7): tmin = a/35 بسيطة الارتكاز
 * - تسليح البلاطات ذات الاتجاه الواحد: البند 6-2-1-3-2 (مطبوع 6-5..6-6)
 * - الغطاء الخرساني: جدول 21-2 كربنة (مطبوع 2-32): 25 مم عناصر داخلية محمية
 * - الأحمال: ECP 201-2012 (حمل حي للسلالم 4 kN/m²)
 *
 * السلم يُ Modellled كـ بلاطة باتجاه واحد (شريحة بعرض 1م)
 * بدعم بسيط على كلا الطرفين، مع حديد علوي عند الركائز حسب معادلة 6-2
 */
class ECPStaircase : StaircaseDesign {

    companion object {
        private const val GAMMA_C = 1.5       // معامل أمان الخرسانة
        private const val GAMMA_S = 1.15      // معامل أمان الحديد
        private const val GAMMA_G = 1.4       // معامل حمل الميت
        private const val GAMMA_Q = 1.6       // معامل حمل الحي
        private const val COVER_INTERIOR = 25.0  // mm - ECP 203-2025 جدول 21-2 (كربنة، عناصر داخلية محمية من الأمطار، مطبوع 2-32)
        private const val COVER_EXTERIOR = 40.0  // mm - جدول 21-2 (دورات بلل وجفاف، مطبوع 2-32) — غير مستعمل: ليس مدخلاً
        const val MIN_WAIST = 120.0     // mm - أقل سماكة عملية للبلاطة (افتراض عملي، ليس بند كود)
        private const val MAX_RISER = 180.0     // mm - أقصى ارتفاع درجة
        private const val MIN_GOING = 250.0     // mm - أقل عرض درجة
        private const val COMFORT_2R_PLUS_G_MIN = 550.0  // mm
        private const val COMFORT_2R_PLUS_G_MAX = 700.0  // mm
        private const val E_S = 200000.0       // MPa - معامل مرونة الحديد
    }

    override fun designStaircase(input: StaircaseInput): StaircaseResult {
        InputGuard.positive("fcu", input.fcu)
        InputGuard.positive("fy", input.fy)
        InputGuard.positive("totalRise", input.totalRise)
        InputGuard.positive("span", input.span)
        InputGuard.positive("deadLoad", input.deadLoad)
        InputGuard.positive("liveLoad", input.liveLoad)
        InputGuard.positive("stairWidth", input.stairWidth)

        val safetyChecks = mutableListOf<StairSafetyCheck>()
        val codeNotes = mutableListOf<String>()

        // Landing/restraint qualification: a landing
        // establishes restraint only when ALL its dimensions are supplied and
        // finite-positive; null, zero, negative or NaN length/width/thickness
        // never qualify, and dimensions alone do not establish restraint
        // without a declared CONTINUOUS_OVER_LANDING support.
        fun finitePositive(v: Double?): Boolean = v != null && v.isFinite() && v > 0.0
        val landingSupplied = finitePositive(input.landingLengthMm) &&
            finitePositive(input.landingWidthMm) && finitePositive(input.landingThicknessMm)
        val continuityDeclared = input.stairType == StairType.DOG_LEG &&
            input.supportCondition == StairSupportCondition.CONTINUOUS_OVER_LANDING && landingSupplied
        // No landing restraint is credited: no located clause in the ECP 203-2025 scan
        // (contents pages list no stair/landing heading) grounds a reduced positive-moment
        // coefficient or an L/d for a flight over a landing, and declared continuity plus
        // landing dimensions does not establish it. Kept as a named switch so a grounded
        // clause can enable it with its own tests.
        val continuousOverLanding = false

        // ========== 1. التصميم الهندسي ==========
        var numRisers: Int
        var numTreads: Int
        var riserMm: Double
        var goingMm: Double

        if (input.riserCount > 0 && input.going > 0) {
            // المستخدم حدد كل شيء
            numRisers = input.riserCount
            numTreads = numRisers - 1
            riserMm = input.totalRise * 1000.0 / numRisers
            goingMm = input.going
        } else if (input.riserCount > 0) {
            // Riser count given, going not given: the going follows the entered span so the
            // adopted run equals the span. No silent clamp into a comfort range — the
            // geometry checks below flag an out-of-range going instead.
            numRisers = input.riserCount
            numTreads = numRisers - 1
            riserMm = input.totalRise * 1000.0 / numRisers
            goingMm = input.span * 1000.0 / numTreads
        } else if (input.going > 0) {
            goingMm = input.going
            val treadsEstimate = (input.span * 1000.0 / goingMm).toInt().coerceIn(3, 40)
            numTreads = treadsEstimate
            numRisers = numTreads + 1
            riserMm = input.totalRise * 1000.0 / numRisers
            // تحسين ليناسب معادلة الراحة
            val targetRiser = (620.0 - goingMm) / 2.0
            val adjustedRisers = round(input.totalRise * 1000.0 / targetRiser).toInt().coerceIn(5, 40)
            if (abs(adjustedRisers - numRisers) > 1) {
                numRisers = adjustedRisers
                numTreads = numRisers - 1
                riserMm = input.totalRise * 1000.0 / numRisers
            }
        } else {
            // حساب تلقائي كامل - أوجد أفضل تقسيم
            var bestRiser = 170.0
            var bestDiff = Double.MAX_VALUE
            for (n in 5..40) {
                val r = input.totalRise * 1000.0 / n
                val t = n - 1
                val g = if (t > 0) input.span * 1000.0 / t else 300.0
                if (r <= MAX_RISER && g >= MIN_GOING) {
                    val comfort = 2 * r + g
                    val diff = abs(comfort - 625.0)
                    if (diff < bestDiff) { bestDiff = diff; bestRiser = r }
                }
            }
            bestRiser = bestRiser.coerceIn(140.0, MAX_RISER)
            numRisers = round(input.totalRise * 1000.0 / bestRiser).toInt().coerceIn(5, 40)
            numTreads = numRisers - 1
            riserMm = input.totalRise * 1000.0 / numRisers
            // Going follows the entered span (no silent clamp): out-of-range values are
            // flagged by the "Going ≥ 250mm" and comfort checks below.
            goingMm = input.span * 1000.0 / numTreads
        }

        val inclinedLength = sqrt(input.span * input.span + input.totalRise * input.totalRise)
        val slopeAngle = Math.toDegrees(atan(input.totalRise / input.span))
        val cosTheta = cos(Math.toRadians(slopeAngle))

        codeNotes.add("Geometry: ${numRisers} risers × ${numTreads} treads")
        codeNotes.add("R = ${String.format("%.1f", riserMm)} mm, G = ${String.format("%.1f", goingMm)} mm")
        codeNotes.add("2R+G = ${String.format("%.0f", 2 * riserMm + goingMm)} mm")
        codeNotes.add("Slope angle θ = ${String.format("%.1f", slopeAngle)}°")

        // ── بنود الكود الحاكمة للسلم (بلاطة مائلة أحادية الاتجاه) ──
        codeNotes.add(CodeReference.ECP.STAIRCASE_FLEXURE)
        codeNotes.add(CodeReference.ECP.STAIRCASE_SHEAR)
        codeNotes.add(CodeReference.ECP.STAIRCASE_DEFLECTION)

        // The discrete adopted run (treads × going) can differ from the entered span when the
        // user supplies the going or the tread count. The analysis span is the LARGER of the
        // two so the numbers never analyze less than the geometry the drawing shows.
        val adoptedRunMm = numTreads * goingMm
        val adoptedRunM = adoptedRunMm / 1000.0
        val analysisSpan = max(input.span, adoptedRunM)
        if (abs(adoptedRunM - input.span) > 0.005) {
            codeNotes.add("Adopted discrete run ${"%.0f".format(adoptedRunMm)} mm (${numTreads} treads × ${"%.1f".format(goingMm)} mm) differs from the entered span ${"%.0f".format(input.span * 1000)} mm; analysis uses the larger, ${"%.3f".format(analysisSpan)} m")
        }

        // ========== 2. سماكة البلاطة ==========
        // No silent clamp: the supplied thickness is analysed as given; below the minimum the
        // "Waist Thickness" check fails. A non-positive thickness is rejected with a field-specific message.
        require(input.waistThickness.isFinite() && input.waistThickness > 0.0) {
            "waistThickness must be a positive thickness in mm (was ${input.waistThickness})"
        }
        val waistThickness = input.waistThickness
        // Cover 25 mm: ECP 203-2025 Table 21-2 (carbonation, printed 2-32 / scan PDF 60) —
        // elements inside buildings protected from rain, moderate humidity. Exterior
        // wet/dry exposure needs 40 mm; exposure is not an app input.
        val cover = COVER_INTERIOR

        safetyChecks.add(StairSafetyCheck(
            name = "Waist Thickness",
            value = waistThickness, limit = MIN_WAIST, unit = "mm",
            isSafe = waistThickness >= MIN_WAIST,
            description = "Practical minimum waist 120 mm (app assumption, not a code clause); code thickness demand comes from the Table 4-10 span/thickness check and Eq. 6-6a below"
        ))

        // Span/thickness is assessed once, in section 7, against ECP 203-2025 Table 4-10.

        // ========== 3. الأحمال ==========
        // w_horiz = 1.4×dead/cos(θ) + 1.6×live (مُسقط أفقياً)
        // ECP 203 §2-3-1: Load combination factors γg=1.4, γq=1.6
        val cosThetaSafe = cosTheta.coerceAtLeast(0.1)  // Prevent division by near-zero cos(θ)
        val horizontalLoad = GAMMA_G * input.deadLoad / cosThetaSafe + GAMMA_Q * input.liveLoad
        val factoredOnSlope = horizontalLoad * cosTheta  // kN/m² on slope

        codeNotes.add("Factored horizontal load = ${String.format("%.2f", horizontalLoad)} kN/m²")
        codeNotes.add("Factored load on slope = ${String.format("%.2f", factoredOnSlope)} kN/m²")

        // ========== 4. التحليل الإنشائي ==========
        // Explicit case analysis model — the coefficient and support contract
        // must travel with the result so a named UI case can never masquerade
        // as a verified design.
        val span = analysisSpan
        val caseSupported: Boolean
        val analysisStatus: StairAnalysisStatus
        when (input.stairType) {
            StairType.STRAIGHT -> {
                caseSupported = true
                if (input.supportCondition == StairSupportCondition.SIMPLY_SUPPORTED) {
                    analysisStatus = StairAnalysisStatus.CASE_MODEL_IMPLEMENTED
                    codeNotes.add("Case model: single flight — one-way inclined slab strip (1 m), simply supported, M = wL²/8, V = wL/2 on the horizontal projection")
                } else {
                    // Declared support not modeled (UNSPECIFIED or continuity): the
                    // wL²/8 numbers are conservative, not a verified model of the
                    // declared support.
                    analysisStatus = StairAnalysisStatus.CONSERVATIVE_FALLBACK_RESTRAINT_UNVERIFIED
                    codeNotes.add("Case model: single flight analyzed simply supported (M = wL²/8); declared support ${input.supportCondition} is not modeled — conservative result, restraint unverified")
                }
            }
            StairType.DOG_LEG -> {
                caseSupported = true
                if (continuousOverLanding) {
                    analysisStatus = StairAnalysisStatus.CASE_MODEL_IMPLEMENTED
                    codeNotes.add("Case model: double flight — M = wL²/10 per flight, continuity over the supplied landing (${input.landingLengthMm?.toInt()} mm × ${input.landingThicknessMm?.toInt()} mm) restrains the flight (analysis-model adoption, not an ECP equation)")
                } else {
                    // No assumed restraint: support/landing UNSPECIFIED, or
                    // continuity declared without usable landing dimensions →
                    // conservative simply-supported per-flight model.
                    analysisStatus = StairAnalysisStatus.CONSERVATIVE_FALLBACK_RESTRAINT_UNVERIFIED
                    if (continuityDeclared) {
                        codeNotes.add("Declared CONTINUOUS_OVER_LANDING with landing dimensions is NOT credited: no grounded restraint coefficient or L/d for this case; per-flight M = wL²/8 and L/d 20 used as an unverified preliminary model. The landing slab itself is not designed")
                    } else if (input.supportCondition == StairSupportCondition.CONTINUOUS_OVER_LANDING) {
                        codeNotes.add("WARNING: CONTINUOUS_OVER_LANDING declared but landing dimensions are missing or not finite-positive (L/W/t) — restraint NOT verified; per-flight M = wL²/8 used as an unverified preliminary model")
                    } else {
                        codeNotes.add("Case model: double flight with UNSPECIFIED support/landing — no restraint assumed; conservative simply-supported per-flight M = wL²/8.")
                    }
                }
                codeNotes.add("The landing slab itself is NOT designed by this engine (landingRebar empty)")
            }
            else -> {
                // OPEN_WELL / SPIRAL: no three-flight, open-well or radial/torsion
                // model is implemented. Falling back to wL²/8 would present an
                // unverified design as if it covered the named case — refuse.
                caseSupported = false
                analysisStatus = StairAnalysisStatus.UNSUPPORTED_CASE
                codeNotes.add("WARNING: no implemented analysis model for ${input.stairType} (three-flight/open-well/radial-torsion not implemented). Numbers below follow a single-flight wL²/8 fallback and are NOT a verified design for this case")
            }
        }
        safetyChecks.add(StairSafetyCheck(
            name = "Case model support",
            value = if (caseSupported) 1.0 else 0.0, limit = 1.0, unit = "",
            isSafe = analysisStatus == StairAnalysisStatus.CASE_MODEL_IMPLEMENTED,
            notAssessed = analysisStatus != StairAnalysisStatus.CASE_MODEL_IMPLEMENTED,
            description = if (caseSupported) "Implemented analysis model for this stair case" else "No implemented analysis model for ${input.stairType} — result is not a verified design for this case"
        ))
        val momentCoefficient = if (continuousOverLanding) 1.0 / 10.0 else 1.0 / 8.0
        val adjustedMoment = horizontalLoad * span * span * momentCoefficient
        val adjustedShear = horizontalLoad * span / 2.0
        val reactionA = adjustedShear
        val reactionB = adjustedShear

        codeNotes.add("Design moment M = ${String.format("%.2f", adjustedMoment)} kN.m/m")
        codeNotes.add("Design shear V = ${String.format("%.2f", adjustedShear)} kN/m")
        codeNotes.add("Design is per 1 m strip; quantities and distribution steel scale with actual stair width W = ${input.stairWidth} m")

        // ========== 5. تصميم الانحناء (K-method) ==========
        val b = 1000.0
        val Mu = adjustedMoment * 1e6

        // ECP 203-2025 §4-2-1-2, Table 4-1 (no redistribution, printed 4-6/4-7, scan
        // PDF 86-87): the ductility cap is Rmax by steel grade with
        // Mumax = Rmax·fcu·b·d²/γc (Eq. 4-4) — i.e. K = Mu/(fcu·b·d²) ≤ Rmax/γc.
        // The previous "K_bal" (a/d = 0.9·εcu/(εcu+εy)) allowed K up to ~0.180 for
        // fy=420 where Table 4-1 caps K at 0.186/1.5 = 0.124 — about 45% unconservative.
        val rMax = rMaxNoRedistribution(input.fy)
        val kMax = rMax / GAMMA_C

        val fs = input.fy / GAMMA_S

        // Effective depth: d = h − cover − main-bar radius. First pass assumes a 12 mm
        // main bar; if a larger bar is selected the section is recomputed once with the
        // actual radius so the depth matches the issued reinforcement.
        var mainBarRadiusMm = 6.0
        var d = waistThickness - cover - mainBarRadiusMm
        require(d > 0.0) {
            "waistThickness ${"%.0f".format(waistThickness)} mm leaves no effective depth after cover and bar allowance"
        }

        val K_DIVISOR = 2.0 * 0.67 / GAMMA_C   // z = d·(0.5 + √(0.25 − K/K_DIVISOR)) from Mu = (0.67·fcu/γc)·b·a·(d − a/2)
        val minSteelRatio = minMainSteelRatioSlab(input.fcu, input.fy)

        var K = 0.0
        var leverArm = 0.0
        var astRequired = 0.0
        var minSteelArea = 0.0
        var mainRebar = ""
        var mainRebarArea = 0.0
        var refined = false
        while (true) {
            K = Mu / (input.fcu * b * d * d)
            InputGuard.finite("K", K)
            leverArm = if (0.25 - K / K_DIVISOR > 0) {
                d * (0.5 + sqrt(0.25 - K / K_DIVISOR))
            } else {
                // K beyond the singly-reinforced quadratic root: the flexure check below
                // fails; a nominal lever arm keeps the reported As finite and honest.
                d * 0.7
            }
            val astFlexure = if (fs > 0 && leverArm > 0) Mu / (fs * leverArm) else 0.0
            minSteelArea = minSteelRatio * b * d
            astRequired = max(astFlexure, minSteelArea)
            mainRebar = selectBars(astRequired)
            mainRebarArea = parseBarArea(mainRebar)
            val selectedRadius = barDiameterOf(mainRebar) / 2.0
            if (!refined && selectedRadius > mainBarRadiusMm) {
                refined = true
                mainBarRadiusMm = selectedRadius
                d = waistThickness - cover - mainBarRadiusMm
                require(d > 0.0) {
                    "waistThickness ${"%.0f".format(waistThickness)} mm leaves no effective depth after cover and the selected main bar"
                }
            } else {
                break
            }
        }
        val manualMain = input.manualMainDia != null && input.manualMainSpacing != null &&
            input.manualMainDia > 0 && input.manualMainSpacing > 0.0
        if (manualMain) {
            val md = input.manualMainDia!!
            val ms = input.manualMainSpacing!!
            mainRebar = "Ø$md @ ${ms.toInt()} mm c/c"
            mainRebarArea = PI * md * md / 4.0 * 1000.0 / ms
            codeNotes.add("Manual bottom steel entered by the user: $mainRebar (${"%.0f".format(mainRebarArea)} mm²/m); the checks below run on the entered bars. Effective depth is kept from the auto pass.")
        }
        val dEffective = d
        if (astRequired == minSteelArea) {
            codeNotes.add("Minimum steel governs: ${String.format("%.0f", minSteelArea)} mm²/m (ECP 203-2025 §6-2-1-3-2 item 1, printed 6-6: ratio ≥ max(0.6/fy, class floor) of b×d)")
        }

        codeNotes.add("K = ${String.format("%.4f", K)}, K_max = Rmax/γc = ${String.format("%.4f", kMax)} (Rmax = ${String.format("%.3f", rMax)} for fy = ${"%.0f".format(input.fy)}, ECP 203-2025 Table 4-1, no redistribution)")
        codeNotes.add("Effective depth d = ${"%.0f".format(dEffective)} mm (h − 25 mm cover − ${"%.0f".format(mainBarRadiusMm)} mm bar radius; cover per Table 21-2 interior, printed 2-32)")

        safetyChecks.add(StairSafetyCheck(
            name = "Flexure (K ≤ Rmax/γc, Table 4-1)",
            value = K, limit = kMax, unit = "",
            isSafe = K <= kMax,
            description = if (K <= kMax) {
                "ECP 203-2025 §4-2-1-2, Table 4-1 (no redistribution, printed 4-6/4-7): K = ${"%.4f".format(K)} ≤ Rmax/γc = ${"%.4f".format(kMax)} (Rmax = ${"%.3f".format(rMax)} for fy = ${"%.0f".format(input.fy)}; Eq. 4-4 Mumax = Rmax·fcu·b·d²/γc)"
            } else {
                "K = ${"%.4f".format(K)} exceeds Rmax/γc = ${"%.4f".format(kMax)} (ECP 203-2025 Table 4-1, no redistribution, printed 4-6/4-7): increase the waist thickness; compression steel per Eq. 4-5..4-7 is not implemented"
            }
        ))

        // Minimum main steel — ECP 203-2025 §6-2-1-3-2 item 1 (printed 6-6, scan PDF 197):
        // ratio ≥ 0.6/fy of the effective concrete area (b×d), and ≥ 0.25% for mild steel
        // or 0.15% (fcu ≤ 30) / 0.18% (fcu ≥ 40) for high-strength steel, linear between.
        // Steel class follows the grade implied by fy (≤ 280 mild, higher = high-strength).
        safetyChecks.add(StairSafetyCheck(
            name = "Minimum main steel (ECP §6-2-1-3-2)",
            value = mainRebarArea, limit = minSteelArea, unit = "mm²/m",
            isSafe = mainRebarArea >= minSteelArea,
            description = "ECP 203-2025 §6-2-1-3-2 item 1 (printed 6-6): ratio ≥ max(0.6/fy = ${"%.3f".format(0.6 / input.fy * 100)}%, ${if (input.fy <= 280.0) "0.25% mild" else "0.15/0.18% high-strength by fcu"} floor) of b×d; required ${"%.0f".format(minSteelArea)} mm²/m, provided ${"%.0f".format(mainRebarArea)} mm²/m. Steel class taken from fy = ${"%.0f".format(input.fy)} MPa"
        ))
        val rho = mainRebarArea / (b * d)

        // حديد التوزيع — ECP 203-2025 §6-2-1-3-2 item 7 (printed 6-6): distribution bars
        // perpendicular to the main steel: area ≥ 1/5 of the main steel, and at least
        // 4 bars per metre (spacing ≤ 250 mm). The bars run across the stair width and are
        // spaced along the flight length; the issued spec is a per-metre spacing spec.
        val distAreaRequired = 0.2 * mainRebarArea
        val distributionRebar = selectDistBars(distAreaRequired)
        val distributionRebarArea = parseDistBarArea(distributionRebar)

        codeNotes.add("Main reinforcement: $mainRebar (${String.format("%.0f", mainRebarArea)} mm²/m)")
        codeNotes.add("Distribution: $distributionRebar (${String.format("%.0f", distributionRebarArea)} mm²/m) — ≥ 20% of main and ≥ 4 bars/m (§6-2-1-3-2 item 7, printed 6-6)")
        codeNotes.add("Detailing per §6-2-1-3-2 (printed 6-6): bottom bars extended into the supports ≥ 1/3 of the midspan bottom steel (item 5); max main-bar spacing in moment zones 200 mm (item 4) — extension lengths not computed")

        // ========== 5ب. حديد علوي عند الركائز ==========
        // Support top steel — two grounded code minimums:
        //  (a) ECP 203-2025 §6-2-1-2 item 4 / Eq. 6-2 (printed 6-5, scan PDF 196): for
        //      slabs cast into walls or beams that give partial end fixity, the negative
        //      support moment taken into account is at least M = −w·L²/16. Stair flights
        //      are cast monolithic with the landing and floor beams, so this minimum top
        //      steel is required even with the simply supported positive-moment model.
        //  (b) §6-2-1-3-2 item 9 (printed 6-6): slabs thicker than 160 mm need a top mesh
        //      in each direction ≥ 20% of the main steel and not less than 5Ø8/m.
        // Extension/anchorage lengths (§4-2-5) are not computed — noted, not invented.
        val muNeg = horizontalLoad * span * span / 16.0 * 1e6
        val kNeg = muNeg / (input.fcu * b * d * d)
        val leverArmNeg = if (0.25 - kNeg / K_DIVISOR > 0) {
            d * (0.5 + sqrt(0.25 - kNeg / K_DIVISOR))
        } else {
            d * 0.7
        }
        val topFlexureArea = if (fs > 0 && leverArmNeg > 0) muNeg / (fs * leverArmNeg) else 0.0
        val topMeshFloor = if (waistThickness > 160.0) {
            max(0.2 * mainRebarArea, 5.0 * PI * 8.0 * 8.0 / 4.0)  // 5Ø8/m = 251 mm²/m
        } else {
            0.0
        }
        val topRequired = max(topFlexureArea, topMeshFloor)
        val manualTop = input.manualTopDia != null && input.manualTopSpacing != null &&
            input.manualTopDia > 0 && input.manualTopSpacing > 0.0
        val topRebar = if (manualTop) "Ø${input.manualTopDia} @ ${input.manualTopSpacing!!.toInt()} mm c/c" else selectBars(topRequired)
        val topRebarArea = if (manualTop) PI * input.manualTopDia!! * input.manualTopDia / 4.0 * 1000.0 / input.manualTopSpacing!! else parseBarArea(topRebar)
        if (manualMain) {
            safetyChecks.add(StairSafetyCheck(
                name = "Entered bottom steel vs required flexure steel",
                value = mainRebarArea, limit = astRequired, unit = "mm²/m",
                isSafe = mainRebarArea >= astRequired,
                description = "Entered As = ${"%.0f".format(mainRebarArea)} mm²/m against the required ${"%.0f".format(astRequired)} mm²/m (ECP 203-2025 flexure, K-method as above)"
            ))
        }

        safetyChecks.add(StairSafetyCheck(
            name = "Support top steel (Eq. 6-2 / §6-2-1-3-2-9)",
            value = topRebarArea, limit = topRequired, unit = "mm²/m",
            isSafe = topRebarArea >= topRequired,
            description = "ECP 203-2025: negative support moment −w·L²/16 (Eq. 6-2, printed 6-5, partial fixity at monolithic supports)" +
                (if (topMeshFloor > 0.0) "; waist > 160 mm → mandatory top mesh ≥ max(20% of main, 5Ø8/m) (§6-2-1-3-2 item 9, printed 6-6)" else "") +
                ". Required ${"%.0f".format(topRequired)} mm²/m, provided ${"%.0f".format(topRebarArea)} mm²/m; anchorage/extension per §4-2-5 not computed"
        ))
        codeNotes.add("Top steel at supports: $topRebar (${"%.0f".format(topRebarArea)} mm²/m) per Eq. 6-2" + (if (topMeshFloor > 0.0) " and the t>160 mm top-mesh rule" else "") + "; extension into the span per §4-2-5 not computed")

        // ========== 6. تصميم القص ==========
        val Vu = adjustedShear * 1000.0
        val qcu = 0.16 * sqrt(input.fcu / GAMMA_C)  // ECP 203-2025 Eq. 4-29 (printed 4-19): uncracked slab concrete shear stress
        val concreteShearCapacity = qcu * b * d / 1000.0
        val maxShearStress = 0.7 * sqrt(input.fcu / GAMMA_C)  // ECP 203-2025 Eq. 4-27 (printed 4-18)
        val maxShearCapacity = maxShearStress * b * d / 1000.0

        val shearSafe = (Vu / 1000.0) <= maxShearCapacity
        safetyChecks.add(StairSafetyCheck(
            name = "Shear Capacity",
            value = adjustedShear, limit = maxShearCapacity, unit = "kN/m",
            isSafe = shearSafe,
            description = "ECP 203-2025 Eq. 4-27 (printed 4-18): q_umax = 0.70·√(fcu/γc)"
        ))

        val requiredStirrups: String
        val stirrupDiameter: Double
        val stirrupSpacing: Double

        // §4-2-2-1-6-د-1 (printed 4-23) and §4-2-2-2-1 "البلاطات والقواعد" (printed 4-25):
        // slabs and footings resist shear by CONCRETE ONLY per Eq. 4-29, and their
        // thickness is set on that basis. Web reinforcement (links) is NOT a permitted
        // shear path for the waist: when Vu exceeds the Eq. 4-29 capacity the compliant
        // response is a thicker waist, not links.
        if ((Vu / 1000.0) <= concreteShearCapacity) {
            requiredStirrups = "None (links not permitted in slabs, §4-2-2-2-1)"
            stirrupDiameter = 0.0
            stirrupSpacing = 0.0
            codeNotes.add("Vc = ${String.format("%.2f", concreteShearCapacity)} kN/m ≥ Vu = ${String.format("%.2f", adjustedShear)} kN/m (Eq. 4-29, concrete-only basis)")
            codeNotes.add("No shear reinforcement: §4-2-2-2-1 requires slabs to be designed on a concrete-only shear basis")
            safetyChecks.add(StairSafetyCheck(
                name = "Shear (concrete-only, Eq. 4-29)",
                value = adjustedShear, limit = concreteShearCapacity, unit = "kN/m",
                isSafe = true,
                description = "ECP 203-2025 Eq. 4-29 (printed 4-19) + §4-2-2-1-6-د-1 (printed 4-23) + §4-2-2-2-1 (printed 4-25): slabs designed on concrete-only shear basis"
            ))
        } else {
            // Vu > Eq. 4-29 concrete capacity: links are NOT permitted — the waist
            // is inadequate and must be thickened. Result is unsafe.
            requiredStirrups = "None (waist inadequate — increase thickness)"
            stirrupDiameter = 0.0
            stirrupSpacing = 0.0
            codeNotes.add("WARNING: Vu = ${String.format("%.2f", adjustedShear)} kN/m > Vc = ${String.format("%.2f", concreteShearCapacity)} kN/m (Eq. 4-29). Links are not permitted in slabs (§4-2-2-2-1) — increase waist thickness")
            safetyChecks.add(StairSafetyCheck(
                name = "Shear (concrete-only, Eq. 4-29)",
                value = adjustedShear, limit = concreteShearCapacity, unit = "kN/m",
                isSafe = false,
                description = "ECP 203-2025 Eq. 4-29 (printed 4-19) + §4-2-2-2-1 (printed 4-25): Vu exceeds concrete-only capacity — increase waist thickness (links not permitted in slabs)"
            ))
        }

        // ========== 7. فحص الانحراف ==========
        // ECP 203-2025 §4-3-1-3 and Table 4-10 (printed 4-65, scan PDF 145): deflection
        // computation may be skipped for one-way slabs of span <= 6 m when Ln/t does not
        // exceed the tabulated value: solid slab 25 simply supported, 28 continuous one end,
        // 31 continuous both ends, 10 cantilever; for fy other than 420 N/mm2 the value is
        // multiplied by (0.40 + fy/700) (Eq. 4-81). Applied here by treating the inclined
        // waist as a solid one-way slab (an analogy: the 2025 contents list no stair-specific
        // clause). No landing continuity is credited, so the simply supported value is used.
        // If the limit is not met the code requires a computed deflection; that computation
        // is NOT implemented, so the check fails instead of being passed by an ungrounded
        // modification factor.
        val table410SimplySupportedSolid = 25.0
        val fyFactor = 0.40 + input.fy / 700.0
        val allowableRatio = table410SimplySupportedSolid * fyFactor
        val actualRatio = (span * 1000) / waistThickness
        val spanWithinTable = span <= 6.0
        val deflectionOk = spanWithinTable && actualRatio <= allowableRatio
        val minThicknessMm = span * 1000 / allowableRatio
        val calculatedDeflection = 0.0     // not computed in this version
        val allowableDeflection = 0.0      // not computed in this version

        safetyChecks.add(StairSafetyCheck(
            name = "Deflection NOT ASSESSED: ECP Table 4-10 L/t deemed-to-satisfy applicability unverified (no computed deflection)",
            value = actualRatio, limit = allowableRatio, unit = "",
            isSafe = false,
            notAssessed = true,
            description = if (!spanWithinTable) {
                "NOT ASSESSED: span over 6 m is outside Table 4-10 applicability; a computed deflection (ECP 4-3) is required and is not implemented. Not a demonstrated failure, not a pass"
            } else if (deflectionOk) {
                "NOT ASSESSED: Table 4-10 conditions of use are not verified for this stair. ECP 203-2025 Table 4-10: Ln/t = ${"%.1f".format(actualRatio)} <= ${"%.1f".format(allowableRatio)} (solid slab, simply supported 25 x (0.40+fy/700)); the adopted waist meets the table value only, no computed deflection"
            } else {
                "NOT ASSESSED: Table 4-10 is deemed-to-satisfy only. Ln/t = ${"%.1f".format(actualRatio)} > ${"%.1f".format(allowableRatio)}, so deflection is NOT demonstrated (not a computed failure). Waist of at least ${"%.0f".format(minThicknessMm)} mm meets the table, or a computed deflection (ECP 4-3) is required and is not implemented in this version"
            }
        ))
        // ECP 203-2025 §6-2-1-6, Eq. 6-6a (printed 6-7, scan PDF 198): in any case the slab
        // thickness is not less than a/35 simply supported (a/40 continuous one side,
        // a/45 continuous both sides), a = effective span. Slab analogy; the analysis span
        // is used as a (conservative vs the code's smaller effective span).
        val tMinSlabMm = span * 1000.0 / 35.0
        safetyChecks.add(StairSafetyCheck(
            name = "Waist >= a/35 (ECP 203-2025 Eq. 6-6a one-way slab minimum, analogy)",
            value = waistThickness, limit = tMinSlabMm, unit = "mm",
            isSafe = waistThickness >= tMinSlabMm,
            description = "ECP 203-2025 §6-2-1-6, Eq. 6-6a (printed 6-7): t ≥ a/35 simply supported one-way slabs; stair waist treated as a slab (analogy); analysis span used as the effective span a (conservative)"
        ))
        codeNotes.add("Deflection: ECP 203-2025 Table 4-10 (printed 4-65) deemed-to-satisfy only; no computed deflection is reported")

        // ========== 8. فحوصات هندسية ==========
        safetyChecks.add(StairSafetyCheck(
            name = "2R + G (Comfort)",
            value = 2 * riserMm + goingMm, limit = COMFORT_2R_PLUS_G_MAX, unit = "mm",
            isSafe = 2 * riserMm + goingMm in COMFORT_2R_PLUS_G_MIN..COMFORT_2R_PLUS_G_MAX,
            description = "Comfort formula: 550 ≤ 2R+G ≤ 700 mm"
        ))
        safetyChecks.add(StairSafetyCheck(
            name = "Riser ≤ 180mm",
            value = riserMm, limit = MAX_RISER, unit = "mm",
            isSafe = riserMm <= MAX_RISER,
            description = "Maximum riser height per ECP 201"
        ))
        safetyChecks.add(StairSafetyCheck(
            name = "Going ≥ 250mm",
            value = goingMm, limit = MIN_GOING, unit = "mm",
            isSafe = goingMm >= MIN_GOING,
            description = "Minimum going per ECP 201"
        ))

        // ========== 9. ملاحظات الكود ==========
        codeNotes.add(CodeReference.ECP.SLAB_ONE_WAY)
        codeNotes.add("ECP 203-2025: Section 4-2-1 + Table 4-1 (Flexure design of waist slab)")
        codeNotes.add("ECP 203-2025: Section 6-2-1 (One-way slab analysis and reinforcement rules)")
        codeNotes.add("ECP 201-2012: Staircase live load = 4.0 kN/m²")

        return StaircaseResult(
            isSafe = safetyChecks.all { it.isSafe },
            designCode = DesignCode.ECP,
            riser = riserMm,
            going = goingMm,
            numberOfRisers = numRisers,
            numberOfTreads = numTreads,
            slopeAngle = slopeAngle,
            inclinedLength = inclinedLength,
            factoredLoad = factoredOnSlope,
            horizontalLoad = horizontalLoad,
            maxMoment = adjustedMoment,
            maxShear = adjustedShear,
            reactionA = reactionA,
            reactionB = reactionB,
            mainRebar = mainRebar,
            mainRebarArea = mainRebarArea,
            distributionRebar = distributionRebar,
            distributionRebarArea = distributionRebarArea,
            effectiveDepth = dEffective,
            reinforcementRatio = rho,
            minSteelRatio = minSteelRatio,
            shearCapacity = concreteShearCapacity,
            requiredStirrups = requiredStirrups,
            stirrupDiameter = stirrupDiameter,
            stirrupSpacing = stirrupSpacing,
            deflection = calculatedDeflection,
            allowableDeflection = allowableDeflection,
            deflectionOk = deflectionOk,
            deflectionComputed = false,
            safetyChecks = safetyChecks,
            codeNotes = codeNotes,
            analysisStatus = analysisStatus,
            analysisSpanM = span,
            adoptedRunMm = adoptedRunMm,
            flightBottomRebar = mainRebar,
            flightTopRebar = topRebar,
            landingRebar = ""
        )
    }

    // ========== دوال مساعدة ==========

    /**
     * ECP 203-2025 جدول (4-1) — بدون إعادة توزيع العزوم (printed 4-6/4-7, scan PDF 86-87):
     * the ductility cap Rmax by steel grade, with Mumax = Rmax·fcu·b·d²/γc (Eq. 4-4).
     * Linear interpolation between tabulated grades; clamped at the table ends.
     */
    private fun rMaxNoRedistribution(fy: Double): Double {
        val table = listOf(
            240.0 to 0.214, 350.0 to 0.196, 400.0 to 0.189, 420.0 to 0.186,
            500.0 to 0.176, 600.0 to 0.165, 700.0 to 0.155
        )
        if (fy <= table.first().first) return table.first().second
        if (fy >= table.last().first) return table.last().second
        for (i in 1 until table.size) {
            val (x1, y1) = table[i - 1]
            val (x2, y2) = table[i]
            if (fy <= x2) return y1 + (y2 - y1) * (fy - x1) / (x2 - x1)
        }
        return table.last().second
    }

    /**
     * ECP 203-2025 §6-2-1-3-2 item 1 (printed 6-6, scan PDF 197): one-way slab main
     * steel ratio not less than 0.6/fy of the effective concrete area, and not less
     * than 0.25% for mild steel, or 0.15% (fcu ≤ 30 N/mm²) / 0.18% (fcu ≥ 40 N/mm²)
     * for high-strength deformed steel, with linear interpolation between.
     * Steel class follows the grade implied by fy: ≤ 280 MPa is mild (240/350, 280/450);
     * higher grades (360/520, 400/600, 500/650) are high-strength.
     */
    private fun minMainSteelRatioSlab(fcu: Double, fy: Double): Double {
        val classFloor = if (fy <= 280.0) {
            0.0025
        } else {
            when {
                fcu <= 30.0 -> 0.0015
                fcu >= 40.0 -> 0.0018
                else -> 0.0015 + (0.0018 - 0.0015) * (fcu - 30.0) / 10.0
            }
        }
        return max(0.6 / fy, classFloor)
    }

    /**
     * Gooseneck (حبة البط) detail calculation per ECP 203 §4-2
     * The gooseneck is the re-entrant corner at the junction of the waist slab
     * and the landing slab where significant tension and stress concentration occurs.
     *
     * Key checks:
     * 1. Minimum gooseneck radius ≥ 50mm to prevent stress concentration cracking
     * 2. Additional reinforcement at the re-entrant corner (typically 50% extra)
     * 3. Development length check for bars terminating at gooseneck
     *
     * NOTE: the 50 mm radius, 0.3·riser recommendation, bar counts and the
     * 0.6·√fcu bond strength are app detailing assumptions; the cited clauses
     * have NOT been verified against the ECP 203-2025 scan.
     *
     * @param riserMm riser height (mm)
     * @param goingMm tread going (mm)
     * @param waistThickness waist slab thickness (mm)
     * @param fy steel yield strength (MPa)
     * @param fcu concrete cube strength (MPa)
     * @return GooseneckDetailResult with reinforcement and geometry
     */
    fun calculateGooseneckDetail(
        riserMm: Double,
        goingMm: Double,
        waistThickness: Double,
        fy: Double,
        fcu: Double
    ): GooseneckDetailResult {
        InputGuard.positive("riserMm", riserMm)
        InputGuard.positive("goingMm", goingMm)
        InputGuard.positive("waistThickness", waistThickness)
        InputGuard.positive("fy", fy)
        InputGuard.positive("fcu", fcu)

        // Minimum radius at re-entrant corner (app detailing assumption, clause not verified)
        val minGooseneckRadius = 50.0  // mm
        val recommendedRadius = max(minGooseneckRadius, riserMm * 0.3)

        // Additional reinforcement at gooseneck (app detailing assumption, clause not verified)
        val fbd = 0.6 * sqrt(fcu)  // Bond strength, cited §5-2-2 — not verified against the 2025 scan
        val barDia = 10.0  // mm — typical gooseneck bar
        val barArea = PI * barDia * barDia / 4.0
        val fs = fy / GAMMA_S
        val Ld = (fs * barDia / (4 * fbd.coerceAtLeast(0.1))).coerceAtLeast(100.0)
        val LdRounded = ceil(Ld / 25.0) * 25.0  // Round to 25mm

        // Number of additional gooseneck bars (typically 2-3)
        // For steep stairs (riser > 170mm), use 3 bars
        val numGooseneckBars = if (riserMm > 170.0) 3 else 2

        // Angle of the gooseneck (half-angle at re-entrant corner)
        val slopeAngleRad = atan2(riserMm, goingMm)
        val gooseneckAngle = Math.toDegrees(slopeAngleRad)

        // Extended length along waist slab (minimum 1/4 span from support)
        val extensionMin = max(LdRounded, 300.0)  // mm — at least 300mm or Ld

        return GooseneckDetailResult(
            gooseneckRadius = recommendedRadius,
            minRadius = minGooseneckRadius,
            additionalBars = numGooseneckBars,
            additionalBarDia = barDia,
            additionalBarArea = numGooseneckBars * barArea,
            developmentLength = LdRounded,
            extensionLength = extensionMin,
            gooseneckAngle = gooseneckAngle,
            codeNote = "Gooseneck detail (app assumption, clauses not verified): ${numGooseneckBars}Ø${barDia.toInt()} at gooseneck, extend min ${extensionMin.toInt()}mm each side, r≥${minGooseneckRadius.toInt()}mm"
        )
    }

    /**
     * اختيار أسياخ رئيسية (مقاسات مترية - ECP)
     * Spacing kept within 100–200 mm: the 200 mm cap is ECP 203-2025 §6-2-1-3-2 item 4
     * (printed 6-6, max main-bar spacing in moment zones); 100 mm is a practical floor.
     * النتيجة بصيغة: "Ø12 @ 150 mm c/c"
     */
    private fun selectBars(requiredArea: Double): String {
        val availableBars = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 25.0)
        val barDia = availableBars.firstOrNull {
            val area = PI * it * it / 4
            val numBars = ceil(requiredArea / area).toInt()
            val spacing = 1000.0 / numBars
            spacing >= 100.0 && spacing <= 200.0
        } ?: 12.0

        val barArea = PI * barDia * barDia / 4
        val numBars = ceil(requiredArea / barArea).toInt().coerceAtLeast(5)
        val spacing = 1000.0 / numBars
        return "Ø${barDia.toInt()} @ ${spacing.toInt()} mm c/c"
    }

    /**
     * اختيار أسياخ التوزيع — ECP 203-2025 §6-2-1-3-2 item 7 (printed 6-6):
     * area ≥ 1/5 of the main steel and at least 4 bars per metre (spacing ≤ 250 mm).
     * The spec is per metre of flight length; bars run across the stair width.
     */
    private fun selectDistBars(requiredAreaPerM: Double): String {
        val availableBars = listOf(8.0, 10.0, 12.0)
        for (d in availableBars) {
            val area = PI * d * d / 4
            val spacingRaw = floor(area * 1000.0 / requiredAreaPerM / 10.0) * 10.0
            val spacing = spacingRaw.coerceAtMost(250.0)  // ≥ 4 bars per metre
            if (spacing >= 100.0) return "Ø${d.toInt()} @ ${spacing.toInt()} mm c/c"
        }
        return "Ø12 @ 100 mm c/c"
    }

    /**
     * حساب مساحة الحديد الرئيسي من النص (mm²/m)
     */
    private fun parseBarArea(barString: String): Double {
        if (barString.contains("None")) return 0.0
        try {
            val parts = barString.split("Ø", " @ ")
            if (parts.size >= 3) {
                val dia = parts[1].trim().split(" ")[0].toDouble()
                val spacing = parts[2].trim().split(" ")[0].toDouble()
                val barArea = PI * dia * dia / 4.0
                return barArea * 1000.0 / spacing
            }
        } catch (e: Exception) { /* fall through */ }
        return 0.0
    }

    /** قطر السيخ من نص الاختيار (mm) */
    private fun barDiameterOf(barString: String): Double {
        try {
            val parts = barString.split("Ø", " @ ")
            if (parts.size >= 2) return parts[1].trim().split(" ")[0].toDouble()
        } catch (e: Exception) { /* fall through */ }
        return 0.0
    }

    /**
     * مساحة حديد التوزيع من النص (mm² per metre of flight length)
     */
    private fun parseDistBarArea(barString: String): Double {
        if (barString.contains("None")) return 0.0
        try {
            val parts = barString.split("Ø", " @ ")
            if (parts.size >= 3) {
                val dia = parts[1].trim().split(" ")[0].toDouble()
                val spacing = parts[2].trim().split(" ")[0].toDouble()
                val barArea = PI * dia * dia / 4.0
                return barArea * 1000.0 / spacing
            }
        } catch (e: Exception) { /* fall through */ }
        return 0.0
    }
}
