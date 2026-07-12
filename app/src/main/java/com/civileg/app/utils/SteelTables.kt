package com.civileg.app.utils

object SteelTables {

    /**
     * Standard Steel Section Properties (IPE, HEA, HEB, UPN, Angle, etc.)
     *
     * Axis convention:
     *   European convention: iy = strong axis (major), iz = weak axis (minor)
     *   American convention:  Ix = strong axis (major), Iy = weak axis (minor)
     *
     * In this file, the European naming is used for the data class fields.
     * Alias properties (Ix_strong, Iy_weak) are provided for compatibility
     * with the AISC/ECP SteelDesignEngine which uses American convention.
     */
    data class SectionProperties(
        val name: String,
        val depth: Double,       // h (mm)
        val width: Double,       // b (mm)
        val tw: Double,          // web thickness (mm)
        val tf: Double,          // flange thickness (mm)
        val area: Double,        // cm²
        val weight: Double,      // kg/m
        val iy: Double,          // cm⁴ — Strong axis moment of inertia (European y-axis)
        val iz: Double,          // cm⁴ — Weak axis moment of inertia (European z-axis)
        val sy: Double,          // cm³ — Elastic Section Modulus, strong axis
        val sz: Double,          // cm³ — Elastic Section Modulus, weak axis
        val ry: Double,          // cm — Radius of gyration, strong axis
        val rz: Double,         // cm — Radius of gyration, weak axis
        val Sx: Double = 0.0,   // cm³ — Elastic Section Modulus, strong axis (American naming alias)
        val Sy_aisc: Double = 0.0,   // cm³ — Elastic Section Modulus, weak axis (American naming alias)
        val Zx: Double = 0.0,   // cm³ — Plastic Section Modulus, strong axis
        val Zy: Double = 0.0,   // cm³ — Plastic Section Modulus, weak axis
        val J: Double = 0.0,    // cm⁴ — Torsional constant (St. Venant)
        val Cw: Double = 0.0    // cm⁶ — Warping constant
    ) {
        // European convention: iy = strong axis, iz = weak axis
        // American convention: Ix = strong axis, Iy = weak axis
        val Ix_strong: Double get() = iy  // alias for compatibility with SteelDesignEngine
        val Iy_weak: Double get() = iz    // alias for compatibility with SteelDesignEngine

        /** Effective Sx: uses the provided Sx value if > 0, otherwise falls back to sy */
        val effectiveSx: Double get() = if (Sx > 0.0) Sx else sy

        /** Effective Zx: uses the provided Zx value if > 0, otherwise approximates as 1.12 × Sx */
        val effectiveZx: Double get() = if (Zx > 0.0) Zx else effectiveSx * 1.12
    }

    val ipeSections = listOf(
        SectionProperties("IPE 80", 80.0, 46.0, 3.8, 5.2, 7.64, 6.0, 80.1, 8.49, 20.0, 3.69, 3.24, 1.05,
            Sx = 20.0, Zx = 23.2, J = 1.04),
        SectionProperties("IPE 100", 100.0, 55.0, 4.1, 5.7, 10.3, 8.1, 171.0, 15.9, 34.2, 5.79, 4.07, 1.24,
            Sx = 34.2, Sy_aisc = 5.79, Zx = 39.4, Zy = 9.15, J = 1.41, Cw = 5770.0),
        SectionProperties("IPE 120", 120.0, 64.0, 4.4, 6.3, 13.2, 10.4, 318.0, 27.7, 53.0, 8.65, 4.90, 1.45,
            Sx = 53.0, Zx = 60.7, J = 2.58),
        SectionProperties("IPE 140", 140.0, 73.0, 4.7, 6.9, 16.4, 12.9, 541.0, 44.9, 77.3, 12.3, 5.74, 1.65,
            Sx = 77.3, Zx = 88.3, J = 4.40),
        SectionProperties("IPE 160", 160.0, 82.0, 5.0, 7.4, 20.1, 15.8, 869.0, 68.3, 109.0, 16.7, 6.58, 1.84,
            Sx = 109.0, Zx = 124.0, J = 7.27),
        SectionProperties("IPE 180", 180.0, 91.0, 5.3, 8.0, 23.9, 18.8, 1317.0, 101.0, 146.0, 22.2, 7.42, 2.05,
            Sx = 146.0, Zx = 166.0, J = 11.3),
        SectionProperties("IPE 200", 200.0, 100.0, 5.6, 8.5, 28.5, 22.4, 1943.0, 142.0, 194.0, 28.5, 8.26, 2.24,
            Sx = 194.0, Sy_aisc = 28.5, Zx = 220.0, Zy = 43.9, J = 14.1, Cw = 54900.0),
        SectionProperties("IPE 220", 220.0, 110.0, 5.9, 9.2, 33.4, 26.2, 2772.0, 205.0, 252.0, 37.3, 9.11, 2.48,
            Sx = 252.0, Zx = 285.0, J = 20.7),
        SectionProperties("IPE 240", 240.0, 120.0, 6.2, 9.8, 39.1, 30.7, 3892.0, 284.0, 324.0, 47.3, 9.97, 2.69,
            Sx = 324.0, Zx = 367.0, J = 29.5),
        SectionProperties("IPE 270", 270.0, 135.0, 6.6, 10.2, 45.9, 36.1, 5790.0, 420.0, 429.0, 62.2, 11.2, 3.02,
            Sx = 429.0, Zx = 484.0, J = 43.8),
        SectionProperties("IPE 300", 300.0, 150.0, 7.1, 10.7, 53.8, 42.2, 8356.0, 604.0, 557.0, 80.5, 12.5, 3.35,
            Sx = 557.0, Sy_aisc = 80.5, Zx = 628.0, Zy = 123.0, J = 39.7, Cw = 197000.0),
        SectionProperties("IPE 330", 330.0, 160.0, 7.5, 11.5, 62.6, 49.1, 11770.0, 788.0, 713.0, 98.5, 13.7, 3.55,
            Sx = 713.0, Zx = 804.0, J = 55.1),
        SectionProperties("IPE 360", 360.0, 170.0, 8.0, 12.7, 72.7, 57.1, 16270.0, 1043.0, 904.0, 123.0, 15.0, 3.79,
            Sx = 904.0, Zx = 1019.0, J = 76.4),
        SectionProperties("IPE 400", 400.0, 180.0, 8.6, 13.5, 84.5, 66.3, 23130.0, 1318.0, 1160.0, 146.0, 16.5, 3.95,
            Sx = 1160.0, Zx = 1307.0, J = 107.0),
        SectionProperties("IPE 450", 450.0, 190.0, 9.4, 14.6, 98.8, 77.6, 33740.0, 1676.0, 1500.0, 176.0, 18.5, 4.12,
            Sx = 1500.0, Zx = 1688.0, J = 157.0),
        SectionProperties("IPE 500", 500.0, 200.0, 10.2, 16.0, 116.0, 90.7, 48200.0, 2142.0, 1930.0, 214.0, 20.4, 4.31,
            Sx = 1930.0, Zx = 2159.0, J = 221.0),
        SectionProperties("IPE 550", 550.0, 210.0, 11.1, 17.2, 134.0, 106.0, 67120.0, 2668.0, 2440.0, 254.0, 22.3, 4.45,
            Sx = 2440.0, Zx = 2727.0, J = 304.0),
        SectionProperties("IPE 600", 600.0, 220.0, 12.0, 19.0, 156.0, 122.0, 92080.0, 3387.0, 3070.0, 308.0, 24.3, 4.66,
            Sx = 3070.0, Zx = 3425.0, J = 414.0)
    )

    val heaSections = listOf(
        SectionProperties("HEA 100", 96.0, 100.0, 5.0, 8.0, 21.2, 16.7, 349.0, 134.0, 72.8, 26.8, 4.06, 2.51,
            Sx = 72.8, Sy_aisc = 26.8, Zx = 83.1, Zy = 41.2, J = 4.52, Cw = 12400.0),
        SectionProperties("HEA 120", 114.0, 120.0, 5.0, 8.0, 25.3, 19.9, 606.0, 231.0, 106.0, 38.5, 4.89, 3.02,
            Sx = 106.0, Zx = 120.0, J = 7.38),
        SectionProperties("HEA 140", 133.0, 140.0, 5.5, 8.5, 31.4, 24.7, 1033.0, 389.0, 155.0, 55.6, 5.73, 3.52,
            Sx = 155.0, Zx = 176.0, J = 12.4),
        SectionProperties("HEA 160", 152.0, 160.0, 6.0, 9.0, 38.8, 30.4, 1673.0, 616.0, 220.0, 76.9, 6.57, 3.98,
            Sx = 220.0, Zx = 249.0, J = 19.5),
        SectionProperties("HEA 180", 171.0, 180.0, 6.0, 9.5, 45.3, 35.5, 2510.0, 925.0, 294.0, 103.0, 7.45, 4.52,
            Sx = 294.0, Zx = 332.0, J = 27.9),
        SectionProperties("HEA 200", 190.0, 200.0, 6.5, 10.0, 53.8, 42.3, 3692.0, 1336.0, 389.0, 134.0, 8.28, 4.98,
            Sx = 389.0, Sy_aisc = 134.0, Zx = 439.0, Zy = 205.0, J = 38.9, Cw = 142000.0),
        SectionProperties("HEA 220", 210.0, 220.0, 7.0, 11.0, 64.3, 50.5, 5410.0, 1955.0, 515.0, 178.0, 9.17, 5.51,
            Sx = 515.0, Zx = 581.0, J = 53.5),
        SectionProperties("HEA 240", 230.0, 240.0, 7.5, 12.0, 76.8, 60.3, 7763.0, 2769.0, 675.0, 231.0, 10.1, 6.00,
            Sx = 675.0, Zx = 761.0, J = 73.5),
        SectionProperties("HEA 260", 250.0, 260.0, 7.5, 12.5, 86.8, 68.2, 10450.0, 3668.0, 836.0, 282.0, 11.0, 6.50,
            Sx = 836.0, Zx = 940.0, J = 93.8),
        SectionProperties("HEA 280", 270.0, 280.0, 8.0, 13.0, 97.3, 76.4, 13670.0, 4763.0, 1010.0, 340.0, 11.9, 7.00,
            Sx = 1010.0, Zx = 1135.0, J = 121.0),
        SectionProperties("HEA 300", 290.0, 300.0, 8.5, 14.0, 112.5, 88.3, 18260.0, 6310.0, 1260.0, 421.0, 12.7, 7.49,
            Sx = 1260.0, Sy_aisc = 421.0, Zx = 1413.0, Zy = 640.0, J = 158.0, Cw = 885000.0),
        SectionProperties("HEA 400", 390.0, 300.0, 11.0, 19.0, 159.0, 125.0, 45070.0, 8564.0, 2310.0, 571.0, 16.8, 7.34,
            Sx = 2310.0, Zx = 2563.0, J = 372.0),
        SectionProperties("HEA 500", 490.0, 300.0, 12.0, 23.0, 197.5, 155.0, 86970.0, 10370.0, 3550.0, 691.0, 21.0, 7.24,
            Sx = 3550.0, Zx = 3917.0, J = 598.0),
        SectionProperties("HEA 600", 590.0, 300.0, 13.0, 25.0, 226.5, 178.0, 141200.0, 11270.0, 4790.0, 751.0, 25.0, 7.05,
            Sx = 4790.0, Zx = 5256.0, J = 858.0)
    )

    val hebSections = listOf(
        SectionProperties("HEB 100", 100.0, 100.0, 6.0, 10.0, 26.0, 20.4, 450.0, 167.0, 89.9, 33.5, 4.16, 2.53,
            Sx = 89.9, Sy_aisc = 33.5, Zx = 105.0, Zy = 51.2, J = 6.05, Cw = 15700.0),
        SectionProperties("HEB 120", 120.0, 120.0, 6.5, 11.0, 34.0, 26.7, 864.0, 318.0, 144.0, 52.9, 5.04, 3.06,
            Sx = 144.0, Zx = 168.0, J = 10.4),
        SectionProperties("HEB 140", 140.0, 140.0, 7.0, 12.0, 43.0, 33.7, 1509.0, 550.0, 216.0, 78.5, 5.93, 3.58,
            Sx = 216.0, Zx = 250.0, J = 17.4),
        SectionProperties("HEB 160", 160.0, 160.0, 8.0, 13.0, 54.3, 42.6, 2492.0, 882.0, 311.0, 110.0, 6.78, 4.03,
            Sx = 311.0, Zx = 359.0, J = 27.0),
        SectionProperties("HEB 180", 180.0, 180.0, 8.5, 14.0, 65.3, 51.2, 3831.0, 1363.0, 426.0, 151.0, 7.66, 4.57,
            Sx = 426.0, Zx = 492.0, J = 39.6),
        SectionProperties("HEB 200", 200.0, 200.0, 9.0, 15.0, 78.1, 61.3, 5696.0, 2003.0, 570.0, 200.0, 8.54, 5.07,
            Sx = 570.0, Sy_aisc = 200.0, Zx = 657.0, Zy = 308.0, J = 56.4, Cw = 262000.0),
        SectionProperties("HEB 220", 220.0, 220.0, 9.5, 16.0, 91.0, 71.5, 8091.0, 2843.0, 736.0, 258.0, 9.43, 5.59,
            Sx = 736.0, Zx = 845.0, J = 76.7),
        SectionProperties("HEB 240", 240.0, 240.0, 10.0, 17.0, 106.0, 83.2, 11260.0, 3923.0, 938.0, 327.0, 10.3, 6.08,
            Sx = 938.0, Zx = 1075.0, J = 102.0),
        SectionProperties("HEB 260", 260.0, 260.0, 10.0, 17.5, 118.4, 93.0, 14920.0, 5135.0, 1150.0, 395.0, 11.2, 6.58,
            Sx = 1150.0, Zx = 1313.0, J = 130.0),
        SectionProperties("HEB 300", 300.0, 300.0, 11.0, 19.0, 149.1, 117.0, 25170.0, 8563.0, 1680.0, 571.0, 13.0, 7.58,
            Sx = 1680.0, Sy_aisc = 571.0, Zx = 1943.0, Zy = 878.0, J = 195.0, Cw = 1620000.0),
        SectionProperties("HEB 400", 400.0, 300.0, 13.5, 24.0, 197.8, 155.0, 57680.0, 10820.0, 2880.0, 721.0, 17.1, 7.40,
            Sx = 2880.0, Zx = 3291.0, J = 449.0),
        SectionProperties("HEB 500", 500.0, 300.0, 14.5, 28.0, 238.6, 187.0, 107200.0, 12620.0, 4290.0, 841.0, 21.2, 7.27,
            Sx = 4290.0, Zx = 4882.0, J = 745.0)
    )

    val upnSections = listOf(
        SectionProperties("UPN 80", 80.0, 45.0, 6.0, 8.0, 11.0, 8.64, 106.0, 19.4, 26.5, 6.36, 3.10, 1.33,
            Sx = 26.5, Zx = 31.8, J = 2.70),
        SectionProperties("UPN 100", 100.0, 50.0, 6.0, 8.5, 13.5, 10.6, 206.0, 29.3, 41.2, 8.49, 3.91, 1.47,
            Sx = 41.2, Zx = 49.7, J = 4.38),
        SectionProperties("UPN 120", 120.0, 55.0, 7.0, 9.0, 17.0, 13.4, 364.0, 43.2, 60.7, 11.1, 4.62, 1.59,
            Sx = 60.7, Zx = 72.5, J = 7.06),
        SectionProperties("UPN 140", 140.0, 60.0, 7.0, 10.0, 20.4, 16.0, 605.0, 62.7, 86.4, 14.8, 5.45, 1.75,
            Sx = 86.4, Zx = 103.0, J = 11.1),
        SectionProperties("UPN 160", 160.0, 65.0, 7.5, 10.5, 24.0, 18.8, 925.0, 85.3, 116.0, 18.3, 6.21, 1.89,
            Sx = 116.0, Zx = 138.0, J = 16.7),
        SectionProperties("UPN 180", 180.0, 70.0, 8.0, 11.0, 28.0, 22.0, 1350.0, 114.0, 150.0, 22.4, 6.95, 2.02,
            Sx = 150.0, Zx = 178.0, J = 24.4),
        SectionProperties("UPN 200", 200.0, 75.0, 8.5, 11.5, 32.2, 25.3, 1910.0, 148.0, 191.0, 27.0, 7.70, 2.14,
            Sx = 191.0, Sy_aisc = 27.0, Zx = 226.0, Zy = 44.5, J = 33.6, Cw = 30400.0),
        SectionProperties("UPN 220", 220.0, 80.0, 9.0, 12.5, 37.4, 29.4, 2690.0, 197.0, 245.0, 33.6, 8.48, 2.30,
            Sx = 245.0, Zx = 290.0, J = 45.6),
        SectionProperties("UPN 240", 240.0, 85.0, 9.5, 13.0, 42.3, 33.2, 3600.0, 248.0, 300.0, 39.6, 9.22, 2.42,
            Sx = 300.0, Zx = 354.0, J = 60.0),
        SectionProperties("UPN 300", 300.0, 100.0, 10.0, 16.0, 58.8, 46.2, 8030.0, 495.0, 535.0, 67.8, 11.7, 2.90,
            Sx = 535.0, Zx = 631.0, J = 115.0)
    )

    val angleSections = listOf(
        SectionProperties("L 50x50x5", 50.0, 50.0, 5.0, 5.0, 4.80, 3.77, 11.0, 11.0, 3.05, 3.05, 1.51, 1.51,
            Sx = 3.05, Sy_aisc = 3.05, Zx = 4.62, Zy = 4.62, J = 0.724),
        SectionProperties("L 60x60x6", 60.0, 60.0, 6.0, 6.0, 6.91, 5.42, 22.8, 22.8, 5.29, 5.29, 1.82, 1.82,
            Sx = 5.29, Sy_aisc = 5.29, Zx = 8.02, Zy = 8.02, J = 1.55),
        SectionProperties("L 70x70x7", 70.0, 70.0, 7.0, 7.0, 9.40, 7.38, 42.4, 42.4, 8.41, 8.41, 2.12, 2.12,
            Sx = 8.41, Sy_aisc = 8.41, Zx = 12.8, Zy = 12.8, J = 2.82),
        SectionProperties("L 80x80x8", 80.0, 80.0, 8.0, 8.0, 12.3, 9.66, 72.2, 72.2, 12.6, 12.6, 2.42, 2.42,
            Sx = 12.6, Sy_aisc = 12.6, Zx = 19.1, Zy = 19.1, J = 4.70),
        SectionProperties("L 100x100x10", 100.0, 100.0, 10.0, 10.0, 19.2, 15.0, 177.0, 177.0, 24.6, 24.6, 3.04, 3.04,
            Sx = 24.6, Sy_aisc = 24.6, Zx = 37.2, Zy = 37.2, J = 11.3),
        SectionProperties("L 120x120x12", 120.0, 120.0, 12.0, 12.0, 27.5, 21.6, 368.0, 368.0, 42.7, 42.7, 3.66, 3.66,
            Sx = 42.7, Sy_aisc = 42.7, Zx = 64.5, Zy = 64.5, J = 23.2),
        SectionProperties("L 150x150x15", 150.0, 150.0, 15.0, 15.0, 43.0, 33.8, 897.0, 897.0, 85.0, 85.0, 4.57, 4.57,
            Sx = 85.0, Sy_aisc = 85.0, Zx = 128.0, Zy = 128.0, J = 67.8)
    )

    fun getAllSections(): List<SectionProperties> = ipeSections + heaSections + hebSections + upnSections + angleSections

    fun getSectionByName(name: String): SectionProperties? = getAllSections().find {
        it.name.equals(name, ignoreCase = true)
    }

    /**
     * Search by number only (e.g., "200" will find IPE 200, HEA 200, etc.)
     */
    fun searchByNumber(number: String): List<SectionProperties> {
        return getAllSections().filter { it.name.contains(number) }
    }

    fun getSectionByDepth(depth: Double, type: String = "IPE"): SectionProperties? {
        val list = when(type) {
            "IPE" -> ipeSections
            "HEA" -> heaSections
            "HEB" -> hebSections
            "UPN" -> upnSections
            "ANGLE" -> angleSections
            else -> ipeSections
        }
        return list.minByOrNull { kotlin.math.abs(it.depth - depth) }
    }
}