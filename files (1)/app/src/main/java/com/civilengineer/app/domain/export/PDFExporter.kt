package com.civilengineer.app.domain.export

import android.content.Context
import com.civilengineer.app.data.models.ColumnDesign
import com.civilengineer.app.data.models.SlabDesign
import com.civilengineer.app.data.models.FootingDesign
import com.civilengineer.app.data.models.RetainingWall
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.properties.BorderConstants
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * فئة تصدير التقارير إلى صيغة PDF
 */
class PDFExporter(private val context: Context) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("ar", "EG"))

    /**
     * تصدير تقرير العمود إلى PDF
     */
    fun exportColumnDesignToPDF(column: ColumnDesign): File {
        val fileName = "Column_${column.columnName}_${System.currentTimeMillis()}.pdf"
        val outputFile = File(context.getExternalFilesDir(null), fileName)
        
        PdfWriter(FileOutputStream(outputFile)).use { writer ->
            Document(PdfWriter(writer)).use { document ->
                // العنوان
                val titleFont = PdfFontFactory.createFont()
                document.add(
                    Paragraph("تقرير تصميم العمود الخرساني")
                        .setFont(titleFont)
                        .setFontSize(20f)
                        .setTextAlignment(TextAlignment.CENTER)
                )
                
                document.add(Paragraph("\n"))

                // معلومات المشروع
                document.add(
                    Paragraph("معلومات العمود")
                        .setFontSize(14f)
                        .setBold()
                )

                val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                infoTable.addCell(createCell("اسم العمود"))
                infoTable.addCell(createCell(column.columnName))
                infoTable.addCell(createCell("التاريخ"))
                infoTable.addCell(createCell(dateFormat.format(Date(column.createdDate))))
                infoTable.addCell(createCell("الكود المستخدم"))
                infoTable.addCell(createCell(column.codeType.name))
                infoTable.addCell(createCell("نوع العمود"))
                infoTable.addCell(createCell(column.columnType.name))
                document.add(infoTable)

                document.add(Paragraph("\n"))

                // الأبعاد
                document.add(
                    Paragraph("الأبعاد والحجم")
                        .setFontSize(14f)
                        .setBold()
                )

                val dimensionsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                dimensionsTable.addCell(createCell("الطول (م)"))
                dimensionsTable.addCell(createCell("%.3f".format(column.lengthM)))
                dimensionsTable.addCell(createCell("العرض (م)"))
                dimensionsTable.addCell(createCell("%.3f".format(column.widthM)))
                dimensionsTable.addCell(createCell("الارتفاع (م)"))
                dimensionsTable.addCell(createCell("%.3f".format(column.heightM)))
                dimensionsTable.addCell(createCell("حجم الخرسانة (م³)"))
                dimensionsTable.addCell(createCell("%.3f".format(column.getConcreteVolume())))
                document.add(dimensionsTable)

                document.add(Paragraph("\n"))

                // الأحمال
                document.add(
                    Paragraph("الأحمال والعزوم")
                        .setFontSize(14f)
                        .setBold()
                )

                val loadsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                loadsTable.addCell(createCell("الحمل المحوري (kN)"))
                loadsTable.addCell(createCell("%.2f".format(column.axialLoadKN)))
                loadsTable.addCell(createCell("عزم الانحناء (kN.m)"))
                loadsTable.addCell(createCell("%.2f".format(column.bendingMomentKNM)))
                document.add(loadsTable)

                document.add(Paragraph("\n"))

                // خصائص المواد
                document.add(
                    Paragraph("خصائص المواد")
                        .setFontSize(14f)
                        .setBold()
                )

                val materialsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                materialsTable.addCell(createCell("درجة الخرسانة"))
                materialsTable.addCell(createCell(column.concreteGrade))
                materialsTable.addCell(createCell("درجة الفولاذ"))
                materialsTable.addCell(createCell(column.steelGrade))
                materialsTable.addCell(createCell("غطاء خرساني (ملم)"))
                materialsTable.addCell(createCell("%d".format(column.coverMM)))
                document.add(materialsTable)

                document.add(Paragraph("\n"))

                // النتائج
                document.add(
                    Paragraph("نتائج التصميم")
                        .setFontSize(14f)
                        .setBold()
                )

                val resultsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                resultsTable.addCell(createCell("مساحة الفولاذ الرئيسي (mm²)"))
                resultsTable.addCell(createCell("%.2f".format(column.mainSteelAreaMM2)))
                resultsTable.addCell(createCell("قطر الفولاذ الرئيسي (ملم)"))
                resultsTable.addCell(createCell("%d".format(column.mainSteelDiaMM)))
                resultsTable.addCell(createCell("تباعد الكانات (ملم)"))
                resultsTable.addCell(createCell("%d".format(column.stirrupsSpacingMM)))
                resultsTable.addCell(createCell("نسبة الفولاذ (%)"))
                resultsTable.addCell(createCell("%.2f".format(column.mainSteelPercentage)))
                resultsTable.addCell(createCell("النسبة الرشاقة"))
                resultsTable.addCell(createCell("%.2f".format(column.slendernessRatio)))
                resultsTable.addCell(createCell("معامل التخفيض"))
                resultsTable.addCell(createCell("%.3f".format(column.reductionFactor)))
                document.add(resultsTable)

                document.add(Paragraph("\n"))

                // التحمل
                document.add(
                    Paragraph("التحمل والأمان")
                        .setFontSize(14f)
                        .setBold()
                )

                val capacityTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                capacityTable.addCell(createCell("تحمل الخرسانة (kN)"))
                capacityTable.addCell(createCell("%.2f".format(column.concreteCapacityKN)))
                capacityTable.addCell(createCell("تحمل الفولاذ (kN)"))
                capacityTable.addCell(createCell("%.2f".format(column.steelCapacityKN)))
                capacityTable.addCell(createCell("التحمل الكلي (kN)"))
                capacityTable.addCell(createCell("%.2f".format(column.totalCapacityKN)))
                capacityTable.addCell(createCell("معامل الأمان"))
                capacityTable.addCell(createCell("%.2f".format(column.safetyFactor)))
                capacityTable.addCell(createCell("الحالة"))
                capacityTable.addCell(
                    createCell(
                        if (column.isSafe) "آمن ✓" else "غير آمن ✗",
                        if (column.isSafe) ColorConstants.GREEN else ColorConstants.RED
                    )
                )
                document.add(capacityTable)

                document.add(Paragraph("\n"))

                // التكاليف
                document.add(
                    Paragraph("تحليل التكاليف")
                        .setFontSize(14f)
                        .setBold()
                )

                val costsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                costsTable.addCell(createCell("وزن الفولاذ (طن)"))
                costsTable.addCell(createCell("%.2f".format(column.getSteelWeightTons())))
                costsTable.addCell(createCell("سعر الفولاذ/طن"))
                costsTable.addCell(createCell("%.2f".format(column.costPerTonSteel)))
                costsTable.addCell(createCell("سعر الخرسانة/م³"))
                costsTable.addCell(createCell("%.2f".format(column.costPerM3Concrete)))
                costsTable.addCell(createCell("التكلفة الإجمالية"))
                costsTable.addCell(createCell("%.2f %s".format(column.totalCost, column.unitCurrency)))
                document.add(costsTable)

                document.add(Paragraph("\n"))

                // الملاحظات
                if (column.notes.isNotEmpty()) {
                    document.add(
                        Paragraph("ملاحظات")
                            .setFontSize(14f)
                            .setBold()
                    )
                    document.add(Paragraph(column.notes))
                }

                document.add(Paragraph("\n"))

                // التوقيع والتاريخ
                document.add(
                    Paragraph("تم إنشاء هذا التق��ير بواسطة تطبيق مساعد المهندس المدني\n")
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontSize(10f)
                )
                document.add(
                    Paragraph(dateFormat.format(Date()))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontSize(10f)
                )
            }
        }

        return outputFile
    }

    /**
     * تصدير تقرير البلاطة إلى PDF
     */
    fun exportSlabDesignToPDF(slab: SlabDesign): File {
        val fileName = "Slab_${slab.slabName}_${System.currentTimeMillis()}.pdf"
        val outputFile = File(context.getExternalFilesDir(null), fileName)

        PdfWriter(FileOutputStream(outputFile)).use { writer ->
            Document(PdfWriter(writer)).use { document ->
                document.add(
                    Paragraph("تقرير تصميم البلاطة الخرسانية")
                        .setFontSize(20f)
                        .setTextAlignment(TextAlignment.CENTER)
                )
                document.add(Paragraph("\n"))

                // معلومات البلاطة
                document.add(Paragraph("معلومات البلاطة").setFontSize(14f).setBold())
                val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                infoTable.addCell(createCell("اسم البلاطة"))
                infoTable.addCell(createCell(slab.slabName))
                infoTable.addCell(createCell("النوع"))
                infoTable.addCell(createCell(slab.slabType.name))
                infoTable.addCell(createCell("نوع الاستناد"))
                infoTable.addCell(createCell(slab.supportType.name))
                document.add(infoTable)
                document.add(Paragraph("\n"))

                // الأبعاد
                document.add(Paragraph("الأبعاد").setFontSize(14f).setBold())
                val dimensionsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                dimensionsTable.addCell(createCell("الطول (م)"))
                dimensionsTable.addCell(createCell("%.3f".format(slab.lengthM)))
                dimensionsTable.addCell(createCell("العرض (م)"))
                dimensionsTable.addCell(createCell("%.3f".format(slab.widthM)))
                dimensionsTable.addCell(createCell("السمك (ملم)"))
                dimensionsTable.addCell(createCell("%d".format(slab.thicknessMM)))
                dimensionsTable.addCell(createCell("حجم الخرسانة (م³)"))
                dimensionsTable.addCell(createCell("%.3f".format(slab.getConcreteVolume())))
                document.add(dimensionsTable)
                document.add(Paragraph("\n"))

                // الأحمال
                document.add(Paragraph("الأحمال").setFontSize(14f).setBold())
                val loadsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                loadsTable.addCell(createCell("الحمل الميت (kN/m²)"))
                loadsTable.addCell(createCell("%.2f".format(slab.deadLoadKNM2)))
                loadsTable.addCell(createCell("الحمل الحي (kN/m²)"))
                loadsTable.addCell(createCell("%.2f".format(slab.liveLoadKNM2)))
                loadsTable.addCell(createCell("الحمل الكلي (kN)"))
                loadsTable.addCell(createCell("%.2f".format(slab.getTotalLoad())))
                document.add(loadsTable)
                document.add(Paragraph("\n"))

                // النتائج
                document.add(Paragraph("نتائج التصميم").setFontSize(14f).setBold())
                val resultsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                resultsTable.addCell(createCell("أقص�� عزم (kN.m)"))
                resultsTable.addCell(createCell("%.2f".format(slab.maxMomentKNM)))
                resultsTable.addCell(createCell("قوة القص (kN)"))
                resultsTable.addCell(createCell("%.2f".format(slab.shearForceKN)))
                resultsTable.addCell(createCell("فولاذ سفلي (قطر/تباعد)"))
                resultsTable.addCell(createCell("%d / %d ملم".format(slab.bottomSteelDiaMM, slab.bottomSteelSpacingMM)))
                resultsTable.addCell(createCell("فولاذ علوي (قطر/تباعد)"))
                resultsTable.addCell(createCell("%d / %d ملم".format(slab.topSteelDiaMM, slab.topSteelSpacingMM)))
                resultsTable.addCell(createCell("الانحراف (ملم)"))
                resultsTable.addCell(createCell("%.2f / %.2f".format(slab.deflectionMM, slab.maxAllowedDeflectionMM)))
                resultsTable.addCell(createCell("الحالة"))
                resultsTable.addCell(
                    createCell(
                        if (slab.isSafe) "آمن ✓" else "غير آمن ✗",
                        if (slab.isSafe) ColorConstants.GREEN else ColorConstants.RED
                    )
                )
                document.add(resultsTable)
                document.add(Paragraph("\n"))

                // التكاليف
                document.add(Paragraph("التكاليف").setFontSize(14f).setBold())
                val costsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                costsTable.addCell(createCell("التكلفة الإجمالية"))
                costsTable.addCell(createCell("%.2f %s".format(slab.totalCost, slab.unitCurrency)))
                document.add(costsTable)
                document.add(Paragraph("\n"))

                document.add(
                    Paragraph(dateFormat.format(Date()))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontSize(10f)
                )
            }
        }

        return outputFile
    }

    /**
     * تصدير تقرير القاعدة إلى PDF
     */
    fun exportFootingDesignToPDF(footing: FootingDesign): File {
        val fileName = "Footing_${footing.footingName}_${System.currentTimeMillis()}.pdf"
        val outputFile = File(context.getExternalFilesDir(null), fileName)

        PdfWriter(FileOutputStream(outputFile)).use { writer ->
            Document(PdfWriter(writer)).use { document ->
                document.add(
                    Paragraph("تقرير تصميم القاعدة الخرسانية")
                        .setFontSize(20f)
                        .setTextAlignment(TextAlignment.CENTER)
                )
                document.add(Paragraph("\n"))

                // المعلومات الأساسية
                document.add(Paragraph("المعلومات الأساسية").setFontSize(14f).setBold())
                val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                infoTable.addCell(createCell("اسم القاعدة"))
                infoTable.addCell(createCell(footing.footingName))
                infoTable.addCell(createCell("النوع"))
                infoTable.addCell(createCell(footing.footingType.name))
                infoTable.addCell(createCell("الكود"))
                infoTable.addCell(createCell(footing.codeType.name))
                document.add(infoTable)
                document.add(Paragraph("\n"))

                // الأحمال والتحمل
                document.add(Paragraph("الأحمال والتحمل").setFontSize(14f).setBold())
                val loadsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                loadsTable.addCell(createCell("حمل العمود (kN)"))
                loadsTable.addCell(createCell("%.2f".format(footing.columnLoadKN)))
                loadsTable.addCell(createCell("تحمل التربة (kN/m²)"))
                loadsTable.addCell(createCell("%.2f".format(footing.soilBearingCapacityKNM2)))
                loadsTable.addCell(createCell("الضغط الفعلي (kN/m²)"))
                loadsTable.addCell(createCell("%.2f".format(footing.actualSoilPressureKNM2)))
                loadsTable.addCell(createCell("مساحة القاعدة (m²)"))
                loadsTable.addCell(createCell("%.2f".format(footing.footingAreaM2)))
                document.add(loadsTable)
                document.add(Paragraph("\n"))

                // الأبعاد
                document.add(Paragraph("الأبعاد").setFontSize(14f).setBold())
                val dimensionsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                dimensionsTable.addCell(createCell("الطول (م)"))
                dimensionsTable.addCell(createCell("%.2f".format(footing.lengthM)))
                dimensionsTable.addCell(createCell("العرض (م)"))
                dimensionsTable.addCell(createCell("%.2f".format(footing.widthM)))
                dimensionsTable.addCell(createCell("العمق (م)"))
                dimensionsTable.addCell(createCell("%.2f".format(footing.depthM)))
                document.add(dimensionsTable)
                document.add(Paragraph("\n"))

                // نتائج التصميم
                document.add(Paragraph("نتائج التصميم").setFontSize(14f).setBold())
                val resultsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                resultsTable.addCell(createCell("أقصى عزم (kN.m)"))
                resultsTable.addCell(createCell("%.2f".format(footing.maxMomentKNM)))
                resultsTable.addCell(createCell("قوة القص (kN)"))
                resultsTable.addCell(createCell("%.2f".format(footing.shearForceKN)))
                resultsTable.addCell(createCell("مساحة الفولاذ (mm²)"))
                resultsTable.addCell(createCell("%.2f".format(footing.steelAreaMM2)))
                resultsTable.addCell(createCell("قطر الفولاذ (ملم)"))
                resultsTable.addCell(createCell("%d".format(footing.steelDiaMM)))
                resultsTable.addCell(createCell("تباعد الفولاذ (ملم)"))
                resultsTable.addCell(createCell("%d".format(footing.steelSpacingMM)))
                document.add(resultsTable)
                document.add(Paragraph("\n"))

                // حالات الأمان
                document.add(Paragraph("حالات الأمان").setFontSize(14f).setBold())
                val safetyTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                safetyTable.addCell(createCell("تحمل التربة"))
                safetyTable.addCell(
                    createCell(
                        if (footing.isSafeBearing) "آمن ✓" else "غير آمن ✗",
                        if (footing.isSafeBearing) ColorConstants.GREEN else ColorConstants.RED
                    )
                )
                safetyTable.addCell(createCell("الانحناء"))
                safetyTable.addCell(
                    createCell(
                        if (footing.isSafeFlexure) "آمن ✓" else "غير آمن ✗",
                        if (footing.isSafeFlexure) ColorConstants.GREEN else ColorConstants.RED
                    )
                )
                safetyTable.addCell(createCell("القص"))
                safetyTable.addCell(
                    createCell(
                        if (footing.isSafeShear) "آمن ✓" else "غير آمن ✗",
                        if (footing.isSafeShear) ColorConstants.GREEN else ColorConstants.RED
                    )
                )
                document.add(safetyTable)
                document.add(Paragraph("\n"))

                // التكاليف
                document.add(Paragraph("التكاليف").setFontSize(14f).setBold())
                val costsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                costsTable.addCell(createCell("التكلفة الإجمالية"))
                costsTable.addCell(createCell("%.2f %s".format(footing.totalCost, footing.unitCurrency)))
                document.add(costsTable)
                document.add(Paragraph("\n"))

                document.add(
                    Paragraph(dateFormat.format(Date()))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontSize(10f)
                )
            }
        }

        return outputFile
    }

    /**
     * تصدير تقرير حائط السند إلى PDF
     */
    fun exportRetainingWallToPDF(wall: RetainingWall): File {
        val fileName = "Wall_${wall.wallName}_${System.currentTimeMillis()}.pdf"
        val outputFile = File(context.getExternalFilesDir(null), fileName)

        PdfWriter(FileOutputStream(outputFile)).use { writer ->
            Document(PdfWriter(writer)).use { document ->
                document.add(
                    Paragraph("تقرير تصميم حائط السند")
                        .setFontSize(20f)
                        .setTextAlignment(TextAlignment.CENTER)
                )
                document.add(Paragraph("\n"))

                // المعلومات الأساسية
                document.add(Paragraph("المعلومات الأساسية").setFontSize(14f).setBold())
                val infoTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                infoTable.addCell(createCell("اسم الحائط"))
                infoTable.addCell(createCell(wall.wallName))
                infoTable.addCell(createCell("النوع"))
                infoTable.addCell(createCell(wall.wallType.name))
                infoTable.addCell(createCell("الكود"))
                infoTable.addCell(createCell(wall.codeType.name))
                document.add(infoTable)
                document.add(Paragraph("\n"))

                // خصائص التربة
                document.add(Paragraph("خصائص التربة").setFontSize(14f).setBold())
                val soilTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                soilTable.addCell(createCell("الوزن الحجمي (kN/m³)"))
                soilTable.addCell(createCell("%.2f".format(wall.soilUnitWeightKNM3)))
                soilTable.addCell(createCell("زاوية الاحتكاك (°)"))
                soilTable.addCell(createCell("%.1f".format(wall.soilFrictionAngleDeg)))
                soilTable.addCell(createCell("التماسك (kN/m²)"))
                soilTable.addCell(createCell("%.2f".format(wall.soilCohesionKNM2)))
                soilTable.addCell(createCell("تحمل التربة (kN/m²)"))
                soilTable.addCell(createCell("%.2f".format(wall.bearingCapacityKNM2)))
                document.add(soilTable)
                document.add(Paragraph("\n"))

                // الأبعاد
                document.add(Paragraph("الأبعاد").setFontSize(14f).setBold())
                val dimensionsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                dimensionsTable.addCell(createCell("ارتفاع الحائط (م)"))
                dimensionsTable.addCell(createCell("%.2f".format(wall.wallHeightM)))
                dimensionsTable.addCell(createCell("ارتفاع التربة (م)"))
                dimensionsTable.addCell(createCell("%.2f".format(wall.soilHeightBehindM)))
                dimensionsTable.addCell(createCell("عرض القاعدة (م)"))
                dimensionsTable.addCell(createCell("%.2f".format(wall.baseWidthM)))
                dimensionsTable.addCell(createCell("سمك الساق (م)"))
                dimensionsTable.addCell(createCell("%.2f".format(wall.stemThicknessM)))
                document.add(dimensionsTable)
                document.add(Paragraph("\n"))

                // نتائج التحليل
                document.add(Paragraph("نتائج التحليل").setFontSize(14f).setBold())
                val analysisTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                analysisTable.addCell(createCell("الضغط النشط (kN/m)"))
                analysisTable.addCell(createCell("%.2f".format(wall.activePressureKNM)))
                analysisTable.addCell(createCell("القوة الأفقية (kN)"))
                analysisTable.addCell(createCell("%.2f".format(wall.totalHorizontalForceKN)))
                analysisTable.addCell(createCell("الحمل الرأسي (kN)"))
                analysisTable.addCell(createCell("%.2f".format(wall.totalVerticalLoadKN)))
                analysisTable.addCell(createCell("عزم الانقلاب (kN.m)"))
                analysisTable.addCell(createCell("%.2f".format(wall.overturbingMomentKNM)))
                analysisTable.addCell(createCell("عزم المقاومة (kN.m)"))
                analysisTable.addCell(createCell("%.2f".format(wall.resistanceMomentKNM)))
                document.add(analysisTable)
                document.add(Paragraph("\n"))

                // معاملات الأمان
                document.add(Paragraph("معاملات الأمان").setFontSize(14f).setBold())
                val safetyTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                safetyTable.addCell(createCell("الانقلاب"))
                safetyTable.addCell(
                    createCell(
                        "%.2f (آمن > 1.5)".format(wall.factorOfSafetyOverturning),
                        if (wall.isSafeOverturning) ColorConstants.GREEN else ColorConstants.RED
                    )
                )
                safetyTable.addCell(createCell("الانزلاق"))
                safetyTable.addCell(
                    createCell(
                        "%.2f (آمن > 1.5)".format(wall.factorOfSafetySliding),
                        if (wall.isSafeSliding) ColorConstants.GREEN else ColorConstants.RED
                    )
                )
                safetyTable.addCell(createCell("تحمل التربة"))
                safetyTable.addCell(
                    createCell(
                        if (wall.isSafeBearing) "آمن ✓" else "غير آمن ✗",
                        if (wall.isSafeBearing) ColorConstants.GREEN else ColorConstants.RED
                    )
                )
                document.add(safetyTable)
                document.add(Paragraph("\n"))

                // ضغوط الأساس
                document.add(Paragraph("ضغوط الأساس").setFontSize(14f).setBold())
                val pressuresTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                pressuresTable.addCell(createCell("الضغط الأقصى (kN/m²)"))
                pressuresTable.addCell(createCell("%.2f".format(wall.maxToePressureKNM2)))
                pressuresTable.addCell(createCell("الضغط الأدنى (kN/m²)"))
                pressuresTable.addCell(createCell("%.2f".format(wall.minHeelPressureKNM2)))
                pressuresTable.addCell(createCell("الانحراف عن المركز (م)"))
                pressuresTable.addCell(createCell("%.3f".format(wall.eccentricityM)))
                document.add(pressuresTable)
                document.add(Paragraph("\n"))

                // التسليح
                document.add(Paragraph("نتائج التسليح").setFontSize(14f).setBold())
                val steelTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                steelTable.addCell(createCell("فولاذ الساق (mm²)"))
                steelTable.addCell(createCell("%.2f".format(wall.stemSteelAreaMM2)))
                steelTable.addCell(createCell("فولاذ القاعدة (mm²)"))
                steelTable.addCell(createCell("%.2f".format(wall.baseSteelAreaMM2)))
                document.add(steelTable)
                document.add(Paragraph("\n"))

                // التكاليف
                document.add(Paragraph("التكاليف").setFontSize(14f).setBold())
                val costsTable = Table(UnitValue.createPercentArray(floatArrayOf(1f, 1f)))
                costsTable.addCell(createCell("التكلفة الإجمالية"))
                costsTable.addCell(createCell("%.2f %s".format(wall.totalCost, wall.unitCurrency)))
                document.add(costsTable)
                document.add(Paragraph("\n"))

                document.add(
                    Paragraph(dateFormat.format(Date()))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontSize(10f)
                )
            }
        }

        return outputFile
    }

    private fun createCell(text: String, backgroundColor: com.itextpdf.kernel.colors.Color? = null): Cell {
        val cell = Cell().add(Paragraph(text))
        if (backgroundColor != null) {
            cell.setBackgroundColor(backgroundColor)
        }
        return cell
    }
}