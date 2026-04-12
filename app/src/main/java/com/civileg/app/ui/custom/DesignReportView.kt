package com.civileg.app.ui.custom

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.civileg.app.databinding.ViewDesignReportBinding
import com.civileg.app.utils.CalculatorEngine
import java.util.Locale

/**
 * DesignReportView: واجهة عرض تفاعلية لعرض نتائج التصميم بشكل احترافي.
 * تعرض الحصر، التكلفة، الرسومات التوضيحية، والمعادلات.
 */
class DesignReportView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding: ViewDesignReportBinding = ViewDesignReportBinding.inflate(
        LayoutInflater.from(context), this, true
    )

    fun setReport(report: CalculatorEngine.DesignReport) {
        binding.apply {
            tvTitle.text = report.elementTitle
            tvDimensions.text = "الأبعاد: ${report.dimensions}"
            
            // عرض الحصر
            tvConcrete.text = String.format(Locale.US, "%.2f m³", report.boq.concreteM3)
            tvSteel.text = String.format(Locale.US, "%.1f kg", report.boq.steelKg)
            tvCost.text = String.format(Locale.US, "%.2f %s", report.boq.totalCost, report.boq.currency)
            
            // عرض تفاصيل التسليح
            layoutSteelDetails.removeAllViews()
            report.reinforcement.forEach { steel ->
                val itemView = android.widget.TextView(context).apply {
                    text = "• ${steel.type}: ${steel.description} (الوزن: ${String.format("%.1f", steel.weightKg)} كجم)"
                    setTextColor(Color.DKGRAY)
                    setPadding(0, 8, 0, 8)
                }
                layoutSteelDetails.addView(itemView)
            }
            
            // عرض الأمان
            val isAllSafe = report.safetyChecks.all { it.isSafe }
            tvSafetyStatus.text = if (isAllSafe) "آمن إنشائياً ✔" else "غير آمن ✖"
            tvSafetyStatus.setTextColor(if (isAllSafe) Color.parseColor("#2E7D32") else Color.RED)
        }
    }
}
