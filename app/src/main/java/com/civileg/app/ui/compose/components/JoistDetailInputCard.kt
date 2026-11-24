package com.civileg.app.ui.compose.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.calculations.utils.JoistDetailCase
import com.civileg.app.domain.calculations.utils.JoistDetailDraft
import com.civileg.app.ui.components.AppText

/** Explicit inputs for the bounded joist review. No preselected assumptions. */
@Composable
fun JoistDetailInputCard(draft: JoistDetailDraft, onChange: (JoistDetailDraft) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppText("مدخلات مراجعة العصب / Joist review inputs")
            AppText("هذه مراجعة جزئية وليست لوحة تنفيذ. لا تفترض قيمًا غير معروفة. / Partial review, not construction detailing. Leave unknown values empty.")
            val fields = listOf(
                "ribCount" to "عدد الأعصاب الفعلي / Actual rib count",
                "flangeWidth" to "عرض الشفة الفعال mm / Effective flange width",
                "leftWidth" to "عرض المسند الأيسر mm / Left support width",
                "rightWidth" to "عرض المسند الأيمن mm / Right support width",
                "leftMoment" to "عزم سالب أيسر kN.m/rib / Left negative moment magnitude",
                "rightMoment" to "عزم سالب أيمن kN.m/rib / Right negative moment magnitude",
                "leftAnchorage" to "طول رباط متاح أيسر mm / Left available anchorage",
                "rightAnchorage" to "طول رباط متاح أيمن mm / Right available anchorage",
                "deadLoad" to "حمل ميت kN/m2 / Dead load",
                "liveLoad" to "حمل حي kN/m2 / Live load",
                "serviceMoment" to "عزم خدمة موجب kN.m/rib / Service positive moment",
                "sustainedFraction" to "نسبة الحمل المستمر 0-1 / Sustained fraction",
                "duration" to "مدة التحميل بالشهور / Duration months",
                "partitionTime" to "تركيب القواطيع بالشهور عند اللزوم / Partition time if applicable",
                "linkDiameter" to "قطر الكانة mm، صفر دون كانة / Link diameter, zero if none",
                "aggregate" to "أقصى مقاس ركام mm / Aggregate size",
                "mainDiameter" to "قطر السيخ الرئيسي mm / Main bar diameter",
                "barCount" to "عدد الأسياخ بالعصب / Bars per rib",
                "stockLength" to "طول السيخ التجاري mm / Stock length",
                "leftSolid" to "منطقة مصمتة يسرى mm / Left solid zone",
                "rightSolid" to "منطقة مصمتة يمنى mm / Right solid zone"
            )
            fields.forEach { (key, label) ->
                PremiumInputField(draft.values[key].orEmpty(), label,
                    { onChange(draft.copy(values = draft.values + (key to it))) }, modifier = Modifier.fillMaxWidth())
            }
            ReviewChoice("المسند الأيسر / Left restraint", draft.leftRestraint,
                JoistDetailCase.Restraint.entries) { onChange(draft.copy(leftRestraint = it)) }
            ReviewChoice("المسند الأيمن / Right restraint", draft.rightRestraint,
                JoistDetailCase.Restraint.entries) { onChange(draft.copy(rightRestraint = it)) }
            ReviewChoice("مادة المسند الأيسر / Left material", draft.leftMaterial,
                JoistDetailCase.SupportMaterial.entries) { onChange(draft.copy(leftMaterial = it)) }
            ReviewChoice("مادة المسند الأيمن / Right material", draft.rightMaterial,
                JoistDetailCase.SupportMaterial.entries) { onChange(draft.copy(rightMaterial = it)) }
            ReviewChoice("بيئة التعرض / Exposure", draft.exposure,
                JoistDetailCase.Exposure.entries) { onChange(draft.copy(exposure = it)) }
            ReviewChoice("أحمال موزعة بانتظام؟ / Uniform loading?", draft.uniformlyDistributed,
                listOf(true, false)) { onChange(draft.copy(uniformlyDistributed = it)) }
            ReviewChoice("تفاصيل زلزالية؟ / Seismic detailing?", draft.seismicRequired,
                listOf(true, false)) { onChange(draft.copy(seismicRequired = it)) }
            ReviewChoice("قواطيع حساسة للترخيم؟ / Sensitive partitions?", draft.sensitivePartitions,
                listOf(true, false)) { onChange(draft.copy(sensitivePartitions = it)) }
        }
    }
}

@Composable
private fun <T> ReviewChoice(label: String, selected: T?, options: List<T>, onChange: (T) -> Unit) {
    Column {
        AppText(label)
        options.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onChange(option) },
                label = { AppText(when (option) { true -> "نعم / Yes"; false -> "لا / No"; else -> option.toString() }) })
        }
    }
}
