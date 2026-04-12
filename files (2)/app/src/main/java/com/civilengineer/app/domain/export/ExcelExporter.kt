package com.civilengineer.app.domain.export

import android.content.Context
import com.civilengineer.app.data.models.ColumnDesign
import com.civilengineer.app.data.models.SlabDesign
import com.civilengineer.app.data.models.FootingDesign
import com.civilengineer.app.data.models.RetainingWall
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * فئة تصدير البيانات إلى ملفات Excel
 */
class ExcelExporter(private val context: Context) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("ar", "EG"))

    /**
     * تصدير جميع التصاميم إلى ملف Excel واحد
     */
    fun exportAllDesignsToExcel(
        columns: List<ColumnDesign>,
        slabs: List<SlabDesign>,
        footings: List<FootingDesign>,
        walls: List<RetainingWall>
    ): File {
        val workbook = XSSFWorkbook()

        // إنشاء ورقة الأعمدة
        if (columns.isNotEmpty()) {
            createColumnsSheet(workbook, columns)
        }

        // إنشاء ورقة البلاطات
        if (slabs.isNotEmpty()) {
            createSlabsSheet(workbook, slabs)
        }

        // إنشاء ورقة القواعس
        if (footings.isNotEmpty()) {
            createFootingsSheet(workbook, footings)
        }

        // إنشاء ورقة حوائط السند
        if (walls.isNotEmpty()) {
            createWallsSheet(workbook, walls)
        }

        val fileName = "CivilEngineer_Report_${System.currentTimeMillis()}.xlsx"
        val outputFile = File(context.getExternalFilesDir(null), fileName)
        val fos = FileOutputStream(outputFile)
        workbook.write(fos)
        fos.close()
        workbook.close()

        return outputFile
    }

    private fun createColumnsSheet(workbook: Workbook, columns: List<ColumnDesign>) {
        val sheet = workbook.createSheet("الأعمدة")
        val headerStyle = createHeaderStyle(workbook)

        val headers = listOf(
            "اسم العمود",
            "الطول (م)",
            "العرض (م)",
            "الارتفاع (م)",
            "الحمل (kN)",
            "العزم (kN.m)",
            "الخرسانة",
            "الفولاذ",
            "الكود",
            "الفولاذ mm²",
            "قطر (ملم)",
            "تباعد (ملم)",
            "معامل الأمان",
            "آمن؟",
            "التكلفة",
            "التاريخ"
        )

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        columns.forEachIndexed { rowIndex, column ->
            val row = sheet.createRow(rowIndex + 1)
            row.createCell(0).setCellValue(column.columnName)
            row.createCell(1).setCellValue(column.lengthM)
            row.createCell(2).setCellValue(column.widthM)
            row.createCell(3).setCellValue(column.heightM)
            row.createCell(4).setCellValue(column.axialLoadKN)
            row.createCell(5).setCellValue(column.bendingMomentKNM)
            row.createCell(6).setCellValue(column.concreteGrade)
            row.createCell(7).setCellValue(column.steelGrade)
            row.createCell(8).setCellValue(column.codeType.name)
            row.createCell(9).setCellValue(column.mainSteelAreaMM2)
            row.createCell(10).setCellValue(column.mainSteelDiaMM.toDouble())
            row.createCell(11).setCellValue(column.stirrupsSpacingMM.toDouble())
            row.createCell(12).setCellValue(column.safetyFactor)
            row.createCell(13).setCellValue(if (column.isSafe) "نعم" else "لا")
            row.createCell(14).setCellValue(column.totalCost)
            row.createCell(15).setCellValue(dateFormat.format(Date(column.createdDate)))
        }

        // تعديل عرض الأعمدة
        sheet.autoSizeColumn(0)
        repeat(15) { sheet.autoSizeColumn(it + 1) }
    }

    private fun createSlabsSheet(workbook: Workbook, slabs: List<SlabDesign>) {
        val sheet = workbook.createSheet("البلاطات")
        val headerStyle = createHeaderStyle(workbook)

        val headers = listOf(
            "اسم البلاطة",
            "الطول (م)",
            "العرض (م)",
            "السمك (ملم)",
            "الحمل الميت",
            "الحمل الحي",
            "النوع",
            "الاستناد",
            "الخرسانة",
            "الفولاذ",
            "الكود",
            "العزم (kN.m)",
            "الفولاذ السفلي mm²/m",
            "الفولاذ العلوي mm²/m",
            "الانحراف (ملم)",
            "الحد الأقصى (ملم)",
            "آمن؟",
            "التكلفة",
            "التاريخ"
        )

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        slabs.forEachIndexed { rowIndex, slab ->
            val row = sheet.createRow(rowIndex + 1)
            row.createCell(0).setCellValue(slab.slabName)
            row.createCell(1).setCellValue(slab.lengthM)
            row.createCell(2).setCellValue(slab.widthM)
            row.createCell(3).setCellValue(slab.thicknessMM.toDouble())
            row.createCell(4).setCellValue(slab.deadLoadKNM2)
            row.createCell(5).setCellValue(slab.liveLoadKNM2)
            row.createCell(6).setCellValue(slab.slabType.name)
            row.createCell(7).setCellValue(slab.supportType.name)
            row.createCell(8).setCellValue(slab.concreteGrade)
            row.createCell(9).setCellValue(slab.steelGrade)
            row.createCell(10).setCellValue(slab.codeType.name)
            row.createCell(11).setCellValue(slab.maxMomentKNM)
            row.createCell(12).setCellValue(slab.bottomSteelAreaMM2M)
            row.createCell(13).setCellValue(slab.topSteelAreaMM2M)
            row.createCell(14).setCellValue(slab.deflectionMM)
            row.createCell(15).setCellValue(slab.maxAllowedDeflectionMM)
            row.createCell(16).setCellValue(if (slab.isSafe) "نعم" else "لا")
            row.createCell(17).setCellValue(slab.totalCost)
            row.createCell(18).setCellValue(dateFormat.format(Date(slab.createdDate)))
        }

        repeat(18) { sheet.autoSizeColumn(it) }
    }

    private fun createFootingsSheet(workbook: Workbook, footings: List<FootingDesign>) {
        val sheet = workbook.createSheet("القواعس")
        val headerStyle = createHeaderStyle(workbook)

        val headers = listOf(
            "اسم القاعدة",
            "حمل العمود",
            "النوع",
            "مساحة القاعدة",
            "الطول",
            "العرض",
            "العمق",
            "الضغط الفعلي",
            "تحمل التربة",
            "آمن تحمل؟",
            "العزم",
            "القص",
            "الفولاذ mm²",
            "قطر الفولاذ",
            "آمن انحناء؟",
            "آمن قص؟",
            "التكلفة",
            "التاريخ"
        )

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        footings.forEachIndexed { rowIndex, footing ->
            val row = sheet.createRow(rowIndex + 1)
            row.createCell(0).setCellValue(footing.footingName)
            row.createCell(1).setCellValue(footing.columnLoadKN)
            row.createCell(2).setCellValue(footing.footingType.name)
            row.createCell(3).setCellValue(footing.footingAreaM2)
            row.createCell(4).setCellValue(footing.lengthM)
            row.createCell(5).setCellValue(footing.widthM)
            row.createCell(6).setCellValue(footing.depthM)
            row.createCell(7).setCellValue(footing.actualSoilPressureKNM2)
            row.createCell(8).setCellValue(footing.soilBearingCapacityKNM2)
            row.createCell(9).setCellValue(if (footing.isSafeBearing) "نعم" else "لا")
            row.createCell(10).setCellValue(footing.maxMomentKNM)
            row.createCell(11).setCellValue(footing.shearForceKN)
            row.createCell(12).setCellValue(footing.steelAreaMM2)
            row.createCell(13).setCellValue(footing.steelDiaMM.toDouble())
            row.createCell(14).setCellValue(if (footing.isSafeFlexure) "نعم" else "لا")
            row.createCell(15).setCellValue(if (footing.isSafeShear) "نعم" else "لا")
            row.createCell(16).setCellValue(footing.totalCost)
            row.createCell(17).setCellValue(dateFormat.format(Date(footing.createdDate)))
        }

        repeat(17) { sheet.autoSizeColumn(it) }
    }

    private fun createWallsSheet(workbook: Workbook, walls: List<RetainingWall>) {
        val sheet = workbook.createSheet("حوائط السند")
        val headerStyle = createHeaderStyle(workbook)

        val headers = listOf(
            "اسم الحائط",
            "النوع",
            "ارتفاع الحائط",
            "ارتفاع التربة",
            "الوزن الحجمي",
            "زاوية الاحتكاك",
            "التماسك",
            "تحمل التربة",
            "الضغط النشط",
            "القوة الأفقية",
            "الحمل الرأسي",
            "عزم الانقلاب",
            "عزم المقاومة",
            "معامل الانقلاب",
            "معامل الانزلاق",
            "الضغط الأقصى",
            "الضغط الأدنى",
            "الاستناد الكلي آمن؟",
            "التكلفة",
            "التاريخ"
        )

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }

        walls.forEachIndexed { rowIndex, wall ->
            val row = sheet.createRow(rowIndex + 1)
            row.createCell(0).setCellValue(wall.wallName)
            row.createCell(1).setCellValue(wall.wallType.name)
            row.createCell(2).setCellValue(wall.wallHeightM)
            row.createCell(3).setCellValue(wall.soilHeightBehindM)
            row.createCell(4).setCellValue(wall.soilUnitWeightKNM3)
            row.createCell(5).setCellValue(wall.soilFrictionAngleDeg)
            row.createCell(6).setCellValue(wall.soilCohesionKNM2)
            row.createCell(7).setCellValue(wall.bearingCapacityKNM2)
            row.createCell(8).setCellValue(wall.activePressureKNM)
            row.createCell(9).setCellValue(wall.totalHorizontalForceKN)
            row.createCell(10).setCellValue(wall.totalVerticalLoadKN)
            row.createCell(11).setCellValue(wall.overturbingMomentKNM)
            row.createCell(12).setCellValue(wall.resistanceMomentKNM)
            row.createCell(13).setCellValue(wall.factorOfSafetyOverturning)
            row.createCell(14).setCellValue(wall.factorOfSafetySliding)
            row.createCell(15).setCellValue(wall.maxToePressureKNM2)
            row.createCell(16).setCellValue(wall.minHeelPressureKNM2)
            val isSafeAll = wall.isSafeOverturning && wall.isSafeSliding && wall.isSafeBearing
            row.createCell(17).setCellValue(if (isSafeAll) "نعم" else "لا")
            row.createCell(18).setCellValue(wall.totalCost)
            row.createCell(19).setCellValue(dateFormat.format(Date(wall.createdDate)))
        }

        repeat(19) { sheet.autoSizeColumn(it) }
    }

    private fun createHeaderStyle(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        style.fillForegroundColor = IndexedColors.LIGHT_BLUE.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        
        val font = workbook.createFont()
        font.bold = true
        font.color = IndexedColors.WHITE.index
        style.setFont(font)
        
        return style
    }
}