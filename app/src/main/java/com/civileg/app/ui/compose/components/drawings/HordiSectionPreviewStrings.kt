package com.civileg.app.ui.compose.components.drawings

import com.civileg.app.domain.drawing.DrawingScene
import com.civileg.app.domain.drawing.Primitive

/** Presentation translations only. The frozen English handoff and report scene stay unchanged. */
internal object HordiSectionPreviewStrings {
    private val arabicWarnings = mapOf(
        "Section forces, actual bar layout, flexure and web shear assessed. Whole-member serviceability approval, durability, topping reinforcement, cross-rib provision and anchorage are incomplete; execution exports remain blocked." to
            "التقييم الحالي يقتصر على قوى القطاع وترتيب التسليح والانحناء وقص العصب. اعتماد صلاحية الخدمة للعنصر بالكامل، ومتطلبات المتانة، وتسليح الطبقة العلوية، وتوفير الأعصاب العرضية، والتثبيت غير مكتمل؛ يظل التصدير التنفيذي محجوبًا.",
        "One entered rib-pitch cell only; full-panel width and edge conditions are not supplied." to
            "خلية واحدة بعرض تكرار العصب المدخل فقط؛ عرض اللوحة بالكامل وشروط الحواف غير متاحة.",
        "Longitudinal bar paths, lengths, support anchorage and curtailment are not supplied." to
            "مسارات الأسياخ الطولية وأطوالها، وتثبيتها عند المساند، ومواضع قطعها غير متاحة.",
        "Link closure, bends, hooks and first longitudinal offset are not supplied." to
            "غلق الكانات وثنياتها وخطافاتها والإزاحة الطولية لأول كانة غير متاحة.",
        "Topping/distribution reinforcement and actual cross-rib provision are not supplied." to
            "تسليح الطبقة العلوية والتوزيع، وتوفير الأعصاب العرضية الفعلي غير متاح.",
        "Adopted calculated section arrangement, not as-built or user-entered horizontal coordinates." to
            "ترتيب القطاع معتمد للحساب فقط، وليس توثيقًا للمنفذ أو إحداثيات أفقية أدخلها المستخدم."
    )

    fun hasWarningTranslation(source: String): Boolean = source in arabicWarnings

    fun warning(source: String, isArabic: Boolean): String = if (!isArabic) source else
        arabicWarnings[source] ?: "قيد أصلي لم تتوفر ترجمته بعد، ولا يجوز تجاهله: $source"

    fun methodReference(source: String, isArabic: Boolean): String = if (!isArabic) source else
        source.replace("bounded rib section", "قطاع عصب محدود النطاق")

    fun policyNote(source: String, isArabic: Boolean): String {
        if (!isArabic) return source
        val policy = source.substringAfter("Four-bar arrangement policy: ", "").substringBefore(';')
        val rows = source.substringAfter("actual adopted rows=", "").substringBefore(". ")
        val description = when (policy) {
            "STANDARD_PAIRED_UPPER" -> "الوضع المعتاد بإضافة السيخ العلوي المقابل عند الحاجة"
            "EXPLICIT_ECONOMY_FOUR_BARS" -> "وضع اقتصادي بطلب صريح مع الحفاظ على الأربعة أسياخ"
            else -> null
        }
        return if (description != null && rows.matches(Regex("[0-9]+(?:\\+[0-9]+)*")) &&
            source.endsWith("Project detailing choice, not a code-mandated bar-count parity rule. All execution holds remain."))
            "سياسة ترتيب التسليح: $description. الصفوف المعتمدة للحساب: $rows. هذا اختيار تفصيلي للمشروع، وليس قاعدة يفرضها الكود لزوجية أو فردية عدد الأسياخ. جميع قيود التنفيذ ما زالت قائمة."
        else "ملاحظة أصلية لم تتوفر ترجمتها بعد، ولا يجوز تجاهلها: $source"
    }

    fun scene(source: DrawingScene, isArabic: Boolean): DrawingScene {
        if (!isArabic) return source
        return source.copy(title = "قطاع عصب محسوب بعرض تكرار واحد، وليس توثيقًا للمنفذ", primitives = source.primitives.map {
            when (it) {
                is Primitive.Text -> it.copy(text = caption(it.text))
                is Primitive.Dimension -> it.copy(text = caption(it.text))
                else -> it
            }
        })
    }

    private fun caption(source: String): String = when {
        source == "Repeating-cell cuts, not free edges." -> "حدود خلية تكرار، وليست حوافًا حرة."
        source.matches(Regex("Tension (BOTTOM|TOP); [0-9]+ adopted bars\\.")) -> {
            val count = source.substringAfter("; ").substringBefore(' ')
            if (source.startsWith("Tension TOP;")) "عدد أسياخ الشد العلوي: $count" else "عدد أسياخ الشد السفلي: $count"
        }
        source.startsWith("Analytical b_eff = ") -> source.removePrefix("Analytical ").replace(" mm", " مم")
        source.startsWith("t = ") || source.startsWith("h ") -> source.replace(" mm", " مم")
        source.startsWith("Pitch ") -> "عرض التكرار " + source.removePrefix("Pitch ").replace(" mm", " مم")
        source.startsWith("Web ") -> "عرض العصب " + source.removePrefix("Web ").replace(" mm", " مم")
        else -> "نص أصلي لم تكتمل ترجمته: $source"
    }
}
