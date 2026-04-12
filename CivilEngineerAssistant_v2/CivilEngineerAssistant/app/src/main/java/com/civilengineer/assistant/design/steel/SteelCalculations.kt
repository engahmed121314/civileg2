package com.civilengineer.assistant.design.steel

import com.civilengineer.assistant.models.*
import com.civilengineer.assistant.models.steel.*
import kotlin.math.*

/**
 * محرك حسابات التصميم المعدني الشامل
 * Steel Design Calculations Engine
 * حسب: AISC 360-22 / ECP 205-2020 / EC3 (EN 1993)
 */
object SteelCalculations {

    // ═══════════════════════════════════════════════════════════
    // 1. تصميم كمرة معدنية - Steel Beam Design
    // ═══════════════════════════════════════════════════════════

    fun steelBeamDesign(
        Mu: Double,                    // kN.m - العزم التصميمي
        Vu: Double,                    // kN - القص التصميمي
        span: Double,                  // mm - البحر
        section: SteelSectionProperties,
        grade: SteelGradeType,
        code: SteelDesignCode,
        Lb: Double = span,             // mm - الطول غير المسنود جانبياً
        Cb: Double = 1.0,              // معامل تعديل العزم
        serviceLoad: Double = 0.0      // kN/m - حمل الخدمة (للترخيم)
    ): SteelBeamResult {
        val equations = mutableListOf<EquationStep>()
        val codeChecks = mutableListOf<CodeCheck>()
        var stepNum = 1

        val fy = grade.fy
        val fu = grade.fu
        val E = grade.E

        // ── تصنيف القطاع (Compactness) ──
        val lambdaF = section.b / (2 * section.tf)
        val lambdaW = section.d / section.tw
        val lambdaPF = 0.38 * sqrt(E / fy)    // حد الشفة Compact
        val lambdaPW = 3.76 * sqrt(E / fy)    // حد الوتر Compact
        val lambdaRF = 1.0 * sqrt(E / fy)     // حد الشفة Non-compact
        val lambdaRW = 5.70 * sqrt(E / fy)    // حد الوتر Non-compact

        val flangeClass = when {
            lambdaF <= lambdaPF -> "Compact"
            lambdaF <= lambdaRF -> "Non-compact"
            else -> "Slender"
        }
        val webClass = when {
            lambdaW <= lambdaPW -> "Compact"
            lambdaW <= lambdaRW -> "Non-compact"
            else -> "Slender"
        }
        val sectionClass = if (flangeClass == "Compact" && webClass == "Compact") "Compact"
        else if (flangeClass != "Slender" && webClass != "Slender") "Non-compact"
        else "Slender"

        equations.add(EquationStep(
            stepNum++, "تصنيف القطاع (Classification)",
            "λf = bf/(2tf), λw = d/tw",
            "λf = ${fmt(lambdaF)}, λw = ${fmt(lambdaW)}\nλpf = ${fmt(lambdaPF)}, λpw = ${fmt(lambdaPW)}",
            "الشفة: $flangeClass, الوتر: $webClass → القطاع: $sectionClass",
            when (code) {
                SteelDesignCode.AISC_360 -> "AISC 360 - Table B4.1b"
                SteelDesignCode.ECP_205 -> "ECP 205 - Table 2.1"
                else -> "EN 1993 - Table 5.2"
            },
            "تصنيف القطاع يحدد هل يمكن الوصول للسعة اللدنة الكاملة"
        ))

        codeChecks.add(CodeCheck("تصنيف القطاع", "λf ≤ λp, λw ≤ λp",
            "Compact", sectionClass, sectionClass == "Compact" || sectionClass == "Non-compact"))

        // ── سعة العزم اللدن Mp ──
        val Mp = fy * section.Zx / 1e6  // kN.m
        val My = fy * section.Wx / 1e6  // kN.m

        equations.add(EquationStep(
            stepNum++, "سعة العزم اللدن Mp",
            "Mp = Fy × Zx",
            "Mp = ${fmt(fy)} × ${fmt(section.Zx)} / 10⁶",
            "Mp = ${fmt(Mp)} kN.m",
            "AISC F2.1 / ECP 205"
        ))

        // ── الانبعاج الجانبي الالتوائي (LTB) ──
        val Lp = 1.76 * section.ry * sqrt(E / fy)  // mm
        val rts = sqrt(sqrt(section.Iy * section.Cw.coerceAtLeast(1.0)) / section.Wx)
            .let { if (it > 0) it else section.ry * 1.2 }
        val c = 1.0 // for doubly symmetric
        val Lr = 1.95 * rts * (E / (0.7 * fy)) * sqrt(
            (section.J.coerceAtLeast(1.0) * c / (section.Wx * (section.h - section.tf))) +
                    sqrt(((section.J.coerceAtLeast(1.0) * c / (section.Wx * (section.h - section.tf))).pow(2)) + 6.76 * ((0.7 * fy / E).pow(2)))
        )

        equations.add(EquationStep(
            stepNum++, "أطوال الانبعاج الجانبي",
            "Lp = 1.76 ry √(E/Fy)\nLr = 1.95 rts (E/0.7Fy)...",
            "Lp = ${fmt(Lp / 1000)} m, Lr = ${fmt(Lr / 1000)} m\nLb = ${fmt(Lb / 1000)} m",
            when {
                Lb <= Lp -> "Lb ≤ Lp → لا انبعاج جانبي (Zone 1)"
                Lb <= Lr -> "Lp < Lb ≤ Lr → انبعاج جانبي غير مرن (Zone 2)"
                else -> "Lb > Lr → انبعاج جانبي مرن (Zone 3)"
            },
            "AISC F2.2 / ECP 205"
        ))

        // ── سعة العزم الاسمية Mn ──
        val phi = when (code) {
            SteelDesignCode.AISC_360 -> 0.90
            SteelDesignCode.ECP_205 -> 1.0 / 1.10  // γM1 = 1.10
            else -> 1.0 / 1.0
        }

        val Mn: Double = when {
            sectionClass == "Compact" && Lb <= Lp -> Mp
            sectionClass == "Compact" && Lb <= Lr -> {
                val CbMn = Cb * (Mp - (Mp - 0.7 * fy * section.Wx / 1e6) * (Lb - Lp) / (Lr - Lp))
                minOf(CbMn, Mp)
            }
            sectionClass == "Compact" -> {
                val Fcr = Cb * PI * PI * E / ((Lb / rts).pow(2)) *
                        sqrt(1 + 0.078 * (section.J.coerceAtLeast(1.0) * c / (section.Wx * (section.h - section.tf))) * (Lb / rts).pow(2))
                minOf(Fcr * section.Wx / 1e6, Mp)
            }
            sectionClass == "Non-compact" -> {
                // Flange Local Buckling
                val MnFLB = Mp - (Mp - 0.7 * fy * section.Wx / 1e6) * ((lambdaF - lambdaPF) / (lambdaRF - lambdaPF))
                minOf(MnFLB, Mp)
            }
            else -> 0.9 * E * section.Wx / (lambdaF.pow(2)) / 1e6 // Slender
        }

        val phiMn = phi * Mn

        equations.add(EquationStep(
            stepNum++, "سعة العزم التصميمية φMn",
            "φMn = φ × Mn",
            "Mn = ${fmt(Mn)} kN.m, φ = ${fmt(phi)}",
            "φMn = ${fmt(phiMn)} kN.m",
            "AISC F2 / ECP 205 Clause 4.2",
            "سعة العزم بعد مراعاة الانبعاج الجانبي"
        ))

        val momentUtil = Mu / phiMn
        codeChecks.add(CodeCheck("فحص العزم", "Mu ≤ φMn",
            "${fmt(phiMn)} kN.m", "${fmt(Mu)} kN.m", Mu <= phiMn))

        // ── سعة القص Vn ──
        val Aw = section.d * section.tw  // mm²
        val kvs = 5.34  // for unstiffened webs
        val Cv1 = if (section.d / section.tw <= 2.24 * sqrt(E / fy)) 1.0
        else 1.10 * kvs * sqrt(E / fy) / (section.d / section.tw)

        val Vn = 0.6 * fy * Aw * Cv1 / 1000.0  // kN
        val phiV = when (code) {
            SteelDesignCode.AISC_360 -> 1.0
            else -> 1.0 / 1.10
        }
        val phiVn = phiV * Vn

        equations.add(EquationStep(
            stepNum++, "سعة القص φVn",
            "Vn = 0.6 × Fy × Aw × Cv1",
            "Aw = ${fmt(Aw)} mm², Cv1 = ${fmt(Cv1)}",
            "φVn = ${fmt(phiVn)} kN",
            "AISC G2.1 / ECP 205 Clause 4.3"
        ))

        codeChecks.add(CodeCheck("فحص القص", "Vu ≤ φVn",
            "${fmt(phiVn)} kN", "${fmt(Vu)} kN", Vu <= phiVn))

        // ── الترخيم ──
        val deflActual = if (serviceLoad > 0) {
            5.0 * serviceLoad * (span.pow(4)) / (384.0 * E * section.Ix)
        } else 0.0
        val deflAllow = span / 360.0

        equations.add(EquationStep(
            stepNum++, "فحص الترخيم",
            "δ = 5wL⁴/(384EI) ≤ L/360",
            "δ = ${fmt(deflActual)} mm",
            "δ_allow = L/360 = ${fmt(deflAllow)} mm ${if (deflActual <= deflAllow) "✓" else "✗"}",
            "AISC L / ECP 205 Clause 5"
        ))

        codeChecks.add(CodeCheck("فحص الترخيم", "δ ≤ L/360",
            "${fmt(deflAllow)} mm", "${fmt(deflActual)} mm", deflActual <= deflAllow || serviceLoad == 0.0))

        val isSafe = Mu <= phiMn && Vu <= phiVn && (deflActual <= deflAllow || serviceLoad == 0.0)

        return SteelBeamResult(
            selectedSection = section,
            momentCapacity = phiMn,
            shearCapacity = phiVn,
            deflectionActual = deflActual,
            deflectionAllowable = deflAllow,
            lateralBucklingMoment = Mn,
            compactnessClass = sectionClass,
            utilizationRatio = maxOf(momentUtil, Vu / phiVn),
            isSafe = isSafe,
            equations = equations,
            codeChecks = codeChecks
        )
    }

    // ═══════════════════════════════════════════════════════════
    // 2. تصميم عمود معدني - Steel Column Design
    // ═══════════════════════════════════════════════════════════

    fun steelColumnDesign(
        Pu: Double,
        Lx: Double,                    // mm - الطول الفعال x
        Ly: Double,                    // mm - الطول الفعال y
        Kx: Double = 1.0,
        Ky: Double = 1.0,
        section: SteelSectionProperties,
        grade: SteelGradeType,
        code: SteelDesignCode
    ): SteelColumnResult {
        val equations = mutableListOf<EquationStep>()
        val codeChecks = mutableListOf<CodeCheck>()
        var stepNum = 1

        val fy = grade.fy
        val E = grade.E
        val A = section.A

        // ── نسبة النحافة ──
        val lambdaX = Kx * Lx / section.rx
        val lambdaY = Ky * Ly / section.ry
        val lambdaMax = maxOf(lambdaX, lambdaY)

        equations.add(EquationStep(
            stepNum++, "نسبة النحافة λ",
            "λx = KxLx/rx, λy = KyLy/ry",
            "λx = ${fmt(Kx)}×${fmt(Lx)}/${fmt(section.rx)} = ${fmt(lambdaX)}\nλy = ${fmt(Ky)}×${fmt(Ly)}/${fmt(section.ry)} = ${fmt(lambdaY)}",
            "λ_governing = ${fmt(lambdaMax)} (${if (lambdaX > lambdaY) "حول x" else "حول y"})",
            "AISC E3 / ECP 205 Clause 3"
        ))

        val lambdaLimit = when (code) {
            SteelDesignCode.ECP_205 -> 180.0
            else -> 200.0
        }
        codeChecks.add(CodeCheck("حد النحافة", "λ ≤ $lambdaLimit",
            "$lambdaLimit", fmt(lambdaMax), lambdaMax <= lambdaLimit))

        // ── تصنيف القطاع ──
        val lambdaF = section.b / (2 * section.tf)
        val lambdaPF = 0.56 * sqrt(E / fy)
        val lambdaW = section.d / section.tw
        val lambdaPW = 1.49 * sqrt(E / fy)

        val sectionClass = when {
            lambdaF <= lambdaPF && lambdaW <= lambdaPW -> "Non-slender"
            else -> "Slender"
        }

        equations.add(EquationStep(
            stepNum++, "تصنيف القطاع (ضغط)",
            "λf = ${fmt(lambdaF)} ${if (lambdaF <= lambdaPF) "≤" else ">"} ${fmt(lambdaPF)}\nλw = ${fmt(lambdaW)} ${if (lambdaW <= lambdaPW) "≤" else ">"} ${fmt(lambdaPW)}",
            "القطاع: $sectionClass",
            sectionClass,
            "AISC Table B4.1a / ECP 205"
        ))

        // ── الإجهاد الحرج Fcr ──
        val Fe = PI * PI * E / (lambdaMax.pow(2))  // Euler stress

        val Fcr: Double = if (lambdaMax * sqrt(fy / E) / PI <= 1.5) {
            // Inelastic buckling
            (0.658.pow(fy / Fe)) * fy
        } else {
            // Elastic buckling
            0.877 * Fe
        }

        equations.add(EquationStep(
            stepNum++, "الإجهاد الحرج Fcr",
            "Fe = π²E/λ² = ${fmt(Fe)} MPa",
            if (fy / Fe <= 2.25) "Fy/Fe = ${fmt(fy / Fe)} ≤ 2.25 → Fcr = 0.658^(Fy/Fe) × Fy"
            else "Fy/Fe = ${fmt(fy / Fe)} > 2.25 → Fcr = 0.877Fe",
            "Fcr = ${fmt(Fcr)} MPa",
            "AISC E3-2,3 / ECP 205"
        ))

        // ── سعة العمود ──
        val phi = when (code) {
            SteelDesignCode.AISC_360 -> 0.90
            SteelDesignCode.ECP_205 -> 1.0 / 1.10
            else -> 1.0 / 1.0
        }

        val Pn = Fcr * A / 1000.0  // kN
        val phiPn = phi * Pn

        equations.add(EquationStep(
            stepNum++, "سعة العمود φPn",
            "Pn = Fcr × Ag",
            "Pn = ${fmt(Fcr)} × ${fmt(A)} / 1000",
            "φPn = ${fmt(phi)} × ${fmt(Pn)} = ${fmt(phiPn)} kN",
            "AISC E3-1 / ECP 205 Clause 3.5"
        ))

        val util = Pu / phiPn
        codeChecks.add(CodeCheck("فحص الضغط", "Pu ≤ φPn",
            "${fmt(phiPn)} kN", "${fmt(Pu)} kN", Pu <= phiPn))

        return SteelColumnResult(
            selectedSection = section,
            axialCapacity = phiPn,
            criticalBucklingLoad = Fe * A / 1000.0,
            slendernessRatioX = lambdaX,
            slendernessRatioY = lambdaY,
            reductionFactor = Fcr / fy,
            compactnessClass = sectionClass,
            utilizationRatio = util,
            isSafe = Pu <= phiPn,
            equations = equations,
            codeChecks = codeChecks
        )
    }

    // ═══════════════════════════════════════════════════════════
    // 3. تصميم وصلة براغي - Bolted Connection
    // ═══════════════════════════════════════════════════════════

    fun boltedConnectionDesign(
        Pu: Double,                    // kN - القوة التصميمية
        connectionType: String,        // "shear", "tension", "combined"
        boltDia: BoltDiameter,
        boltGrade: BoltGrade,
        plateThickness: Double,        // mm
        plateFu: Double,               // MPa
        code: SteelDesignCode,
        isSingleShear: Boolean = true,
        isSlipCritical: Boolean = false
    ): BoltedConnectionResult {
        val equations = mutableListOf<EquationStep>()
        val codeChecks = mutableListOf<CodeCheck>()
        var stepNum = 1

        val d = boltDia.d
        val Ab = boltDia.area
        val As = boltDia.tensileArea
        val fub = boltGrade.fub

        val phi = when (code) {
            SteelDesignCode.AISC_360 -> 0.75
            SteelDesignCode.ECP_205 -> 1.0 / 1.25
            else -> 1.0 / 1.25
        }

        // ── سعة القص لبرغي واحد ──
        val nShearPlanes = if (isSingleShear) 1 else 2
        val Fnv = 0.50 * fub  // N/mm² (AISC: threads in shear plane)
        val Rnv = phi * Fnv * Ab * nShearPlanes / 1000.0  // kN

        equations.add(EquationStep(
            stepNum++, "سعة القص لبرغي واحد",
            "Rnv = φ × Fnv × Ab × n_planes",
            "Fnv = 0.50 × ${fmt(fub)} = ${fmt(Fnv)} MPa\nRnv = ${fmt(phi)} × ${fmt(Fnv)} × ${fmt(Ab)} × $nShearPlanes / 1000",
            "Rnv = ${fmt(Rnv)} kN",
            "AISC J3.6 / ECP 205"
        ))

        // ── سعة التحمل (Bearing) ──
        val dHole = d + 2  // standard hole
        val edgeDistance = 2.0 * d  // minimum
        val pitch = 3.0 * d
        val lc = edgeDistance - dHole / 2.0
        val RnBearing = phi * 2.4 * d * plateThickness * plateFu / 1000.0  // kN (upper bound)
        val RnBearingLC = phi * 1.2 * lc * plateThickness * plateFu / 1000.0
        val Rn_bearing_per_bolt = minOf(RnBearing, RnBearingLC)

        equations.add(EquationStep(
            stepNum++, "سعة التحمل لبرغي واحد",
            "Rn = min(2.4dtFu, 1.2lctFu) × φ",
            "2.4dtFu = ${fmt(RnBearing)} kN, 1.2lctFu = ${fmt(RnBearingLC)} kN",
            "Rn_bearing = ${fmt(Rn_bearing_per_bolt)} kN",
            "AISC J3.10 / ECP 205"
        ))

        // ── سعة الشد لبرغي واحد ──
        val Fnt = 0.75 * fub
        val RnTension = phi * Fnt * As / 1000.0

        equations.add(EquationStep(
            stepNum++, "سعة الشد لبرغي واحد",
            "Rnt = φ × Fnt × As",
            "Fnt = 0.75 × ${fmt(fub)} = ${fmt(Fnt)} MPa",
            "Rnt = ${fmt(RnTension)} kN"
        ))

        // ── السعة المحكمة لبرغي واحد ──
        val capacityPerBolt = when (connectionType) {
            "shear" -> minOf(Rnv, Rn_bearing_per_bolt)
            "tension" -> RnTension
            else -> minOf(Rnv, Rn_bearing_per_bolt)  // simplified
        }

        // ── عدد البراغي المطلوب ──
        val nBoltsRequired = ceil(Pu / capacityPerBolt).toInt().coerceAtLeast(2)

        // ترتيب البراغي
        val nCols = if (nBoltsRequired <= 4) 1 else 2
        val nRows = ceil(nBoltsRequired.toDouble() / nCols).toInt()
        val gauge = if (nCols > 1) 3.0 * d else 0.0

        equations.add(EquationStep(
            stepNum++, "عدد البراغي",
            "n = Pu / R_per_bolt",
            "n = ${fmt(Pu)} / ${fmt(capacityPerBolt)} = ${fmt(Pu / capacityPerBolt)}",
            "n = $nBoltsRequired برغي ($nRows صفوف × $nCols أعمدة)",
            "AISC J3"
        ))

        val totalCapacity = nBoltsRequired * capacityPerBolt
        val util = Pu / totalCapacity

        codeChecks.add(CodeCheck("سعة الوصلة", "Pu ≤ n × Rn",
            "${fmt(totalCapacity)} kN", "${fmt(Pu)} kN", Pu <= totalCapacity))

        return BoltedConnectionResult(
            numberOfBolts = nBoltsRequired,
            boltDiameter = boltDia,
            boltGrade = boltGrade,
            shearCapacityPerBolt = Rnv,
            bearingCapacityPerBolt = Rn_bearing_per_bolt,
            tensionCapacityPerBolt = RnTension,
            totalCapacity = totalCapacity,
            rows = nRows,
            columns = nCols,
            edgeDistance = edgeDistance,
            pitch = pitch,
            gauge = gauge,
            utilizationRatio = util,
            isSafe = Pu <= totalCapacity,
            equations = equations,
            codeChecks = codeChecks
        )
    }

    // ═══════════════════════════════════════════════════════════
    // 4. تصميم وصلة لحام - Welded Connection
    // ═══════════════════════════════════════════════════════════

    fun weldedConnectionDesign(
        Pu: Double,                    // kN
        weldType: WeldType,
        weldSize: Double,              // mm - حجم اللحام
        weldLength: Double,            // mm - طول اللحام
        electrodeStrength: Double = 490.0,  // MPa (E70xx)
        code: SteelDesignCode
    ): WeldedConnectionResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        val FEXX = electrodeStrength
        val phi = when (code) {
            SteelDesignCode.AISC_360 -> 0.75
            else -> 1.0 / 1.25
        }

        // الحلق الفعال
        val te = when (weldType) {
            WeldType.FILLET -> weldSize * 0.707  // a × sin45°
            WeldType.GROOVE_FULL -> weldSize     // full penetration
            WeldType.GROOVE_PARTIAL -> weldSize * 0.707
            else -> weldSize * 0.707
        }

        equations.add(EquationStep(
            stepNum++, "سمك الحلق الفعال te",
            when (weldType) {
                WeldType.FILLET -> "te = 0.707 × a"
                else -> "te = a"
            },
            "te = ${if (weldType == WeldType.FILLET) "0.707 × " else ""}${fmt(weldSize)}",
            "te = ${fmt(te)} mm"
        ))

        // سعة اللحام الزاوي
        val Fnw = 0.60 * FEXX
        val effectiveLength = weldLength - 2 * weldSize  // deduct ends

        val Rn = phi * Fnw * te * effectiveLength / 1000.0  // kN

        equations.add(EquationStep(
            stepNum++, "سعة اللحام",
            "Rn = φ × 0.60FEXX × te × Leff",
            "Rn = ${fmt(phi)} × 0.60 × ${fmt(FEXX)} × ${fmt(te)} × ${fmt(effectiveLength)} / 1000",
            "Rn = ${fmt(Rn)} kN",
            "AISC J2.4 / ECP 205"
        ))

        val util = Pu / Rn

        return WeldedConnectionResult(
            weldType = weldType,
            weldSize = weldSize,
            weldLength = weldLength,
            weldCapacity = Rn,
            effectiveThroat = te,
            utilizationRatio = util,
            isSafe = Pu <= Rn,
            equations = equations
        )
    }

    // ═══════════════════════════════════════════════════════════
    // 5. تصميم لوح القاعدة - Base Plate Design
    // ═══════════════════════════════════════════════════════════

    fun basePlateDesign(
        Pu: Double,                    // kN
        Mu: Double,                    // kN.m
        columnSection: SteelSectionProperties,
        grade: SteelGradeType,
        fcConcrete: Double,            // MPa
        code: SteelDesignCode
    ): BasePlateResult {
        val equations = mutableListOf<EquationStep>()
        var stepNum = 1

        val fy = grade.fy
        val bf = columnSection.b
        val d = columnSection.h
        val phi = 0.65  // bearing on concrete

        // مساحة اللوح المطلوبة
        val A1required = Pu * 1000.0 / (phi * 0.85 * fcConcrete)
        val Nmin = d + 100  // mm
        val Bmin = bf + 100

        val N = maxOf(Nmin, sqrt(A1required * d / bf)).let { ceil(it / 25) * 25 }
        val B = maxOf(Bmin, ceil(A1required / N / 25) * 25)

        equations.add(EquationStep(
            stepNum++, "أبعاد لوح القاعدة",
            "A1 ≥ Pu / (φ × 0.85f'c)",
            "A1_req = ${fmt(A1required)} mm²",
            "N = ${fmt(N)} mm, B = ${fmt(B)} mm",
            "AISC J8 / ECP 205"
        ))

        // إجهاد التحمل
        val fp = Pu * 1000.0 / (N * B)  // MPa
        val fpAllow = phi * 0.85 * fcConcrete

        equations.add(EquationStep(
            stepNum++, "إجهاد التحمل على الخرسانة",
            "fp = Pu / (N × B) ≤ φ × 0.85f'c",
            "fp = ${fmt(fp)} MPa, fp_allow = ${fmt(fpAllow)} MPa",
            if (fp <= fpAllow) "آمن ✓" else "غير آمن ✗"
        ))

        // سمك اللوح
        val m = (N - 0.95 * d) / 2.0
        val n = (B - 0.80 * bf) / 2.0
        val lambdaN = sqrt(d * bf) / 4.0
        val controlDim = maxOf(m, n, lambdaN)

        val tp = controlDim * sqrt(2.0 * fp / (0.90 * fy))
        val tpFinal = ceil(tp / 2) * 2  // round to even mm

        equations.add(EquationStep(
            stepNum++, "سمك لوح القاعدة tp",
            "tp = l × √(2fp / 0.9Fy)",
            "m = ${fmt(m)}, n = ${fmt(n)}, l = ${fmt(controlDim)}",
            "tp = ${fmt(tpFinal)} mm",
            "AISC Design Guide 1"
        ))

        // براغي التثبيت
        val anchorDia = if (Pu > 500) 24.0 else if (Pu > 200) 20.0 else 16.0
        val nAnchors = if (Mu > 0) 4 else 2

        return BasePlateResult(
            plateLength = N,
            plateWidth = B,
            plateThickness = tpFinal,
            anchorBoltDiameter = anchorDia,
            numberOfAnchorBolts = nAnchors,
            bearingPressure = fp,
            isSafe = fp <= fpAllow,
            equations = equations
        )
    }

    private fun fmt(v: Double, dec: Int = 2) = String.format("%.${dec}f", v)
}
