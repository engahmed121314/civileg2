package com.civilengineer.assistant.models.steel

/**
 * نماذج بيانات المنشآت المعدنية الشاملة
 * Steel Structures Data Models
 * حسب: AISC 360 / ECP 205 / SBC 306
 */

// ═══════════════════════════════════════════════════════════
// أنواع القطاعات المعدنية
// ═══════════════════════════════════════════════════════════

enum class SteelSectionType(val displayName: String, val displayNameEn: String) {
    IPE("قطاع IPE", "IPE Section"),
    HEA("قطاع HEA (عريض خفيف)", "HEA Wide Flange Light"),
    HEB("قطاع HEB (عريض متوسط)", "HEB Wide Flange Medium"),
    HEM("قطاع HEM (عريض ثقيل)", "HEM Wide Flange Heavy"),
    UPN("قطاع مجرى UPN", "UPN Channel"),
    EQUAL_ANGLE("زاوية متساوية الأضلاع", "Equal Angle"),
    UNEQUAL_ANGLE("زاوية غير متساوية", "Unequal Angle"),
    SHS("مربع مجوف SHS", "Square Hollow Section"),
    RHS("مستطيل مجوف RHS", "Rectangular Hollow Section"),
    CHS("دائري مجوف CHS", "Circular Hollow Section"),
    PLATE_GIRDER("بليت جيردر", "Plate Girder"),
    BUILT_UP("قطاع مركب", "Built-Up Section"),
    COLD_FORMED_C("C مشكل على البارد", "Cold-Formed C"),
    COLD_FORMED_Z("Z مشكل على البارد", "Cold-Formed Z")
}

// ═══════════════════════════════════════════════════════════
// أنواع التصميم المعدني
// ═══════════════════════════════════════════════════════════

enum class SteelDesignType(val displayName: String) {
    BEAM_DESIGN("تصميم كمرة معدنية"),
    COLUMN_DESIGN("تصميم عمود معدني"),
    BEAM_COLUMN("تصميم عمود-كمرة (مشترك)"),
    TENSION_MEMBER("عنصر شد"),
    BASE_PLATE("لوح قاعدة (Base Plate)"),
    BOLTED_CONNECTION("وصلة براغي"),
    WELDED_CONNECTION("وصلة لحام"),
    MOMENT_CONNECTION("وصلة عزم"),
    SHEAR_CONNECTION("وصلة قص"),
    BRACING("شداد (Bracing)"),
    TRUSS("جمالون (Truss)"),
    PURLIN("مرين (Purlin)"),
    GIRT("جيرت (Girt)"),
    CRANE_BEAM("كمرة رافعة"),
    PLATE_GIRDER_DESIGN("بليت جيردر"),
    COMPOSITE_BEAM("كمرة مركبة (حديد + خرسانة)"),
    STIFFENER("تقوية (Stiffener)")
}

// ═══════════════════════════════════════════════════════════
// الكود المعدني
// ═══════════════════════════════════════════════════════════

enum class SteelDesignCode(val displayName: String, val shortName: String) {
    ECP_205("الكود المصري ECP 205", "ECP"),
    SBC_306("الكود السعودي SBC 306", "SBC"),
    AISC_360("الكود الأمريكي AISC 360-22", "AISC"),
    EC3("يوروكود 3 - EN 1993", "EC3")
}

// ═══════════════════════════════════════════════════════════
// رتب الصلب
// ═══════════════════════════════════════════════════════════

enum class SteelGradeType(
    val fy: Double,    // N/mm² - Yield Strength
    val fu: Double,    // N/mm² - Ultimate Strength
    val E: Double,     // N/mm² - Modulus of Elasticity
    val displayName: String
) {
    ST_37(240.0, 370.0, 200000.0, "St 37 (S235) - Fy=240 MPa"),
    ST_44(280.0, 440.0, 200000.0, "St 44 (S275) - Fy=280 MPa"),
    ST_52(360.0, 520.0, 200000.0, "St 52 (S355) - Fy=360 MPa"),
    A36(250.0, 400.0, 200000.0, "ASTM A36 - Fy=250 MPa"),
    A572_50(345.0, 450.0, 200000.0, "ASTM A572 Gr.50 - Fy=345 MPa"),
    A992(345.0, 450.0, 200000.0, "ASTM A992 - Fy=345 MPa"),
    S235(235.0, 360.0, 210000.0, "S235 (EN) - Fy=235 MPa"),
    S275(275.0, 430.0, 210000.0, "S275 (EN) - Fy=275 MPa"),
    S355(355.0, 510.0, 210000.0, "S355 (EN) - Fy=355 MPa"),
    S460(460.0, 540.0, 210000.0, "S460 (EN) - Fy=460 MPa");
}

// ═══════════════════════════════════════════════════════════
// خصائص القطاع المعدني
// ═══════════════════════════════════════════════════════════

data class SteelSectionProperties(
    val name: String,               // مثال: IPE 300
    val type: SteelSectionType,
    val h: Double,                  // mm - الارتفاع الكلي
    val b: Double,                  // mm - عرض الشفة
    val tw: Double,                 // mm - سمك الوتر (web)
    val tf: Double,                 // mm - سمك الشفة (flange)
    val r: Double = 0.0,            // mm - نصف قطر التقعر
    val A: Double,                  // mm² - المساحة
    val Ix: Double,                 // mm⁴ - عزم القصور حول x
    val Iy: Double,                 // mm⁴ - عزم القصور حول y
    val Wx: Double,                 // mm³ - معامل المقطع المرن x (Sx)
    val Wy: Double,                 // mm³ - معامل المقطع المرن y (Sy)
    val Zx: Double,                 // mm³ - معامل المقطع اللدن x
    val Zy: Double,                 // mm³ - معامل المقطع اللدن y
    val rx: Double,                 // mm - نصف قطر الجيريشن x
    val ry: Double,                 // mm - نصف قطر الجيريشن y
    val weight: Double,             // kg/m - الوزن لكل متر
    val d: Double = h - 2 * tf,     // mm - عمق الوتر الصافي
    val J: Double = 0.0,            // mm⁴ - ثابت الالتواء
    val Cw: Double = 0.0            // mm⁶ - ثابت الانبعاج الالتوائي
)

// ═══════════════════════════════════════════════════════════
// قاعدة بيانات القطاعات القياسية
// ═══════════════════════════════════════════════════════════

object SteelSectionDatabase {

    val IPE_SECTIONS = listOf(
        SteelSectionProperties("IPE 100", SteelSectionType.IPE, 100.0, 55.0, 4.1, 5.7, 7.0, 1032.0, 1710000.0, 159000.0, 34200.0, 5790.0, 39400.0, 8910.0, 40.7, 12.4, 8.1),
        SteelSectionProperties("IPE 120", SteelSectionType.IPE, 120.0, 64.0, 4.4, 6.3, 7.0, 1321.0, 3180000.0, 277000.0, 53000.0, 8650.0, 60700.0, 13600.0, 49.0, 14.5, 10.4),
        SteelSectionProperties("IPE 140", SteelSectionType.IPE, 140.0, 73.0, 4.7, 6.9, 7.0, 1643.0, 5410000.0, 449000.0, 77300.0, 12300.0, 88300.0, 19300.0, 57.4, 16.5, 12.9),
        SteelSectionProperties("IPE 160", SteelSectionType.IPE, 160.0, 82.0, 5.0, 7.4, 9.0, 2009.0, 8690000.0, 683000.0, 109000.0, 16700.0, 124000.0, 26100.0, 65.8, 18.4, 15.8),
        SteelSectionProperties("IPE 180", SteelSectionType.IPE, 180.0, 91.0, 5.3, 8.0, 9.0, 2395.0, 13170000.0, 1010000.0, 146000.0, 22200.0, 166000.0, 34600.0, 74.2, 20.5, 18.8),
        SteelSectionProperties("IPE 200", SteelSectionType.IPE, 200.0, 100.0, 5.6, 8.5, 12.0, 2848.0, 19430000.0, 1424000.0, 194000.0, 28500.0, 221000.0, 44600.0, 82.6, 22.4, 22.4),
        SteelSectionProperties("IPE 220", SteelSectionType.IPE, 220.0, 110.0, 5.9, 9.2, 12.0, 3337.0, 27720000.0, 2049000.0, 252000.0, 37300.0, 285000.0, 58100.0, 91.1, 24.8, 26.2),
        SteelSectionProperties("IPE 240", SteelSectionType.IPE, 240.0, 120.0, 6.2, 9.8, 15.0, 3912.0, 38920000.0, 2840000.0, 324000.0, 47300.0, 367000.0, 73900.0, 99.7, 26.9, 30.7),
        SteelSectionProperties("IPE 270", SteelSectionType.IPE, 270.0, 135.0, 6.6, 10.2, 15.0, 4594.0, 57900000.0, 4199000.0, 429000.0, 62200.0, 484000.0, 97000.0, 112.3, 30.2, 36.1),
        SteelSectionProperties("IPE 300", SteelSectionType.IPE, 300.0, 150.0, 7.1, 10.7, 15.0, 5381.0, 83560000.0, 6038000.0, 557000.0, 80500.0, 628000.0, 125000.0, 124.6, 33.5, 42.2),
        SteelSectionProperties("IPE 330", SteelSectionType.IPE, 330.0, 160.0, 7.5, 11.5, 18.0, 6261.0, 117700000.0, 7881000.0, 713000.0, 98500.0, 804000.0, 154000.0, 137.1, 35.5, 49.1),
        SteelSectionProperties("IPE 360", SteelSectionType.IPE, 360.0, 170.0, 8.0, 12.7, 18.0, 7273.0, 162700000.0, 10430000.0, 904000.0, 123000.0, 1019000.0, 191000.0, 149.5, 37.9, 57.1),
        SteelSectionProperties("IPE 400", SteelSectionType.IPE, 400.0, 180.0, 8.6, 13.5, 21.0, 8446.0, 231300000.0, 13180000.0, 1156000.0, 146000.0, 1307000.0, 229000.0, 165.5, 39.5, 66.3),
        SteelSectionProperties("IPE 450", SteelSectionType.IPE, 450.0, 190.0, 9.4, 14.6, 21.0, 9882.0, 337400000.0, 16760000.0, 1500000.0, 176000.0, 1702000.0, 276000.0, 184.8, 41.2, 77.6),
        SteelSectionProperties("IPE 500", SteelSectionType.IPE, 500.0, 200.0, 10.2, 16.0, 21.0, 11550.0, 482000000.0, 21370000.0, 1928000.0, 214000.0, 2194000.0, 336000.0, 204.3, 43.0, 90.7),
        SteelSectionProperties("IPE 550", SteelSectionType.IPE, 550.0, 210.0, 11.1, 17.2, 24.0, 13440.0, 671200000.0, 26670000.0, 2441000.0, 254000.0, 2788000.0, 401000.0, 223.5, 44.5, 105.5),
        SteelSectionProperties("IPE 600", SteelSectionType.IPE, 600.0, 220.0, 12.0, 19.0, 24.0, 15600.0, 920800000.0, 33870000.0, 3069000.0, 308000.0, 3512000.0, 486000.0, 243.0, 46.6, 122.4),
    )

    val HEB_SECTIONS = listOf(
        SteelSectionProperties("HEB 100", SteelSectionType.HEB, 100.0, 100.0, 6.0, 10.0, 12.0, 2604.0, 4494000.0, 1672000.0, 89900.0, 33400.0, 104200.0, 51400.0, 41.6, 25.3, 20.4),
        SteelSectionProperties("HEB 120", SteelSectionType.HEB, 120.0, 120.0, 6.5, 11.0, 12.0, 3401.0, 8644000.0, 3178000.0, 144100.0, 52960.0, 165200.0, 81000.0, 50.4, 30.6, 26.7),
        SteelSectionProperties("HEB 140", SteelSectionType.HEB, 140.0, 140.0, 7.0, 12.0, 12.0, 4296.0, 15090000.0, 5501000.0, 215500.0, 78600.0, 245400.0, 120000.0, 59.3, 35.8, 33.7),
        SteelSectionProperties("HEB 160", SteelSectionType.HEB, 160.0, 160.0, 8.0, 13.0, 15.0, 5425.0, 24920000.0, 8892000.0, 311500.0, 111200.0, 354000.0, 170000.0, 67.8, 40.5, 42.6),
        SteelSectionProperties("HEB 200", SteelSectionType.HEB, 200.0, 200.0, 9.0, 15.0, 18.0, 7808.0, 56960000.0, 20030000.0, 569600.0, 200300.0, 642500.0, 305800.0, 85.4, 50.7, 61.3),
        SteelSectionProperties("HEB 240", SteelSectionType.HEB, 240.0, 240.0, 10.0, 17.0, 21.0, 10600.0, 112600000.0, 39230000.0, 938300.0, 327000.0, 1053000.0, 498400.0, 103.1, 60.8, 83.2),
        SteelSectionProperties("HEB 260", SteelSectionType.HEB, 260.0, 260.0, 10.0, 17.5, 24.0, 11840.0, 149200000.0, 51350000.0, 1148000.0, 395000.0, 1283000.0, 602200.0, 112.2, 65.8, 93.0),
        SteelSectionProperties("HEB 300", SteelSectionType.HEB, 300.0, 300.0, 11.0, 19.0, 27.0, 14910.0, 251700000.0, 85630000.0, 1678000.0, 571000.0, 1869000.0, 870100.0, 129.9, 75.8, 117.0),
        SteelSectionProperties("HEB 360", SteelSectionType.HEB, 360.0, 300.0, 12.5, 22.5, 27.0, 18060.0, 431900000.0, 101400000.0, 2400000.0, 676000.0, 2683000.0, 1032000.0, 154.6, 74.9, 142.0),
        SteelSectionProperties("HEB 400", SteelSectionType.HEB, 400.0, 300.0, 13.5, 24.0, 27.0, 19780.0, 576800000.0, 108200000.0, 2884000.0, 721400.0, 3232000.0, 1104000.0, 170.7, 74.0, 155.0),
        SteelSectionProperties("HEB 500", SteelSectionType.HEB, 500.0, 300.0, 14.5, 28.0, 27.0, 23860.0, 1072000000.0, 126200000.0, 4287000.0, 842000.0, 4815000.0, 1292000.0, 211.9, 72.7, 187.0),
        SteelSectionProperties("HEB 600", SteelSectionType.HEB, 600.0, 300.0, 15.5, 30.0, 27.0, 27010.0, 1710000000.0, 135100000.0, 5701000.0, 900700.0, 6425000.0, 1385000.0, 251.6, 70.7, 212.0),
    )

    val UPN_SECTIONS = listOf(
        SteelSectionProperties("UPN 80", SteelSectionType.UPN, 80.0, 45.0, 6.0, 8.0, 8.0, 1104.0, 1060000.0, 194000.0, 26500.0, 6360.0, 31600.0, 11200.0, 31.0, 13.3, 8.64),
        SteelSectionProperties("UPN 100", SteelSectionType.UPN, 100.0, 50.0, 6.0, 8.5, 8.5, 1350.0, 2060000.0, 293000.0, 41200.0, 8490.0, 49000.0, 15100.0, 39.1, 14.7, 10.6),
        SteelSectionProperties("UPN 120", SteelSectionType.UPN, 120.0, 55.0, 7.0, 9.0, 9.0, 1700.0, 3640000.0, 432000.0, 60700.0, 11100.0, 72400.0, 19600.0, 46.2, 15.9, 13.4),
        SteelSectionProperties("UPN 140", SteelSectionType.UPN, 140.0, 60.0, 7.0, 10.0, 10.0, 2040.0, 6050000.0, 627000.0, 86400.0, 14800.0, 103000.0, 26100.0, 54.5, 17.5, 16.0),
        SteelSectionProperties("UPN 160", SteelSectionType.UPN, 160.0, 65.0, 7.5, 10.5, 10.5, 2400.0, 9250000.0, 854000.0, 116000.0, 18300.0, 138000.0, 32300.0, 62.1, 18.9, 18.8),
        SteelSectionProperties("UPN 200", SteelSectionType.UPN, 200.0, 75.0, 8.5, 11.5, 11.5, 3220.0, 19100000.0, 1480000.0, 191000.0, 27000.0, 228000.0, 47500.0, 77.0, 21.4, 25.3),
        SteelSectionProperties("UPN 240", SteelSectionType.UPN, 240.0, 85.0, 9.5, 13.0, 13.0, 4230.0, 35900000.0, 2480000.0, 300000.0, 39600.0, 358000.0, 69500.0, 92.1, 24.2, 33.2),
        SteelSectionProperties("UPN 300", SteelSectionType.UPN, 300.0, 100.0, 10.0, 16.0, 16.0, 5880.0, 80300000.0, 4950000.0, 535000.0, 67800.0, 632000.0, 118000.0, 116.8, 29.0, 46.2),
    )

    fun getSectionByName(name: String): SteelSectionProperties? {
        return (IPE_SECTIONS + HEB_SECTIONS + UPN_SECTIONS).find { it.name == name }
    }

    fun getSectionsByType(type: SteelSectionType): List<SteelSectionProperties> = when (type) {
        SteelSectionType.IPE -> IPE_SECTIONS
        SteelSectionType.HEB -> HEB_SECTIONS
        SteelSectionType.UPN -> UPN_SECTIONS
        else -> emptyList()
    }
}

// ═══════════════════════════════════════════════════════════
// أنواع البراغي
// ═══════════════════════════════════════════════════════════

enum class BoltGrade(
    val fyb: Double,   // N/mm²
    val fub: Double,   // N/mm²
    val displayName: String
) {
    GRADE_4_6(240.0, 400.0, "Grade 4.6"),
    GRADE_4_8(320.0, 400.0, "Grade 4.8"),
    GRADE_5_6(300.0, 500.0, "Grade 5.6"),
    GRADE_5_8(400.0, 500.0, "Grade 5.8"),
    GRADE_8_8(640.0, 800.0, "Grade 8.8"),
    GRADE_10_9(900.0, 1000.0, "Grade 10.9"),
    A325(660.0, 830.0, "ASTM A325"),
    A490(940.0, 1040.0, "ASTM A490")
}

enum class BoltDiameter(val d: Double, val area: Double, val tensileArea: Double, val displayName: String) {
    M12(12.0, 113.1, 84.3, "M12"),
    M14(14.0, 153.9, 115.0, "M14"),
    M16(16.0, 201.1, 157.0, "M16"),
    M18(18.0, 254.5, 192.0, "M18"),
    M20(20.0, 314.2, 245.0, "M20"),
    M22(22.0, 380.1, 303.0, "M22"),
    M24(24.0, 452.4, 353.0, "M24"),
    M27(27.0, 572.6, 459.0, "M27"),
    M30(30.0, 706.9, 561.0, "M30"),
    M36(36.0, 1017.9, 817.0, "M36")
}

// ═══════════════════════════════════════════════════════════
// أنواع اللحام
// ═══════════════════════════════════════════════════════════

enum class WeldType(val displayName: String) {
    FILLET("لحام زاوي (Fillet)"),
    GROOVE_FULL("لحام تجويفي كامل الاختراق (CJP)"),
    GROOVE_PARTIAL("لحام تجويفي جزئي الاختراق (PJP)"),
    PLUG("لحام سدادي (Plug)"),
    SLOT("لحام شقي (Slot)")
}

// ═══════════════════════════════════════════════════════════
// نتائج التصميم المعدني
// ═══════════════════════════════════════════════════════════

data class SteelBeamResult(
    val selectedSection: SteelSectionProperties,
    val momentCapacity: Double,      // kN.m
    val shearCapacity: Double,       // kN
    val deflectionActual: Double,    // mm
    val deflectionAllowable: Double, // mm
    val lateralBucklingMoment: Double, // kN.m
    val compactnessClass: String,    // Compact/Non-compact/Slender
    val utilizationRatio: Double,    // نسبة الاستغلال
    val isSafe: Boolean,
    val equations: List<com.civilengineer.assistant.models.EquationStep>,
    val codeChecks: List<com.civilengineer.assistant.models.CodeCheck>
)

data class SteelColumnResult(
    val selectedSection: SteelSectionProperties,
    val axialCapacity: Double,       // kN
    val criticalBucklingLoad: Double,// kN
    val slendernessRatioX: Double,
    val slendernessRatioY: Double,
    val reductionFactor: Double,     // χ
    val compactnessClass: String,
    val utilizationRatio: Double,
    val isSafe: Boolean,
    val equations: List<com.civilengineer.assistant.models.EquationStep>,
    val codeChecks: List<com.civilengineer.assistant.models.CodeCheck>
)

data class BoltedConnectionResult(
    val numberOfBolts: Int,
    val boltDiameter: BoltDiameter,
    val boltGrade: BoltGrade,
    val shearCapacityPerBolt: Double,     // kN
    val bearingCapacityPerBolt: Double,   // kN
    val tensionCapacityPerBolt: Double,   // kN
    val totalCapacity: Double,            // kN
    val rows: Int,
    val columns: Int,
    val edgeDistance: Double,              // mm
    val pitch: Double,                    // mm
    val gauge: Double,                    // mm
    val utilizationRatio: Double,
    val isSafe: Boolean,
    val equations: List<com.civilengineer.assistant.models.EquationStep>,
    val codeChecks: List<com.civilengineer.assistant.models.CodeCheck>
)

data class WeldedConnectionResult(
    val weldType: WeldType,
    val weldSize: Double,                 // mm
    val weldLength: Double,               // mm
    val weldCapacity: Double,             // kN
    val effectiveThroat: Double,          // mm
    val utilizationRatio: Double,
    val isSafe: Boolean,
    val equations: List<com.civilengineer.assistant.models.EquationStep>
)

data class BasePlateResult(
    val plateLength: Double,              // mm
    val plateWidth: Double,               // mm
    val plateThickness: Double,           // mm
    val anchorBoltDiameter: Double,       // mm
    val numberOfAnchorBolts: Int,
    val bearingPressure: Double,          // MPa
    val isSafe: Boolean,
    val equations: List<com.civilengineer.assistant.models.EquationStep>
)

data class TrussDesignResult(
    val topChordForce: Double,            // kN
    val bottomChordForce: Double,         // kN
    val maxDiagonalForce: Double,         // kN
    val topChordSection: String,
    val bottomChordSection: String,
    val diagonalSection: String,
    val maxDeflection: Double,            // mm
    val isSafe: Boolean,
    val memberForces: List<TrussMemberForce>,
    val equations: List<com.civilengineer.assistant.models.EquationStep>
)

data class TrussMemberForce(
    val memberName: String,
    val force: Double,                    // kN (+ tension, - compression)
    val isTension: Boolean,
    val selectedSection: String,
    val utilizationRatio: Double
)
