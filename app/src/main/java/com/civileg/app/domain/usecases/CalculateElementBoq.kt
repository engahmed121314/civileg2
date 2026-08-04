package com.civileg.app.domain.usecases

import com.civileg.app.domain.entities.*
import javax.inject.Inject
import kotlin.math.PI

/**
 * حساب كميات العناصر الإنشائية من نتائج التصميم
 *
 * ينتج قائمة BoqItem لكل عنصر (عمود، كمرة، بلاطة، قاعدة، سلم، خزان، حائط ساند).
 * الوحدات:
 *   الخرسانة   → m³
 *   الحديد     → ton
 *   الشدة      → m²
 *   الحفر      → m³
 *   المعالجة    → m²
 *
 * أبعاد الإدخال بالملليمتر باستثناء الأطوال (البحور) بالمتر.
 */
class CalculateElementBoq @Inject constructor() {

    // ==============================================================
    // العمود
    // ==============================================================
    fun calculateColumnBoq(
        width: Double,      // mm
        depth: Double,      // mm
        height: Double,     // mm
        reinforcementResult: ReinforcementResult,
        prices: MaterialPrices
    ): List<BoqItem> {
        val items = mutableListOf<BoqItem>()

        // 1. الخرسانة: V = b × d × h  (mm³ → m³)
        val concreteVolume = width * depth * height / 1e9
        items += concreteItem("COL_CONC_001", "Column ${width.toInt()}×${depth.toInt()}mm, h=${height.toInt()}mm", concreteVolume, prices.concretePerM3, "V = b × d × h")

        // 2. حديد التسليح الرئيسي: W = As × L × 7850 / 1e12  (tons)
        //    As(mm²) × L(mm) = mm³ → /1e9 = m³ → × 7850 kg/m³ → /1000 = tons
        val steelWeight = reinforcementResult.astProvided * height * 7850.0 / 1e12
        if (reinforcementResult.numberOfBars > 0) {
            items += steelItem("COL_REINF_001", "Main rebar ${reinforcementResult.numberOfBars}Ø${reinforcementResult.barDiameter.toInt()}mm", steelWeight, prices.steelPerTon)
        }

        // 3. الكانات
        val tiesWeight = calculateTiesWeight(width, depth, height, reinforcementResult.tiesDiameter, reinforcementResult.tiesSpacing)
        items += steelItem("COL_TIES_001", "Ties Ø${reinforcementResult.tiesDiameter.toInt()}mm @ ${reinforcementResult.tiesSpacing.toInt()}mm c/c", tiesWeight, prices.steelPerTon)

        // 4. الشدة الخشبية (2 × عرض + 2 × عمق) × ارتفاع
        val formworkArea = 2.0 * (width + depth) * height / 1e6
        items += formworkItem("COL_FORM_001", "Column formwork ${width.toInt()}×${depth.toInt()}mm", formworkArea, prices.formworkPerM2)

        return items
    }

    // ==============================================================
    // الكمرة
    // ==============================================================
    fun calculateBeamBoq(
        width: Double,      // mm
        depth: Double,      // mm
        span: Double,       // m
        flexureResult: ReinforcementResult,
        shearResult: ShearReinforcementResult,
        prices: MaterialPrices
    ): List<BoqItem> {
        val items = mutableListOf<BoqItem>()
        val spanMm = span * 1000.0

        // 1. الخرسانة
        val concreteVolume = width * depth * spanMm / 1e9
        items += concreteItem("BEAM_CONC_001", "Beam ${width.toInt()}×${depth.toInt()}mm, L=${span}m", concreteVolume, prices.concretePerM3, "V = b × h × L")

        // 2. الحديد الرئيسي (السفلي + العلوي إن وجد)
        val mainSteelWeight = flexureResult.astProvided * spanMm * 7850.0 / 1e12
        if (flexureResult.numberOfBars > 0) {
            items += steelItem("BEAM_REINF_001", "Main ${flexureResult.numberOfBars}Ø${flexureResult.barDiameter.toInt()}mm, L=${span}m", mainSteelWeight, prices.steelPerTon)
        }

        // 3. الكانات
        val stirrupsWeight = calculateStirrupsWeight(width, depth, spanMm, shearResult.stirrupDiameter, shearResult.stirrupSpacing)
        items += steelItem("BEAM_STIR_001", "Stirrups Ø${shearResult.stirrupDiameter.toInt()}mm @ ${shearResult.stirrupSpacing.toInt()}mm", stirrupsWeight, prices.steelPerTon)

        // 4. الشدة الخشبية (3 وجوه: جانبين + سفل)
        val formworkArea = (2.0 * depth + width) * spanMm / 1e6
        items += formworkItem("BEAM_FORM_001", "Beam formwork 3 sides, L=${span}m", formworkArea, prices.formworkPerM2)

        return items
    }

    // ==============================================================
    // البلاطة
    // ==============================================================
    fun calculateSlabBoq(
        spanX: Double,       // m (البحر القصير)
        spanY: Double,       // m (البحر الطويل)
        thickness: Double,   // mm
        mainDia: Double,     // mm
        mainSpacing: Double, // mm
        distDia: Double,     // mm
        distSpacing: Double, // mm
        cover: Double = 25.0,// mm
        prices: MaterialPrices
    ): List<BoqItem> {
        val items = mutableListOf<BoqItem>()
        val lxMm = spanX * 1000.0
        val lyMm = spanY * 1000.0

        // 1. الخرسانة
        val concreteVolume = lxMm * lyMm * thickness / 1e9
        items += concreteItem("SLAB_CONC_001", "Solid slab ${spanX}m × ${spanY}m × ${thickness.toInt()}mm", concreteVolume, prices.concretePerM3, "V = Lx × Ly × t")

        // 2. حديد التسليح الرئيسي (اتجاه Lx — البحر القصير)
        val mainBarsCount = (lyMm / mainSpacing).toInt() + 1
        val mainBarArea = PI * mainDia * mainDia / 4.0
        val mainWeight = mainBarsCount * mainBarArea * lxMm * 7850.0 / 1e12
        items += steelItem("SLAB_MAIN_001", "Main Ø${mainDia.toInt()}mm @ ${mainSpacing.toInt()}mm (Lx=${spanX}m)", mainWeight, prices.steelPerTon)

        // 3. حديد التوزيع (اتجاه Ly — البحر الطويل)
        val distBarsCount = (lxMm / distSpacing).toInt() + 1
        val distBarArea = PI * distDia * distDia / 4.0
        val distWeight = distBarsCount * distBarArea * lyMm * 7850.0 / 1e12
        items += steelItem("SLAB_DIST_001", "Dist Ø${distDia.toInt()}mm @ ${distSpacing.toInt()}mm (Ly=${spanY}m)", distWeight, prices.steelPerTon)

        // 4. الشدة الخشبية (سفل فقط للبلاطة)
        val formworkArea = lxMm * lyMm / 1e6
        items += formworkItem("SLAB_FORM_001", "Slab soffit formwork ${spanX}m × ${spanY}m", formworkArea, prices.formworkPerM2)

        return items
    }

    // ==============================================================
    // القاعدة المنفردة
    // ==============================================================
    fun calculateFootingBoq(
        length: Double,      // mm
        width: Double,       // mm
        thickness: Double,   // mm
        concreteGrade: Double,
        astBottomX: Double,  // mm² (حديد سفلي اتجاه X)
        astBottomY: Double,  // mm² (حديد سفلي اتجاه Y)
        rebarDia: Double,    // mm
        rebarSpacingX: Double, // mm
        rebarSpacingY: Double, // mm
        prices: MaterialPrices,
        excavationDepth: Double = 0.0, // m
        concreteCover: Double = 75.0 // mm
    ): List<BoqItem> {
        val items = mutableListOf<BoqItem>()

        // 1. الحفر
        if (excavationDepth > 0) {
            val excVolume = (length / 1000.0) * (width / 1000.0) * excavationDepth
            items += excavationItem("FTG_EXCAV_001", "Footing excavation ${length.toInt()}×${width.toInt()}mm", excVolume, prices.excavationPerM3)
        }

        // 2. الخرسانة
        val concVolume = length * width * thickness / 1e9
        items += concreteItem("FTG_CONC_001", "Footing concrete ${length.toInt()}×${width.toInt()}×${thickness.toInt()}mm", concVolume, prices.concretePerM3, "V = L × B × t")

        // 3. حديد سفلي اتجاه X (البحر الطولي)
        val bottomBarsX = (width / rebarSpacingX).toInt() + 1
        val barArea = PI * rebarDia * rebarDia / 4.0
        val bottomWeightX = bottomBarsX * barArea * length * 7850.0 / 1e12
        items += steelItem("FTG_REINF_X_001", "Bottom Ø${rebarDia.toInt()}mm @ ${rebarSpacingX.toInt()}mm (X-dir)", bottomWeightX, prices.steelPerTon)

        // 4. حديد سفلي اتجاه Y (البحر العرضي)
        val bottomBarsY = (length / rebarSpacingY).toInt() + 1
        val bottomWeightY = bottomBarsY * barArea * width * 7850.0 / 1e12
        items += steelItem("FTG_REINF_Y_001", "Bottom Ø${rebarDia.toInt()}mm @ ${rebarSpacingY.toInt()}mm (Y-dir)", bottomWeightY, prices.steelPerTon)

        // 5. الشدة الخشبية (الجوانب + السفل = 5 وجوه للقاعدة المكشوفة)
        val formArea = (2 * (length + width) * thickness + length * width) / 1e6
        items += formworkItem("FTG_FORM_001", "Footing formwork (5 sides)", formArea, prices.formworkPerM2)

        return items
    }

    // ==============================================================
    // السلم
    // ==============================================================
    fun calculateStairBoq(
        stairWidth: Double,   // mm
        totalHeight: Double,  // mm (ارتفاع السلم الكلي)
        stairLength: Double,  // mm (طول السلم الأفقي)
        slabThickness: Double, // mm
        waistThickness: Double, // mm
        riserHeight: Double,  // mm
        treadWidth: Double,   // mm
        mainRebarArea: Double, // mm²
        mainRebarDia: Double, // mm
        numMainBars: Int,
        stirrupDia: Double = 8.0,
        stirrupSpacing: Double = 200.0,
        prices: MaterialPrices
    ): List<BoqItem> {
        val items = mutableListOf<BoqItem>()
        val slope = kotlin.math.sqrt(totalHeight * totalHeight + stairLength * stairLength) / stairLength
        val inclinedLength = kotlin.math.sqrt(totalHeight * totalHeight + stairLength * stairLength)

        // 1. الخرسانة (بلاطة + خرسانة الدرج + الأرضية)
        //    مساحة البلاطة المائلة × السماكة
        val slabArea = inclinedLength * stairWidth / 1e6 // m²
        val concreteVolume = slabArea * waistThickness / 1000.0 // m³
        items += concreteItem("STR_CONC_001", "Stair waist slab ${stairWidth.toInt()}mm wide, t=${waistThickness.toInt()}mm", concreteVolume, prices.concretePerM3, "V = inclined_area × t")

        // 2. حديد التسليح الرئيسي
        val mainWeight = mainRebarArea * inclinedLength * 7850.0 / 1e12
        items += steelItem("STR_REINF_001", "Main ${numMainBars}Ø${mainRebarDia.toInt()}mm (inclined)", mainWeight, prices.steelPerTon)

        // 3. الكانات
        // approximate: use average section width for perimeter
        val avgWidth = (stairWidth + waistThickness) / 2.0
        val stirrupsWeight = calculateStirrupsWeight(avgWidth, waistThickness, inclinedLength, stirrupDia, stirrupSpacing)
        items += steelItem("STR_STIR_001", "Stirrups Ø${stirrupDia.toInt()}mm @ ${stirrupSpacing.toInt()}mm", stirrupsWeight, prices.steelPerTon)

        // 4. الشدة (سفل + جانب واحد)
        val formworkArea = (stairWidth + slabThickness) * inclinedLength / 1e6
        items += formworkItem("STR_FORM_001", "Stair formwork (soffit + 1 side)", formworkArea, prices.formworkPerM2)

        return items
    }

    // ==============================================================
    // خزان المياه (تقريبي)
    // ==============================================================
    fun calculateTankBoq(
        tankLength: Double,   // m
        tankWidth: Double,    // m
        tankHeight: Double,   // m
        wallThickness: Double, // mm
        baseThickness: Double, // mm
        wallRebarDia: Double, // mm
        wallRebarSpacingH: Double, // mm (أفقي)
        wallRebarSpacingV: Double, // mm (عمودي)
        baseRebarDia: Double, // mm
        baseRebarSpacing: Double, // mm
        prices: MaterialPrices,
        excavationDepth: Double = 0.5 // m
    ): List<BoqItem> {
        val items = mutableListOf<BoqItem>()
        val lMm = tankLength * 1000.0
        val wMm = tankWidth * 1000.0
        val hMm = tankHeight * 1000.0

        // 1. الحفر
        val excVolume = (tankLength + 0.5) * (tankWidth + 0.5) * excavationDepth
        items += excavationItem("TNK_EXCAV_001", "Tank pit excavation ${tankLength}×${tankWidth}m", excVolume, prices.excavationPerM3)

        // 2. خرسانة القاعدة
        val baseConc = lMm * wMm * baseThickness / 1e9
        items += concreteItem("TNK_BASE_001", "Tank base ${tankLength}×${tankWidth}m, t=${baseThickness.toInt()}mm", baseConc, prices.concretePerM3)

        // 3. خرسانة الجدران (4 جدران تقريباً)
        val wallConc = 2.0 * (lMm + wMm) * hMm * wallThickness / 1e9
        items += concreteItem("TNK_WALL_001", "Tank walls, t=${wallThickness.toInt()}mm", wallConc, prices.concretePerM3)

        // 4. حديد القاعدة
        val baseBarsX = (wMm / baseRebarSpacing).toInt() + 1
        val baseBarsY = (lMm / baseRebarSpacing).toInt() + 1
        val barArea = PI * baseRebarDia * baseRebarDia / 4.0
        // طبقتين (سفلي + علوي)
        val baseWeight = 2.0 * (baseBarsX * lMm + baseBarsY * wMm) * barArea * 7850.0 / 1e12
        items += steelItem("TNK_REINF_BASE_001", "Base rebar 2 layers Ø${baseRebarDia.toInt()}mm @ ${baseRebarSpacing.toInt()}mm", baseWeight, prices.steelPerTon)

        // 5. حديد الجدران (تقريبي — طبقتين: داخلية + خارجية)
        val wallBarArea = PI * wallRebarDia * wallRebarDia / 4.0
        // عدد السيخ الأفقي لكل جدار
        val hBarsPerWall = (hMm / wallRebarSpacingH).toInt() + 1
        val vBarsPerWall = (lMm / wallRebarSpacingV).toInt() + 1 // تقريبي باستخدام البحر الأطول
        val totalWallLength = 2.0 * (lMm + wMm) // محيط الجدران
        val wallWeight = 2.0 * (hBarsPerWall * totalWallLength + vBarsPerWall * totalWallLength) * wallBarArea * 7850.0 / 1e12
        items += steelItem("TNK_REINF_WALL_001", "Wall rebar 2 layers Ø${wallRebarDia.toInt()}mm", wallWeight, prices.steelPerTon)

        // 6. الشدة
        val formArea = (2.0 * (lMm + wMm) * hMm + lMm * wMm) / 1e6 // جدران + قاعدة
        items += formworkItem("TNK_FORM_001", "Tank formwork (walls + base)", formArea, prices.formworkPerM2)

        return items
    }

    // ==============================================================
    // حائط ساند
    // ==============================================================
    fun calculateRetainingWallBoq(
        wallLength: Double,    // m (الطول الأفقي للحائط)
        totalHeight: Double,   // m (الارتفاع الكلي من أساس إلى قمة الحائط)
        baseWidth: Double,     // m (عرض القاعدة)
        baseThickness: Double, // mm (سماكة القاعدة)
        stemTopThickness: Double, // mm (سماكة الحائط من أعلى)
        stemBottomThickness: Double, // mm (سماكة الحائط من أسفل)
        mainRebarDia: Double,  // mm
        verticalRebarSpacing: Double, // mm
        horizontalRebarDia: Double = 12.0,
        horizontalRebarSpacing: Double = 200.0,
        prices: MaterialPrices,
        excavationDepth: Double = 0.0,
        backfillLength: Double = 0.0 // m (طول الردم خلف الحائط)
    ): List<BoqItem> {
        val items = mutableListOf<BoqItem>()
        val lMm = wallLength * 1000.0
        val hMm = totalHeight * 1000.0
        val avgStemT = (stemTopThickness + stemBottomThickness) / 2.0

        // 1. الحفر
        if (excavationDepth > 0 && backfillLength > 0) {
            val excVolume = wallLength * backfillLength * excavationDepth
            items += excavationItem("RW_EXCAV_001", "RW excavation ${wallLength}m long", excVolume, prices.excavationPerM3)
        }

        // 2. خرسانة القاعدة
        val baseConc = lMm * (baseWidth * 1000.0) * baseThickness / 1e9
        items += concreteItem("RW_BASE_001", "RW base ${wallLength}m × ${baseWidth}m, t=${baseThickness.toInt()}mm", baseConc, prices.concretePerM3)

        // 3. خرسانة الحائط (شريطي متغير السماكة — نستخدم المتوسط)
        val stemConc = lMm * hMm * avgStemT / 1e9
        items += concreteItem("RW_STEM_001", "RW stem ${wallLength}m × ${totalHeight}m, avg t=${avgStemT.toInt()}mm", stemConc, prices.concretePerM3)

        // 4. حديد التسليح الرئيسي (عمودي — جانب الأرض)
        val mainBarArea = PI * mainRebarDia * mainRebarDia / 4.0
        val vertBarsCount = (lMm / verticalRebarSpacing).toInt() + 1
        val mainWeight = vertBarsCount * mainBarArea * hMm * 7850.0 / 1e12
        items += steelItem("RW_REINF_V_001", "Vertical main Ø${mainRebarDia.toInt()}mm @ ${verticalRebarSpacing.toInt()}mm", mainWeight, prices.steelPerTon)

        // 5. حديد التوزيع الأفقي
        val horBarArea = PI * horizontalRebarDia * horizontalRebarDia / 4.0
        val horBarsCount = (hMm / horizontalRebarSpacing).toInt() + 1
        val horWeight = horBarsCount * horBarArea * lMm * 7850.0 / 1e12
        items += steelItem("RW_REINF_H_001", "Horizontal dist Ø${horizontalRebarDia.toInt()}mm @ ${horizontalRebarSpacing.toInt()}mm", horWeight, prices.steelPerTon)

        // 6. الشدة
        val formArea = (lMm * hMm + lMm * (baseWidth * 1000.0)) / 1e6
        items += formworkItem("RW_FORM_001", "RW formwork (stem + base)", formArea, prices.formworkPerM2)

        return items
    }

    // ==============================================================
    // Helper factories
    // ==============================================================
    private fun concreteItem(id: String, desc: String, qty: Double, price: Double, codeRef: String) =
        BoqItem(id, desc, BoqCategory.CONCRETE, "m³", qty, price, codeReference = codeRef)

    private fun steelItem(id: String, desc: String, qty: Double, price: Double) =
        BoqItem(id, desc, BoqCategory.REINFORCEMENT, "ton", qty, price)

    private fun formworkItem(id: String, desc: String, qty: Double, price: Double) =
        BoqItem(id, desc, BoqCategory.FORMWORK, "m²", qty, price)

    private fun excavationItem(id: String, desc: String, qty: Double, price: Double) =
        BoqItem(id, desc, BoqCategory.EXCAVATION, "m³", qty, price)

    // ==============================================================
    // دوال مساعدة لحساب وزن الكانات والأطواق
    // ==============================================================
    /**
     * حساب وزن الكانات للعمود
     * محيط الكانة = 2×(b-2c + d-2c) + 24mm للخطافين
     */
    private fun calculateTiesWeight(
        colWidth: Double, colDepth: Double, colHeight: Double,
        tiesDiameter: Double, tiesSpacing: Double
    ): Double {
        if (tiesSpacing <= 0 || tiesDiameter <= 0) return 0.0
        val cover = 40.0 // mm
        val tiePerimeter = 2 * (colWidth - 2 * cover + colDepth - 2 * cover) + 24.0 // mm
        val tieLength = tiePerimeter / 1000.0 // m
        val numberOfTies = (colHeight / tiesSpacing).toInt() + 1
        val barArea = PI * tiesDiameter * tiesDiameter / 4.0 // mm²
        // weight = n × (A / 1e6) × L × 7850  [m³ × kg/m³ = kg]
        val weightKg = numberOfTies * (barArea / 1e6) * tieLength * 7850.0
        return weightKg / 1000.0 // tons
    }

    /**
     * حساب وزن الكانات للكمرة
     */
    private fun calculateStirrupsWeight(
        beamWidth: Double, beamDepth: Double, beamSpanMm: Double,
        stirrupDiameter: Double, stirrupSpacing: Double
    ): Double {
        if (stirrupSpacing <= 0 || stirrupDiameter <= 0) return 0.0
        val cover = 40.0 // mm
        val stirrupPerimeter = 2 * (beamWidth - 2 * cover + beamDepth - 2 * cover) + 24.0 // mm
        val stirrupLength = stirrupPerimeter / 1000.0 // m
        val numberOfStirrups = (beamSpanMm / stirrupSpacing).toInt() + 1
        val barArea = PI * stirrupDiameter * stirrupDiameter / 4.0 // mm²
        val weightKg = numberOfStirrups * (barArea / 1e6) * stirrupLength * 7850.0
        return weightKg / 1000.0 // tons
    }
}

data class MaterialPrices(
    val concretePerM3: Double = 1200.0,    // EGP per m³
    val steelPerTon: Double = 18000.0,     // EGP per ton
    val formworkPerM2: Double = 150.0,     // EGP per m²
    val excavationPerM3: Double = 50.0     // EGP per m³
)