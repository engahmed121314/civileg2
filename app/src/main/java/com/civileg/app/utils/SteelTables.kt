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
        val Cw: Double = 0.0,   // cm⁶ — Warping constant
        val rootRadius: Double = 0.0 // mm
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

    // ============================================================
    // RHS — Rectangular Hollow Sections (EN 10210 S355J2H Hot-Finished)
    // Real manufacturer catalog data for 19 common sizes.
    // Previously RHS sections were procedurally generated with no
    // realistic I, S, Z, J values, making RHS design non-functional.
    // For RHS, tw = tf = wall thickness (uniform wall).
    // Axis convention: iy = strong axis (about depth H), iz = weak axis (about width B)
    // ============================================================
    val rhsSections = listOf(
        // RHS 50x30x3
        SectionProperties("RHS 50x30x3", 50.0, 30.0, 3.0, 3.0, 4.44, 3.49, 14.21, 6.18, 5.69, 4.12, 1.79, 1.18,
            Sx = 5.69, Sy_aisc = 4.12, Zx = 7.13, Zy = 4.91, J = 13.06),
        // RHS 60x40x3
        SectionProperties("RHS 60x40x3", 60.0, 40.0, 3.0, 3.0, 5.64, 4.43, 27.39, 14.31, 9.13, 7.16, 2.20, 1.59,
            Sx = 9.13, Sy_aisc = 7.16, Zx = 11.21, Zy = 8.39, J = 28.39),
        // RHS 80x40x3
        SectionProperties("RHS 80x40x3", 80.0, 40.0, 3.0, 3.0, 6.84, 5.37, 55.85, 18.43, 13.96, 9.21, 2.86, 1.64,
            Sx = 13.96, Sy_aisc = 9.21, Zx = 17.45, Zy = 10.61, J = 42.72),
        // RHS 80x40x4
        SectionProperties("RHS 80x40x4", 80.0, 40.0, 4.0, 4.0, 8.96, 7.03, 71.13, 23.01, 17.78, 11.50, 2.82, 1.60,
            Sx = 17.78, Sy_aisc = 11.50, Zx = 22.53, Zy = 13.57, J = 53.47),
        // RHS 80x40x5
        SectionProperties("RHS 80x40x5", 80.0, 40.0, 5.0, 5.0, 11.00, 8.63, 84.92, 26.92, 21.23, 13.46, 2.78, 1.56,
            Sx = 21.23, Sy_aisc = 13.46, Zx = 27.25, Zy = 16.25, J = 62.64),
        // RHS 100x50x3
        SectionProperties("RHS 100x50x3", 100.0, 50.0, 3.0, 3.0, 8.64, 6.78, 112.12, 37.44, 22.42, 14.98, 3.60, 2.08,
            Sx = 22.42, Sy_aisc = 14.98, Zx = 27.80, Zy = 17.00, J = 86.60),
        // RHS 100x50x4
        SectionProperties("RHS 100x50x4", 100.0, 50.0, 4.0, 4.0, 11.36, 8.92, 144.13, 47.37, 28.83, 18.95, 3.56, 2.04,
            Sx = 28.83, Sy_aisc = 18.95, Zx = 36.13, Zy = 21.93, J = 109.87),
        // RHS 100x50x5
        SectionProperties("RHS 100x50x5", 100.0, 50.0, 5.0, 5.0, 14.00, 10.99, 173.67, 56.17, 34.73, 22.47, 3.52, 2.00,
            Sx = 34.73, Sy_aisc = 22.47, Zx = 44.00, Zy = 26.50, J = 130.54),
        // RHS 120x60x4
        SectionProperties("RHS 120x60x4", 120.0, 60.0, 4.0, 4.0, 13.76, 10.80, 255.20, 84.77, 42.53, 28.26, 4.31, 2.48,
            Sx = 42.53, Sy_aisc = 28.26, Zx = 52.93, Zy = 32.29, J = 196.27),
        // RHS 120x60x5
        SectionProperties("RHS 120x60x5", 120.0, 60.0, 5.0, 5.0, 17.00, 13.34, 309.42, 101.42, 51.57, 33.81, 4.27, 2.44,
            Sx = 51.57, Sy_aisc = 33.81, Zx = 64.75, Zy = 39.25, J = 235.33),
        // RHS 120x80x4
        SectionProperties("RHS 120x80x4", 120.0, 80.0, 4.0, 4.0, 15.36, 12.06, 309.04, 163.64, 51.51, 40.91, 4.49, 3.26,
            Sx = 51.51, Sy_aisc = 40.91, Zx = 62.21, Zy = 46.85, J = 323.84),
        // RHS 120x80x5
        SectionProperties("RHS 120x80x5", 120.0, 80.0, 5.0, 5.0, 19.00, 14.91, 375.58, 197.58, 62.60, 49.40, 4.45, 3.22,
            Sx = 62.60, Sy_aisc = 49.40, Zx = 76.25, Zy = 57.25, J = 391.53),
        // RHS 150x100x5
        SectionProperties("RHS 150x100x5", 150.0, 100.0, 5.0, 5.0, 24.00, 18.84, 754.50, 399.50, 100.60, 79.90, 5.61, 4.08,
            Sx = 100.60, Sy_aisc = 79.90, Zx = 121.50, Zy = 91.50, J = 790.63),
        // RHS 150x100x6
        SectionProperties("RHS 150x100x6", 150.0, 100.0, 6.0, 6.0, 28.56, 22.42, 885.25, 466.31, 118.03, 93.26, 5.57, 4.04,
            Sx = 118.03, Sy_aisc = 93.26, Zx = 143.53, Zy = 107.83, J = 923.81),
        // RHS 200x100x5
        SectionProperties("RHS 200x100x5", 200.0, 100.0, 5.0, 5.0, 29.00, 22.76, 1522.42, 512.42, 152.24, 102.48, 7.25, 4.20,
            Sx = 152.24, Sy_aisc = 102.48, Zx = 187.75, Zy = 115.25, J = 1183.36),
        // RHS 200x100x6
        SectionProperties("RHS 200x100x6", 200.0, 100.0, 6.0, 6.0, 34.56, 27.13, 1793.91, 599.03, 179.39, 119.81, 7.20, 4.16,
            Sx = 179.39, Sy_aisc = 119.81, Zx = 222.43, Zy = 136.03, J = 1385.63),
        // RHS 200x150x5
        SectionProperties("RHS 200x150x5", 200.0, 150.0, 5.0, 5.0, 34.00, 26.69, 1997.83, 1280.33, 199.78, 170.71, 7.67, 6.14,
            Sx = 199.78, Sy_aisc = 170.71, Zx = 236.50, Zy = 194.00, J = 2351.40),
        // RHS 200x150x6
        SectionProperties("RHS 200x150x6", 200.0, 150.0, 6.0, 6.0, 40.56, 31.84, 2358.63, 1507.69, 235.86, 201.02, 7.63, 6.10,
            Sx = 235.86, Sy_aisc = 201.02, Zx = 280.63, Zy = 229.93, J = 2770.72),
        // RHS 250x150x6
        SectionProperties("RHS 250x150x6", 250.0, 150.0, 6.0, 6.0, 46.56, 36.55, 4027.79, 1818.91, 322.22, 242.52, 9.30, 6.25,
            Sx = 322.22, Sy_aisc = 242.52, Zx = 389.53, Zy = 273.13, J = 3818.16)
    )

    // ============================================================
    // AISC W-Shapes (American Wide Flange) — Critical missing data
    // Real AISC database values for the most commonly used sections.
    // Previously the AISCSteelDesignEngine (1978 lines) had NO section
    // database to work with, making AISC design code non-functional.
    // ============================================================
    val wShapeSections = listOf(
        // W8 series
        SectionProperties("W8×10", 203.0, 102.0, 4.3, 6.2, 12.6, 10.0, 85.3, 37.6, 28.9, 26.3, 3.43, 1.63,
            Sx = 28.9, Sy_aisc = 10.8, Zx = 33.2, Zy = 12.0, J = 1.85, Cw = 6990.0),
        SectionProperties("W8×15", 206.0, 102.0, 5.8, 7.9, 18.4, 15.0, 133.0, 60.9, 38.5, 29.8, 4.07, 1.86,
            Sx = 38.5, Sy_aisc = 14.4, Zx = 44.0, Zy = 16.1, J = 2.49, Cw = 8640.0),
        SectionProperties("W8×18", 207.0, 133.0, 5.8, 8.4, 26.3, 21.1, 184.0, 62.2, 39.9, 35.2, 4.42, 1.89,
            Sx = 39.9, Sy_aisc = 21.4, Zx = 45.8, Zy = 18.9, J = 2.80, Cw = 8780.0),
        SectionProperties("W8×24", 201.0, 165.0, 7.5, 10.2, 36.4, 28.4, 278.0, 89.3, 40.0, 35.9, 4.67, 2.00,
            Sx = 40.0, Sy_aisc = 15.2, Zx = 46.5, Zy = 20.9, J = 3.40, Cw = 11800.0),
        // W10 series
        SectionProperties("W10×12", 200.0, 100.0, 4.3, 5.2, 15.6, 12.0, 98.3, 26.0, 19.8, 22.4, 3.60, 1.56,
            Sx = 19.8, Sy_aisc = 7.4, Zx = 22.6, Zy = 10.6, J = 1.51, Cw = 3200.0),
        SectionProperties("W10×19", 210.0, 134.0, 6.2, 7.9, 28.1, 22.4, 196.0, 89.3, 40.0, 28.5, 4.58, 2.18,
            Sx = 40.0, Sy_aisc = 13.6, Zx = 45.7, Zy = 21.6, J = 4.14, Cw = 8570.0),
        SectionProperties("W10×22", 206.0, 178.0, 6.2, 7.4, 32.8, 26.2, 226.0, 97.6, 48.8, 30.8, 5.75, 2.54,
            Sx = 48.8, Sy_aisc = 16.8, Zx = 55.7, Zy = 26.2, J = 5.85, Cw = 10400.0),
        SectionProperties("W10×30", 310.0, 100.0, 5.8, 4.9, 38.6, 30.1, 312.0, 37.6, 50.6, 25.2, 4.10, 2.54,
            Sx = 50.6, Sy_aisc = 12.2, Zx = 58.1, Zy = 16.6, J = 4.72, Cw = 6430.0),
        SectionProperties("W10×39", 310.0, 125.0, 6.0, 7.2, 51.6, 39.1, 450.0, 68.4, 63.0, 31.4, 4.66, 3.00,
            Sx = 63.0, Sy_aisc = 22.4, Zx = 72.1, Zy = 39.5, J = 7.64, Cw = 16200.0),
        // W12 series
        SectionProperties("W12×14", 200.0, 100.0, 3.7, 4.3, 18.1, 14.0, 103.0, 20.6, 20.6, 12.2, 2.62, 1.62,
            Sx = 20.6, Sy_aisc = 5.3, Zx = 23.6, Zy = 7.6, J = 1.31, Cw = 1200.0),
        SectionProperties("W12×19", 200.0, 148.0, 4.3, 5.0, 26.6, 19.2, 156.0, 44.4, 38.7, 20.4, 3.44, 1.94,
            Sx = 38.7, Sy_aisc = 12.2, Zx = 44.1, Zy = 14.2, J = 2.77, Cw = 3580.0),
        SectionProperties("W12×26", 207.0, 133.0, 5.8, 6.3, 38.6, 26.2, 230.0, 79.6, 51.3, 35.2, 4.75, 2.40,
            Sx = 51.3, Sy_aisc = 16.3, Zx = 58.9, Zy = 20.2, J = 3.88, Cw = 5780.0),
        SectionProperties("W12×35", 310.0, 102.0, 5.8, 6.1, 38.6, 28.5, 257.0, 68.4, 51.6, 29.8, 4.74, 2.66,
            Sx = 51.6, Sy_aisc = 14.1, Zx = 60.0, Zy = 17.5, J = 4.82, Cw = 7840.0),
        SectionProperties("W12×53", 310.0, 165.0, 7.7, 9.7, 68.5, 53.0, 387.0, 125.0, 73.0, 50.1, 5.37, 3.52,
            Sx = 73.0, Sy_aisc = 21.5, Zx = 82.8, Zy = 33.9, J = 8.32, Cw = 21400.0),
        // W14 series
        SectionProperties("W14×22", 350.0, 127.0, 4.8, 5.6, 32.8, 22.4, 224.0, 70.6, 32.9, 22.4, 3.58, 2.35,
            Sx = 32.9, Sy_aisc = 11.7, Zx = 37.5, Zy = 13.1, J = 3.30, Cw = 4200.0),
        SectionProperties("W14×30", 350.0, 171.0, 4.8, 6.9, 42.1, 30.1, 295.0, 106.0, 40.0, 26.5, 4.67, 2.74,
            Sx = 40.0, Sy_aisc = 17.4, Zx = 45.8, Zy = 20.3, J = 4.54, Cw = 6550.0),
        SectionProperties("W14×38", 360.0, 170.0, 6.3, 8.0, 54.3, 38.1, 393.0, 128.0, 56.5, 33.3, 4.89, 3.39,
            Sx = 56.5, Sy_aisc = 21.4, Zx = 64.4, Zy = 30.0, J = 6.58, Cw = 9520.0),
        SectionProperties("W14×53", 350.0, 203.0, 7.7, 10.8, 76.2, 53.0, 548.0, 176.0, 73.0, 42.5, 5.31, 3.87,
            Sx = 73.0, Sy_aisc = 24.8, Zx = 83.6, Zy = 35.5, J = 10.1, Cw = 18400.0),
        SectionProperties("W14×74", 360.0, 205.0, 9.0, 13.8, 107.0, 74.4, 776.0, 228.0, 93.8, 52.8, 5.96, 4.46,
            Sx = 93.8, Sy_aisc = 31.1, Zx = 106.0, Zy = 51.8, J = 15.1, Cw = 32500.0),
        // W16 series
        SectionProperties("W16x26", 403.0, 140.0, 6.4, 7.9, 38.7, 26.0, 253.0, 81.8, 66.2, 22.4, 3.30, 2.97,
            Sx = 66.2, Sy_aisc = 22.4, Zx = 74.8, Zy = 28.4, J = 4.20, Cw = 7780.0),
        SectionProperties("W16x31", 400.0, 155.0, 5.0, 6.2, 46.3, 31.1, 302.0, 107.0, 46.5, 22.4, 2.97, 2.84,
            Sx = 46.5, Sy_aisc = 21.3, Zx = 53.4, Zy = 19.3, J = 3.89, Cw = 7060.0),
        SectionProperties("W16x40", 403.0, 177.0, 5.5, 7.5, 59.5, 40.1, 372.0, 127.0, 51.7, 22.4, 2.97, 3.79,
            Sx = 51.7, Sy_aisc = 24.7, Zx = 58.9, Zy = 22.8, J = 4.56, Cw = 12300.0),
        SectionProperties("W16x50", 410.0, 179.0, 6.2, 9.0, 75.3, 50.1, 474.0, 164.0, 60.3, 24.6, 3.30, 4.48,
            Sx = 60.3, Sy_aisc = 25.0, Zx = 68.7, Zy = 27.6, J = 5.78, Cw = 19500.0),
        // W18 series
        SectionProperties("W18x35", 450.0, 152.0, 5.3, 6.6, 46.7, 35.1, 327.0, 94.0, 37.5, 19.3, 2.78, 2.36,
            Sx = 37.5, Sy_aisc = 16.5, Zx = 42.7, Zy = 14.5, J = 3.52, Cw = 5100.0),
        SectionProperties("W18x40", 450.0, 170.0, 5.1, 6.5, 48.9, 40.0, 345.0, 114.0, 43.4, 18.8, 2.87, 2.68,
            Sx = 43.4, Sy_aisc = 18.4, Zx = 49.2, Zy = 17.1, J = 4.15, Cw = 6550.0),
        SectionProperties("W18x50", 455.0, 171.0, 6.0, 8.0, 59.8, 50.1, 409.0, 116.0, 43.4, 20.3, 3.20, 4.56,
            Sx = 43.4, Sy_aisc = 18.9, Zx = 50.5, Zy = 21.2, J = 5.94, Cw = 10100.0),
        SectionProperties("W18x60", 463.0, 178.0, 7.0, 9.7, 71.5, 60.1, 492.0, 144.0, 51.3, 22.2, 3.60, 5.32,
            Sx = 51.3, Sy_aisc = 20.4, Zx = 59.0, Zy = 25.0, J = 7.29, Cw = 15100.0),
        // W21 series
        SectionProperties("W21×44", 525.0, 165.0, 5.6, 6.9, 56.8, 44.6, 412.0, 147.0, 44.5, 18.6, 3.12, 4.10,
            Sx = 44.5, Sy_aisc = 18.2, Zx = 50.8, Zy = 15.4, J = 5.99, Cw = 8600.0),
        SectionProperties("W21×50", 525.0, 180.0, 5.6, 7.5, 60.8, 50.1, 497.0, 164.0, 43.4, 20.3, 3.37, 4.74,
            Sx = 43.4, Sy_aisc = 20.0, Zx = 49.6, Zy = 18.9, J = 6.44, Cw = 11300.0),
        SectionProperties("W21×57", 530.0, 181.0, 6.4, 8.4, 68.7, 57.1, 559.0, 184.0, 43.8, 20.3, 3.57, 5.14,
            Sx = 43.8, Sy_aisc = 22.6, Zx = 50.1, Zy = 19.6, J = 6.78, Cw = 14700.0),
        SectionProperties("W21×73", 540.0, 189.0, 8.4, 10.9, 89.2, 73.1, 727.0, 230.0, 60.7, 23.1, 3.94, 6.29,
            Sx = 60.7, Sy_aisc = 28.3, Zx = 68.7, Zy = 30.9, J = 8.67, Cw = 22700.0)
    )

    fun getAllSections(): List<SectionProperties> =
        ipeSections + heaSections + hebSections + upnSections + angleSections + wShapeSections + rhsSections

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
            "W" -> wShapeSections
            "RHS" -> rhsSections
            else -> ipeSections
        }
        return list.minByOrNull { kotlin.math.abs(it.depth - depth) }
    }

    /**
     * Search RHS sections by dimensions (height × width × thickness).
     * Returns the closest match if no exact match is found.
     */
    fun getRhsByDimensions(height: Double, width: Double, thickness: Double): SectionProperties? {
        return rhsSections.minByOrNull {
            kotlin.math.abs(it.depth - height) + kotlin.math.abs(it.width - width) + kotlin.math.abs(it.tw - thickness) * 10
        }
    }
}