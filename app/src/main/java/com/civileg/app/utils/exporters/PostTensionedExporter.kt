package com.civileg.app.utils.exporters

import android.content.Context
import android.graphics.Bitmap
import com.civileg.app.domain.calculations.base.posttensioned.PostTensionedResult
import com.civileg.app.utils.DxfExporter
import com.civileg.app.utils.DxfDrawable
import com.civileg.app.utils.ExcelExporter
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.BbsRow
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.BoqRow
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.CalculationStep
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.ReportConfig
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.ReportType
import com.civileg.app.utils.exporters.ProfessionalEnglishPdfReporter.SafetyCheck
import java.io.File
import java.util.Locale
import kotlin.math.sqrt

/**
 * Export helpers for post-tensioned members: professional PDF report,
 * DXF drawing (section + elevation) and CSV summary.
 *
 * ADR-009: DXF / PDF output is English only. Layer names are ASCII.
 */
object PostTensionedExporter {

    private fun f(v: Double, decimals: Int = 2): String =
        String.format(Locale.US, "%.${decimals}f", v)

    // ───────────────────────────────────────────────────────────────────────
    // PDF
    // ───────────────────────────────────────────────────────────────────────

    fun exportPdf(
        result: PostTensionedResult,
        outputPath: String,
        drawingBitmap: Bitmap? = null,
        config: ReportConfig = ReportConfig()
    ): File? {
        val i = result.input
        val pd = result.prestressDesign
        val losses = result.losses
        val us = result.ultimateStrength
        val sc = result.stressCheck
        val df = result.deflection

        val inputs = linkedMapOf(
            "Element Type" to i.elementType.displayName,
            "Support Condition" to i.supportType.displayName,
            "Design Code" to i.designCode.version,
            "Span" to "${f(i.span)} m",
            "Section Width" to "${f(i.width, 0)} mm",
            "Section Depth" to "${f(i.depth, 0)} mm",
            "Concrete fcu" to "${f(i.fcu, 1)} MPa",
            "Mild Steel fy" to "${f(i.fy, 1)} MPa",
            "Tendon fpk / fp0.1k" to "${f(i.fpk, 0)} / ${f(i.fp01k, 0)} MPa",
            "Tendon Type" to i.tendonType.description,
            "Number of Tendons" to i.numTendons.toString(),
            "Tendon Area (total)" to "${f(i.tendonTotalArea(), 1)} mm²",
            "Superimposed Dead Load" to "${f(i.deadLoad)} kN/m",
            "Live Load" to "${f(i.liveLoad)} kN/m",
            "Friction / Wobble" to "${f(i.frictionCoefficient)} / ${f(i.wobbleCoefficient, 4)} 1/m",
            "Anchorage Slip" to "${f(i.anchorageSlip, 1)} mm",
            "Exposure Class" to i.exposureClass.displayName,
            "Cover" to "${f(i.cover, 0)} mm"
        )

        val results = linkedMapOf(
            "Required Effective Force" to "${f(pd.requiredForce)} kN",
            "Jacking Force" to "${f(pd.jackingForce)} kN",
            "Initial Stress at Transfer" to "${f(pd.initialStress)} MPa",
            "Effective Stress after Losses" to "${f(pd.effectiveStress)} MPa",
            "Required Eccentricity" to "${f(pd.requiredEccentricity, 1)} mm",
            "Provided Eccentricity" to "${f(pd.providedEccentricity, 1)} mm",
            "Tendon Profile" to pd.tendonProfile.displayName,
            "Total Losses" to "${f(losses.total)} MPa (${f(losses.totalPercent)}%)",
            "Effective Prestress Force" to "${f(losses.effectiveForce)} kN",
            "Flexural Capacity" to "${f(us.flexuralCapacity)} kN·m",
            "Required Moment" to "${f(us.requiredMoment)} kN·m",
            "Shear Capacity" to "${f(us.shearCapacity)} kN",
            "Required Shear" to "${f(us.requiredShear)} kN",
            "Stirrups" to (us.requiredStirrups?.let {
                "Ø${f(it.diameter, 0)} @ ${f(it.spacing, 0)} mm, ${it.legs} legs"
            } ?: "—"),
            "Bursting Reinforcement" to "${f(result.anchorageZone.burstingReinforcement, 0)} mm²",
            "Total Deflection" to "${f(df.totalDeflection)} mm",
            "Allowable Deflection" to "${f(df.allowableDeflection)} mm",
            "Prestress Camber" to "${f(df.preCamber)} mm",
            "Concrete Volume" to "${f(result.quantity.concreteVolume)} m³",
            "PT Tendon Weight" to "${f(result.quantity.ptTendonWeight)} kg",
            "Estimated Cost" to f(result.quantity.totalCost)
        )

        val checks = listOf(
            SafetyCheck(
                "Losses of Prestress", losses.totalPercent, 25.0, "%",
                losses.totalPercent <= 25.0, "ACI 318 / ECP 203"
            ),
            SafetyCheck(
                "Transfer Stress (compression)", sc.transferTopCompression,
                sc.allowableTransferComp, "MPa",
                sc.isTransferSafe, "Serviceability at transfer"
            ),
            SafetyCheck(
                "Service Stress (compression)", sc.serviceTopCompression,
                sc.allowableServiceComp, "MPa",
                sc.isServiceSafe, "Serviceability at service"
            ),
            SafetyCheck(
                "Flexural Capacity", us.requiredMoment, us.flexuralCapacity, "kN·m",
                us.isFlexureSafe, "Ultimate limit state"
            ),
            SafetyCheck(
                "Shear Capacity", us.requiredShear, us.shearCapacity, "kN",
                us.isShearSafe, "Ultimate limit state"
            ),
            SafetyCheck(
                "Anchorage Zone", result.anchorageZone.burstingForce,
                result.anchorageZone.burstingReinforcement, "mm²",
                result.anchorageZone.isAdequate, "Bursting / spalling"
            ),
            SafetyCheck(
                "Deflection", df.totalDeflection, df.allowableDeflection, "mm",
                df.isSafe, "Serviceability limit state"
            )
        )

        val steps = listOf(
            CalculationStep(
                1, "Factored design moment and shear",
                "Mu = wu·L²/coeff ; Vu = wu·L/coeff",
                i.designCode.version,
                "Mu = ${f(us.requiredMoment)} kN·m, Vu = ${f(us.requiredShear)} kN"
            ),
            CalculationStep(
                2, "Jacking stress (initial prestress)",
                "fpj = min(0.80·fpk, 0.90·fp0.1k)",
                i.designCode.version,
                "fpj = ${f(pd.initialStress)} MPa"
            ),
            CalculationStep(
                3, "Time-dependent and friction losses",
                "Δfp = Δf_friction + Δf_slip + Δf_elastic + Δf_creep + Δf_shrink + Δf_relax",
                i.designCode.version,
                "Δfp = ${f(losses.total)} MPa (${f(losses.totalPercent)}%)"
            ),
            CalculationStep(
                4, "Required effective prestress and eccentricity",
                "Pe = Mu/0.9d - ... ; e = (M/P) + (Z/A)",
                i.designCode.version,
                "Pe = ${f(losses.effectiveForce)} kN, e = ${f(pd.providedEccentricity, 1)} mm"
            ),
            CalculationStep(
                5, "Ultimate flexural strength (bonded tendon)",
                "fps = fpu·(1 - γp·ρp·fpu/fc') ; Mn = As·fps·(d - a/2)",
                i.designCode.version,
                "Mn = ${f(us.flexuralCapacity)} kN·m"
            ),
            CalculationStep(
                6, "Anchorage zone bursting reinforcement",
                "Fburst = 0.25·P·(1 - a/h) ; As = Fburst/fs",
                i.designCode.version,
                "As,burst = ${f(result.anchorageZone.burstingReinforcement, 0)} mm²"
            )
        )

        val boq = listOf(
            BoqRow("Concrete", "Post-tensioned concrete", "m³", result.quantity.concreteVolume),
            BoqRow("Mild steel", "Reinforcement (non-prestressed)", "kg", result.quantity.rebarWeight),
            BoqRow("PT strand", "Prestressing tendons", "kg", result.quantity.ptTendonWeight),
            BoqRow("Duct", "Corrugated duct", "kg", result.quantity.ductWeight),
            BoqRow("Anchorage", "Anchor heads and wedges", "kg", result.quantity.anchorageWeight),
            BoqRow("Grout", "Cementitious grout", "m³", result.quantity.groutVolume),
            BoqRow("Formwork", "Formwork area", "m²", result.quantity.formworkArea)
        )

        val bbs = mutableListOf<BbsRow>()
        us.requiredStirrups?.let {
            bbs += BbsRow(
                memberMark = "PT-01",
                barMark = "S1",
                diameter = it.diameter.toInt(),
                shapeCode = 51,
                count = (i.span * 1000 / it.spacing.coerceAtLeast(1.0)).toInt() + 1,
                totalLengthMm = 2 * (i.width + i.depth),
                weightKg = 0.0
            )
        }

        return ProfessionalEnglishPdfReporter.generateReport(
            reportType = ReportType.GENERIC,
            title = "Post-Tensioned ${i.elementType.displayName} Design",
            subtitle = "Prestressed Concrete Member — ${i.designCode.displayName}",
            designCode = i.designCode.version,
            inputs = inputs,
            results = results,
            safetyChecks = checks,
            isSafe = result.isSafe,
            drawingBitmap = drawingBitmap,
            calculationSteps = steps,
            bbsRows = bbs,
            boqRows = boq,
            config = config,
            outputPath = outputPath
        )
    }

    // ───────────────────────────────────────────────────────────────────────
    // DXF
    // ───────────────────────────────────────────────────────────────────────

    fun exportDxf(result: PostTensionedResult, outputPath: String): File? {
        val i = result.input
        val drawing = DxfDrawable { ex ->
            val w = i.width
            val d = i.depth
            val spanMm = i.span * 1000.0
            val eMid = result.prestressDesign.providedEccentricity.coerceIn(0.0, d - i.cover)
            val ductR = (sqrt(i.tendonArea) / 2.0).coerceIn(15.0, 50.0)
            val endY = i.cover + ductR
            val elevationBase = d + 1200.0

            // ── Section (left) ──
            ex.addPolyline(
                listOf(0.0 to 0.0, w to 0.0, w to d, 0.0 to d),
                "BEAMS", closed = true
            )
            val ductXs = if (i.numTendons <= 1) listOf(w / 2.0)
            else List(i.numTendons) { k -> w * (k + 1) / (i.numTendons + 1) }
            ductXs.forEach { x ->
                ex.addCircle(x, eMid, ductR, "STIRRUPS")
                ex.addCircle(x, eMid, ductR * 1.35, "STIRRUPS")
            }
            // Stirrup outline
            val inset = i.cover
            ex.addPolyline(
                listOf(
                    inset to inset, w - inset to inset,
                    w - inset to d - inset, inset to d - inset
                ),
                "REBAR", closed = true
            )
            ex.addText(w / 2.0, d + 120.0, "SECTION", 90.0, "TEXT")
            ex.addText(w + 120.0, d / 2.0, "b = ${f(w, 0)}", 70.0, "DIMENSIONS", 90.0)
            ex.addText(-350.0, d / 2.0, "h = ${f(d, 0)}", 70.0, "DIMENSIONS", 90.0)
            ex.addText(w / 2.0, -180.0, "e = ${f(eMid, 0)} mm", 70.0, "DIMENSIONS")

            // ── Elevation (right, above) ──
            val y0 = elevationBase
            ex.addPolyline(
                listOf(0.0 to y0, spanMm to y0, spanMm to y0 + d, 0.0 to y0 + d),
                "BEAMS", closed = true
            )
            val tendonPts = mutableListOf<Pair<Double, Double>>()
            val nSeg = 40
            for (k in 0..nSeg) {
                val x = spanMm * k / nSeg
                val t = x / spanMm
                val y = endY + (eMid - endY) * 4.0 * t * (1.0 - t)
                tendonPts += x to (y0 + y)
            }
            ex.addPolyline(tendonPts, "REBAR")
            ex.addCircle(0.0, y0 + endY, ductR * 1.5, "STIRRUPS")
            ex.addCircle(spanMm, y0 + endY, ductR * 1.5, "STIRRUPS")
            ex.addText(spanMm / 2.0, y0 + d + 120.0, "ELEVATION — TENDON PROFILE", 90.0, "TEXT")
            ex.addText(spanMm / 2.0, y0 - 220.0, "L = ${f(i.span)} m", 70.0, "DIMENSIONS")
            ex.addText(
                spanMm / 2.0, y0 - 420.0,
                "${i.numTendons} × ${i.tendonType.description} — Pe = ${f(result.losses.effectiveForce)} kN",
                70.0, "TEXT"
            )
            ex.addText(
                0.0, y0 - 620.0,
                "${i.designCode.version} — ${i.elementType.displayName} — ${i.supportType.displayName}",
                70.0, "TEXT"
            )
            ex.addText(
                0.0, y0 - 800.0,
                "Concrete fcu = ${f(i.fcu, 0)} MPa — Tendon fpk = ${f(i.fpk, 0)} MPa — Losses = ${f(result.losses.totalPercent)}%",
                70.0, "TEXT"
            )
        }
        return DxfExporter().exportDxf(drawing, outputPath)
    }

    // ───────────────────────────────────────────────────────────────────────
    // CSV
    // ───────────────────────────────────────────────────────────────────────

    fun exportCsv(
        context: Context,
        result: PostTensionedResult,
        fileName: String = "post_tensioned_results.csv"
    ): File? {
        val i = result.input
        val pd = result.prestressDesign
        val sb = StringBuilder()
        fun row(vararg cells: String) {
            sb.append(cells.joinToString(",") { c ->
                if (c.any { it == ',' || it == '"' || it == '\n' }) {
                    "\"" + c.replace("\"", "\"\"") + "\""
                } else c
            })
            sb.append("\r\n")
        }

        row("Post-Tensioned Design Summary")
        row("Property", "Value", "Unit")
        row("Element", i.elementType.displayName, "")
        row("Support", i.supportType.displayName, "")
        row("Design Code", i.designCode.version, "")
        row("Span", f(i.span), "m")
        row("Width", f(i.width, 0), "mm")
        row("Depth", f(i.depth, 0), "mm")
        row("fcu", f(i.fcu, 1), "MPa")
        row("fy", f(i.fy, 1), "MPa")
        row("fpk", f(i.fpk, 0), "MPa")
        row("fp0.1k", f(i.fp01k, 0), "MPa")
        row("Tendon type", i.tendonType.description, "")
        row("Number of tendons", i.numTendons.toString(), "")
        row("Total tendon area", f(i.tendonTotalArea(), 1), "mm2")
        row("Required effective force", f(pd.requiredForce), "kN")
        row("Jacking force", f(pd.jackingForce), "kN")
        row("Initial stress", f(pd.initialStress), "MPa")
        row("Effective stress", f(pd.effectiveStress), "MPa")
        row("Required eccentricity", f(pd.requiredEccentricity, 1), "mm")
        row("Provided eccentricity", f(pd.providedEccentricity, 1), "mm")
        row("Total losses", f(result.losses.total), "MPa")
        row("Total losses percent", f(result.losses.totalPercent), "%")
        row("Flexural capacity", f(result.ultimateStrength.flexuralCapacity), "kN.m")
        row("Required moment", f(result.ultimateStrength.requiredMoment), "kN.m")
        row("Shear capacity", f(result.ultimateStrength.shearCapacity), "kN")
        row("Required shear", f(result.ultimateStrength.requiredShear), "kN")
        row("Total deflection", f(result.deflection.totalDeflection), "mm")
        row("Allowable deflection", f(result.deflection.allowableDeflection), "mm")
        row("Prestress camber", f(result.deflection.preCamber), "mm")
        row("Concrete volume", f(result.quantity.concreteVolume), "m3")
        row("PT tendon weight", f(result.quantity.ptTendonWeight), "kg")
        row("Estimated cost", f(result.quantity.totalCost), "")
        row("Overall status", if (result.isSafe) "SAFE" else "UNSAFE", "")
        result.warnings.forEach { row("Warning", it, "") }

        return ExcelExporter.exportTextCsv(context, fileName, sb.toString())
    }
}
