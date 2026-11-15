package com.civileg.core.output

import com.civileg.core.output.dxf.DxfEngine
import com.civileg.core.output.pdf.PdfReportEngine
import com.civileg.core.sanity.EngineeringSanityEngine
import com.civileg.core.sanity.SanityReport

/**
 * StructuralReportExporter — Pure Kotlin unified facade for exporting PDF calculation sheets
 * and DXF CAD drawings for all structural elements (Beams, Columns, Footings, Slabs, Steel Members).
 * Zero UI/Android dependencies.
 */
object StructuralReportExporter {

    // =========================================================================
    // 1. BEAM EXPORT (PDF & DXF)
    // =========================================================================

    data class BeamExportData(
        val elementName: String = "B1",
        val designCode: String = "ECP 203",
        val bMm: Double,
        val hMm: Double,
        val spanM: Double,
        val fcuMPa: Double,
        val fyMPa: Double,
        val muKNm: Double,
        val vuKN: Double,
        val asReqMm2: Double,
        val asProvText: String,
        val asProvMm2: Double,
        val stirrupsText: String = "5Φ8/m",
        val deflectionMm: Double = 0.0,
        val deflectionLimitMm: Double = 0.0
    )

    fun generateBeamDxf(data: BeamExportData): String {
        val sb = StringBuilder()
        sb.append(DxfEngine.headerSection())

        val layers = listOf(
            DxfEngine.LAYER_ECP_CONCRETE,
            DxfEngine.LAYER_ECP_REBAR,
            DxfEngine.LAYER_ECP_DIMENSION,
            DxfEngine.LAYER_DEFAULT
        )
        sb.append(DxfEngine.layerTable(layers))
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val b = data.bMm
        val h = data.hMm
        val concreteLayer = DxfEngine.layerForCode(data.designCode, "CONCRETE")
        val rebarLayer = DxfEngine.layerForCode(data.designCode, "REBAR")
        val dimLayer = DxfEngine.layerForCode(data.designCode, "DIM")

        // Concrete Rectangle (0,0) -> (b, h)
        sb.append(DxfEngine.lineEntity(0.0, 0.0, b, 0.0, concreteLayer))
        sb.append(DxfEngine.lineEntity(b, 0.0, b, h, concreteLayer))
        sb.append(DxfEngine.lineEntity(b, h, 0.0, h, concreteLayer))
        sb.append(DxfEngine.lineEntity(0.0, h, 0.0, 0.0, concreteLayer))

        // Stirrups (Cover = 50mm)
        val cover = 50.0
        val stX1 = cover
        val stY1 = cover
        val stX2 = b - cover
        val stY2 = h - cover
        sb.append(DxfEngine.lineEntity(stX1, stY1, stX2, stY1, rebarLayer))
        sb.append(DxfEngine.lineEntity(stX2, stY1, stX2, stY2, rebarLayer))
        sb.append(DxfEngine.lineEntity(stX2, stY2, stX1, stY2, rebarLayer))
        sb.append(DxfEngine.lineEntity(stX1, stY2, stX1, stY1, rebarLayer))

        // Corner Rebar
        sb.append(DxfEngine.circleEntity(stX1 + 10, stY1 + 10, 10.0, rebarLayer))
        sb.append(DxfEngine.circleEntity(stX2 - 10, stY1 + 10, 10.0, rebarLayer))

        // Text Labels
        sb.append(DxfEngine.textEntity("BEAM ${data.elementName} (${data.bMm.toInt()}x${data.hMm.toInt()})", 0.0, h + 80.0, 30.0, dimLayer))
        sb.append(DxfEngine.textEntity("Main Steel: ${data.asProvText}", 0.0, h + 40.0, 20.0, dimLayer))
        sb.append(DxfEngine.textEntity("Stirrups: ${data.stirrupsText}", 0.0, h + 15.0, 20.0, dimLayer))

        sb.append("0\nENDSEC\n")
        sb.append("0\nEOF\n")
        return sb.toString()
    }

    fun generateBeamPdf(data: BeamExportData): String {
        val sanity = EngineeringSanityEngine.checkBeam(
            bMm = data.bMm, hMm = data.hMm, spanM = data.spanM,
            asReqMm2 = data.asReqMm2, asProvMm2 = data.asProvMm2,
            shearVuKN = data.vuKN, shearVcKN = data.vuKN * 1.5,
            deflectionMm = data.deflectionMm, deflectionLimitMm = data.deflectionLimitMm
        )

        val statusText = if (sanity.hasError) "UNSAFE / FAIL" else "SAFE / PASS"

        val sections = listOf(
            PdfReportEngine.PdfSection(
                titleAr = "1. بيانات العنصر الإنشائي والمواد (Geometry & Materials)",
                titleEn = "1. Geometry & Material Properties",
                contentAr = "المقطع: ${data.bMm.toInt()}x${data.hMm.toInt()} مم | البحر: ${data.spanM} م | fcu = ${data.fcuMPa} MPa | fy = ${data.fyMPa} MPa",
                contentEn = "Section: ${data.bMm.toInt()}x${data.hMm.toInt()} mm | Span: ${data.spanM} m | fcu = ${data.fcuMPa} MPa | fy = ${data.fyMPa} MPa"
            ),
            PdfReportEngine.PdfSection(
                titleAr = "2. الأحمال والقوى التصميمية (Design Actions)",
                titleEn = "2. Design Actions & Load Combinations",
                contentAr = "عزم الانحناء (Mu): ${data.muKNm} kN.m | قوى القص (Vu): ${data.vuKN} kN",
                contentEn = "Bending Moment (Mu): ${data.muKNm} kN.m | Shear Force (Vu): ${data.vuKN} kN"
            ),
            PdfReportEngine.PdfSection(
                titleAr = "3. حسابات التسليح والتحقق من السلامة (Reinforcement & Check)",
                titleEn = "3. Reinforcement & Safety Check",
                contentAr = "التسليح المطلوب: ${data.asReqMm2.toInt()} مم² | التسليح الموفر: ${data.asProvText} (${data.asProvMm2.toInt()} مم²) | الحالة: $statusText",
                contentEn = "As Req: ${data.asReqMm2.toInt()} mm² | As Prov: ${data.asProvText} (${data.asProvMm2.toInt()} mm²) | Status: $statusText"
            )
        )

        return PdfReportEngine.generateReport(
            titleAr = "مذكرة حسابية لتصميم كمرة خرسانية (${data.elementName}) - ${data.designCode}",
            titleEn = "Structural Calculation Sheet - Beam (${data.elementName}) - ${data.designCode}",
            sections = sections
        )
    }

    // =========================================================================
    // 2. COLUMN EXPORT (PDF & DXF)
    // =========================================================================

    data class ColumnExportData(
        val elementName: String = "C1",
        val designCode: String = "ECP 203",
        val bMm: Double,
        val tMm: Double,
        val lengthM: Double,
        val fcuMPa: Double,
        val fyMPa: Double,
        val puKN: Double,
        val puCapacityKN: Double,
        val muKNm: Double = 0.0,
        val slendernessLambda: Double = 0.0,
        val asProvText: String,
        val asProvMm2: Double,
        val tiesText: String = "5Φ8/m"
    )

    fun generateColumnDxf(data: ColumnExportData): String {
        val sb = StringBuilder()
        sb.append(DxfEngine.headerSection())

        val layers = listOf(
            DxfEngine.LAYER_ECP_CONCRETE,
            DxfEngine.LAYER_ECP_REBAR,
            DxfEngine.LAYER_ECP_DIMENSION,
            DxfEngine.LAYER_DEFAULT
        )
        sb.append(DxfEngine.layerTable(layers))
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val b = data.bMm
        val t = data.tMm
        val concreteLayer = DxfEngine.layerForCode(data.designCode, "CONCRETE")
        val rebarLayer = DxfEngine.layerForCode(data.designCode, "REBAR")
        val dimLayer = DxfEngine.layerForCode(data.designCode, "DIM")

        // Column Cross-Section Outline (0,0) -> (b, t)
        sb.append(DxfEngine.lineEntity(0.0, 0.0, b, 0.0, concreteLayer))
        sb.append(DxfEngine.lineEntity(b, 0.0, b, t, concreteLayer))
        sb.append(DxfEngine.lineEntity(b, t, 0.0, t, concreteLayer))
        sb.append(DxfEngine.lineEntity(0.0, t, 0.0, 0.0, concreteLayer))

        // Ties / Stirrup (Cover = 25mm for columns)
        val cover = 25.0
        val stX1 = cover
        val stY1 = cover
        val stX2 = b - cover
        val stY2 = t - cover
        sb.append(DxfEngine.lineEntity(stX1, stY1, stX2, stY1, rebarLayer))
        sb.append(DxfEngine.lineEntity(stX2, stY1, stX2, stY2, rebarLayer))
        sb.append(DxfEngine.lineEntity(stX2, stY2, stX1, stY2, rebarLayer))
        sb.append(DxfEngine.lineEntity(stX1, stY2, stX1, stY1, rebarLayer))

        // 4 Corner Longitudinal Rebar
        sb.append(DxfEngine.circleEntity(stX1 + 8, stY1 + 8, 8.0, rebarLayer))
        sb.append(DxfEngine.circleEntity(stX2 - 8, stY1 + 8, 8.0, rebarLayer))
        sb.append(DxfEngine.circleEntity(stX2 - 8, stY2 - 8, 8.0, rebarLayer))
        sb.append(DxfEngine.circleEntity(stX1 + 8, stY2 - 8, 8.0, rebarLayer))

        // Text Labels
        sb.append(DxfEngine.textEntity("COLUMN ${data.elementName} (${data.bMm.toInt()}x${data.tMm.toInt()})", 0.0, t + 80.0, 30.0, dimLayer))
        sb.append(DxfEngine.textEntity("Rebar: ${data.asProvText}", 0.0, t + 40.0, 20.0, dimLayer))
        sb.append(DxfEngine.textEntity("Ties: ${data.tiesText}", 0.0, t + 15.0, 20.0, dimLayer))

        sb.append("0\nENDSEC\n")
        sb.append("0\nEOF\n")
        return sb.toString()
    }

    fun generateColumnPdf(data: ColumnExportData): String {
        val sanity = EngineeringSanityEngine.checkColumn(
            bMm = data.bMm, tMm = data.tMm, lengthM = data.lengthM,
            puKN = data.puKN, puCapacityKN = data.puCapacityKN,
            slendernessLambda = data.slendernessLambda, asProvMm2 = data.asProvMm2
        )

        val statusText = if (sanity.hasError) "UNSAFE / FAIL" else "SAFE / PASS"

        val sections = listOf(
            PdfReportEngine.PdfSection(
                titleAr = "1. بيانات العمود والمواد (Column Geometry & Materials)",
                titleEn = "1. Column Geometry & Material Properties",
                contentAr = "المقطع: ${data.bMm.toInt()}x${data.tMm.toInt()} مم | الارتفاع: ${data.lengthM} م | fcu = ${data.fcuMPa} MPa | fy = ${data.fyMPa} MPa",
                contentEn = "Section: ${data.bMm.toInt()}x${data.tMm.toInt()} mm | Height: ${data.lengthM} m | fcu = ${data.fcuMPa} MPa | fy = ${data.fyMPa} MPa"
            ),
            PdfReportEngine.PdfSection(
                titleAr = "2. الأحمال والنحافة التصميمية (Design Loads & Slenderness)",
                titleEn = "2. Design Loads & Slenderness Check",
                contentAr = "الحمل المحوري (Pu): ${data.puKN} kN | السعة القصوى (Pu Capacity): ${data.puCapacityKN} kN | معامل النحافة (λ): ${String.format("%.1f", data.slendernessLambda)}",
                contentEn = "Axial Load (Pu): ${data.puKN} kN | Capacity: ${data.puCapacityKN} kN | Slenderness (λ): ${String.format("%.1f", data.slendernessLambda)}"
            ),
            PdfReportEngine.PdfSection(
                titleAr = "3. التسليح والسلامة الإنشائية (Reinforcement & Safety)",
                titleEn = "3. Reinforcement & Safety Check",
                contentAr = "التسليح الموفر: ${data.asProvText} (${data.asProvMm2.toInt()} مم²) | الكانات: ${data.tiesText} | الحالة: $statusText",
                contentEn = "Reinforcement: ${data.asProvText} (${data.asProvMm2.toInt()} mm²) | Ties: ${data.tiesText} | Status: $statusText"
            )
        )

        return PdfReportEngine.generateReport(
            titleAr = "مذكرة حسابية لتصميم عمود خرساني (${data.elementName}) - ${data.designCode}",
            titleEn = "Structural Calculation Sheet - Column (${data.elementName}) - ${data.designCode}",
            sections = sections
        )
    }

    // =========================================================================
    // 3. FOOTING EXPORT (PDF & DXF)
    // =========================================================================

    data class FootingExportData(
        val elementName: String = "F1",
        val designCode: String = "ECP 203",
        val lengthMm: Double,
        val widthMm: Double,
        val thicknessMm: Double,
        val fcuMPa: Double,
        val fyMPa: Double,
        val columnPuKN: Double,
        val qAllowableKgCm2: Double,
        val qActualKgCm2: Double,
        val rebarXText: String,
        val rebarYText: String
    )

    fun generateFootingDxf(data: FootingExportData): String {
        val sb = StringBuilder()
        sb.append(DxfEngine.headerSection())

        val layers = listOf(
            DxfEngine.LAYER_ECP_CONCRETE,
            DxfEngine.LAYER_ECP_REBAR,
            DxfEngine.LAYER_ECP_DIMENSION,
            DxfEngine.LAYER_DEFAULT
        )
        sb.append(DxfEngine.layerTable(layers))
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val l = data.lengthMm
        val w = data.widthMm
        val concreteLayer = DxfEngine.layerForCode(data.designCode, "CONCRETE")
        val rebarLayer = DxfEngine.layerForCode(data.designCode, "REBAR")
        val dimLayer = DxfEngine.layerForCode(data.designCode, "DIM")

        // Footing Plan Outline (0,0) -> (l, w)
        sb.append(DxfEngine.lineEntity(0.0, 0.0, l, 0.0, concreteLayer))
        sb.append(DxfEngine.lineEntity(l, 0.0, l, w, concreteLayer))
        sb.append(DxfEngine.lineEntity(l, w, 0.0, w, concreteLayer))
        sb.append(DxfEngine.lineEntity(0.0, w, 0.0, 0.0, concreteLayer))

        // Center Column (assuming 300x500 at center)
        val colW = 300.0
        val colL = 500.0
        val cX1 = (l - colL) / 2.0
        val cY1 = (w - colW) / 2.0
        val cX2 = cX1 + colL
        val cY2 = cY1 + colW
        sb.append(DxfEngine.lineEntity(cX1, cY1, cX2, cY1, concreteLayer))
        sb.append(DxfEngine.lineEntity(cX2, cY1, cX2, cY2, concreteLayer))
        sb.append(DxfEngine.lineEntity(cX2, cY2, cX1, cY2, concreteLayer))
        sb.append(DxfEngine.lineEntity(cX1, cY2, cX1, cY1, concreteLayer))

        // Rebar Mesh Lines
        val cover = 70.0 // Footing cover
        sb.append(DxfEngine.lineEntity(cover, cover, l - cover, cover, rebarLayer))
        sb.append(DxfEngine.lineEntity(cover, cover, cover, w - cover, rebarLayer))

        // Labels
        sb.append(DxfEngine.textEntity("FOOTING ${data.elementName} (${data.lengthMm.toInt()}x${data.widthMm.toInt()}x${data.thicknessMm.toInt()})", 0.0, w + 100.0, 40.0, dimLayer))
        sb.append(DxfEngine.textEntity("Rebar Dir-X: ${data.rebarXText}", 0.0, w + 50.0, 25.0, dimLayer))
        sb.append(DxfEngine.textEntity("Rebar Dir-Y: ${data.rebarYText}", 0.0, w + 20.0, 25.0, dimLayer))

        sb.append("0\nENDSEC\n")
        sb.append("0\nEOF\n")
        return sb.toString()
    }

    fun generateFootingPdf(data: FootingExportData): String {
        val isSafeBearing = data.qActualKgCm2 <= data.qAllowableKgCm2
        val statusText = if (isSafeBearing) "SAFE / PASS" else "UNSAFE (Soil Overstressed)"

        val sections = listOf(
            PdfReportEngine.PdfSection(
                titleAr = "1. أبعاد القاعدة وإجهاد التربة (Footing Geometry & Soil Capacity)",
                titleEn = "1. Footing Geometry & Soil Bearing",
                contentAr = "الأبعاد: ${data.lengthMm.toInt()}x${data.widthMm.toInt()}x${data.thicknessMm.toInt()} مم | الإجهاد المسموح: ${data.qAllowableKgCm2} kg/cm² | الإجهاد الفعلي: ${data.qActualKgCm2} kg/cm²",
                contentEn = "Dimensions: ${data.lengthMm.toInt()}x${data.widthMm.toInt()}x${data.thicknessMm.toInt()} mm | q_allow: ${data.qAllowableKgCm2} kg/cm² | q_act: ${data.qActualKgCm2} kg/cm²"
            ),
            PdfReportEngine.PdfSection(
                titleAr = "2. أحمال العمود والتسليح (Column Load & Reinforcement)",
                titleEn = "2. Column Load & Reinforcement Mesh",
                contentAr = "حمل العمود (Pu): ${data.columnPuKN} kN | تسليح الاتجاه الطولي: ${data.rebarXText} | تسليح الاتجاه العرضي: ${data.rebarYText} | الحالة: $statusText",
                contentEn = "Column Load (Pu): ${data.columnPuKN} kN | Dir-X Mesh: ${data.rebarXText} | Dir-Y Mesh: ${data.rebarYText} | Status: $statusText"
            )
        )

        return PdfReportEngine.generateReport(
            titleAr = "مذكرة حسابية لتصميم قاعدة خرسانية منفصلة (${data.elementName}) - ${data.designCode}",
            titleEn = "Structural Calculation Sheet - Footing (${data.elementName}) - ${data.designCode}",
            sections = sections
        )
    }

    // =========================================================================
    // 4. STEEL MEMBER EXPORT (PDF & DXF)
    // =========================================================================

    data class SteelExportData(
        val elementName: String = "S1",
        val designCode: String = "AISC 360",
        val sectionName: String = "IPE 300",
        val lengthM: Double,
        val puKN: Double = 0.0,
        val muKNm: Double = 0.0,
        val maxDeflectionMm: Double,
        val allowDeflectionMm: Double,
        val utilizationRatio: Double
    )

    fun generateSteelDxf(data: SteelExportData): String {
        val sb = StringBuilder()
        sb.append(DxfEngine.headerSection())

        val layers = listOf(
            "STEEL_SECTION",
            "DIMENSION",
            DxfEngine.LAYER_DEFAULT
        )
        sb.append(DxfEngine.layerTable(layers))
        sb.append("0\nSECTION\n2\nENTITIES\n")

        val steelLayer = "STEEL_SECTION"
        val dimLayer = "DIMENSION"

        // Draw I-beam outline at (0,0) — IPE 300 (300x150)
        val h = 300.0
        val b = 150.0
        val tf = 10.7
        val tw = 7.1

        // Top Flange
        sb.append(DxfEngine.lineEntity(-b/2, h/2, b/2, h/2, steelLayer))
        sb.append(DxfEngine.lineEntity(b/2, h/2, b/2, h/2 - tf, steelLayer))
        sb.append(DxfEngine.lineEntity(b/2, h/2 - tf, tw/2, h/2 - tf, steelLayer))

        // Web Right
        sb.append(DxfEngine.lineEntity(tw/2, h/2 - tf, tw/2, -h/2 + tf, steelLayer))

        // Bottom Flange Right
        sb.append(DxfEngine.lineEntity(tw/2, -h/2 + tf, b/2, -h/2 + tf, steelLayer))
        sb.append(DxfEngine.lineEntity(b/2, -h/2 + tf, b/2, -h/2, steelLayer))
        sb.append(DxfEngine.lineEntity(b/2, -h/2, -b/2, -h/2, steelLayer))

        // Text Labels
        sb.append(DxfEngine.textEntity("STEEL MEMBER ${data.elementName} (${data.sectionName})", -b/2, h/2 + 50.0, 30.0, dimLayer))
        sb.append(DxfEngine.textEntity("Code: ${data.designCode} | Utilization: ${String.format("%.2f", data.utilizationRatio)}", -b/2, h/2 + 20.0, 20.0, dimLayer))

        sb.append("0\nENDSEC\n")
        sb.append("0\nEOF\n")
        return sb.toString()
    }

    fun generateSteelPdf(data: SteelExportData): String {
        val isSafe = data.utilizationRatio <= 1.0 && data.maxDeflectionMm <= data.allowDeflectionMm
        val statusText = if (isSafe) "SAFE / PASS" else "UNSAFE / OVER-STRESSED"

        val sections = listOf(
            PdfReportEngine.PdfSection(
                titleAr = "1. بيانات القطاع والقوى المطبقة (Steel Section & Loads)",
                titleEn = "1. Steel Section & Applied Actions",
                contentAr = "القطاع: ${data.sectionName} | الطول: ${data.lengthM} م | Pu = ${data.puKN} kN | Mu = ${data.muKNm} kN.m",
                contentEn = "Section: ${data.sectionName} | Length: ${data.lengthM} m | Pu = ${data.puKN} kN | Mu = ${data.muKNm} kN.m"
            ),
            PdfReportEngine.PdfSection(
                titleAr = "2. فحص الترخيم ونسبة الاستغلال (Deflection & Utilization Check)",
                titleEn = "2. Deflection & Capacity Utilization",
                contentAr = "الترخيم الفعلي: ${String.format("%.2f", data.maxDeflectionMm)} mm | الترخيم المسموح: ${String.format("%.2f", data.allowDeflectionMm)} mm | نسبة الاستغلال: ${String.format("%.2f", data.utilizationRatio)} | الحالة: $statusText",
                contentEn = "Actual Defl: ${String.format("%.2f", data.maxDeflectionMm)} mm | Allow Defl: ${String.format("%.2f", data.allowDeflectionMm)} mm | Utilization: ${String.format("%.2f", data.utilizationRatio)} | Status: $statusText"
            )
        )

        return PdfReportEngine.generateReport(
            titleAr = "مذكرة حسابية لتصميم عنصر معدني (${data.elementName}) - ${data.designCode}",
            titleEn = "Structural Calculation Sheet - Steel Member (${data.elementName}) - ${data.designCode}",
            sections = sections
        )
    }
}
