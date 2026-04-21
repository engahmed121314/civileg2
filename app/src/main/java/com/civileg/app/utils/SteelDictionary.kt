package com.civileg.app.utils

import com.civileg.app.domain.entities.SteelGrade

/**
 * قاموس المنشآت المعدنية الشامل
 * يحتوي على تعريفات، جداول، ومعادلات التصميم للمنشآت المعدنية
 */
object SteelDictionary {

    /**
     * تعريفات المصطلحات الأساسية
     */
    val glossary = mapOf(
        "Modulus of Elasticity (E)" to "Standard value is 210,000 MPa (or 2100 t/cm²).",
        "Yield Strength (Fy)" to "The stress at which a material begins to deform plastically.",
        "Slenderness Ratio (λ)" to "The ratio of the effective length of a member to its least radius of gyration.",
        "Compact Section" to "Section that can develop its full plastic moment without local buckling.",
        "Lateral Torsional Buckling" to "Buckling of a beam due to the compression flange becoming unstable laterally.",
        "Stiffener" to "A plate welded to the web or flange to prevent local buckling or reinforce connection."
    )

    /**
     * معاملات الأمان وخصائص المواد حسب النوع
     */
    fun getSteelProperties(grade: SteelGrade) = mapOf(
        "Fy" to "${grade.fy} MPa",
        "Fu" to "${grade.fu} MPa",
        "Density" to "7850 kg/m³",
        "Code Reference" to grade.codeReference
    )

    /**
     * معادلات تصميمية سريعة
     */
    fun getDesignFormulas(code: String): List<String> {
        return when(code) {
            "ECP" -> listOf(
                "Allowable Axial Stress (F_c): Depends on λ (Slenderness).",
                "Allowable Bending Stress (F_bc): 0.64 Fy for compact sections.",
                "Shear Stress (q): 0.35 Fy."
            )
            "AISC" -> listOf(
                "Available Tensile Strength: φPn = φ Fy Ag (LRFD).",
                "Nominal Flexural Strength: Mn = Fy Zx.",
                "Shear Strength: Vn = 0.6 Fy Aw Cv."
            )
            else -> emptyList()
        }
    }

    /**
     * مكتبة القطاعات المركبة (Built-up Sections)
     */
    fun getBuiltUpDescription(): String {
        return """
            القطاعات المركبة (Built-up Sections):
            تستخدم عندما لا تكفي القطاعات المدرفلة (Hot-rolled) للأحمال الكبيرة.
            - يتم تصميمها باستخدام ألواح (Plates) للحصول على Web و Flanges بأبعاد مخصصة.
            - يجب التحقق من اللحام الطولي (Longitudinal Weld) بين العصب والشفة.
        """.trimIndent()
    }

    /**
     * أنواع الوصلات الشائعة
     */
    val connectionTypes = listOf(
        "Fin Plate Connection (Shear only)",
        "End Plate Connection (Moment/Shear)",
        "Splice Connection",
        "Base Plate with Anchor Bolts",
        "Gusset Plate for Bracing"
    )
}
