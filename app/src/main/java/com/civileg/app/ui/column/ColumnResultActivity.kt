package com.civileg.app.ui.column

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.civileg.app.R
import com.civileg.app.databinding.ActivityColumnResultBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.PdfGenerator
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class ColumnResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityColumnResultBinding
    private lateinit var result: CalculatorEngine.ColumnResult

    companion object {
        private const val EXTRA_RESULT = "extra_column_result"

        fun newIntent(context: Context, result: CalculatorEngine.ColumnResult): Intent {
            return Intent(context, ColumnResultActivity::class.java).apply {
                putExtra(EXTRA_RESULT, result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityColumnResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val receivedResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT, CalculatorEngine.ColumnResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<CalculatorEngine.ColumnResult>(EXTRA_RESULT)
        }

        if (receivedResult == null) {
            Toast.makeText(this, "Error: Result not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        result = receivedResult

        displayResult()
        setupDrawing()
        setupExportButtons()
    }

    private fun displayResult() {
        binding.tvWidth.text = String.format(Locale.getDefault(), "Width: %.0f mm", result.width)
        binding.tvDepth.text = String.format(Locale.getDefault(), "Depth: %.0f mm", result.depth)
        binding.tvReinforcement.text = "Main Bars: ${result.reinforcement.barString}"
        binding.tvTies.text = "Ties: ${result.stirrups.description}"
        binding.tvConcrete.text = String.format(Locale.getDefault(), "Concrete: %.3f m³", result.concreteVolume)
        binding.tvSteel.text = String.format(Locale.getDefault(), "Steel: %.2f kg", result.steelWeight)
        binding.tvCost.text = String.format(Locale.getDefault(), "Est. Cost: %.2f", result.cost)
        
        binding.tvSlenderness.text = String.format(Locale.getDefault(), "Slenderness (λ): %.2f (%s)", 
            result.slenderness, if(result.isSlender) "Slender" else "Short")

        binding.tvStatusText.text = if (result.isSafe) "SAFE / آمن" else "UNSAFE / غير آمن"
        binding.tvStatusText.setTextColor(if (result.isSafe) 
            ContextCompat.getColor(this, android.R.color.holo_green_dark) 
            else ContextCompat.getColor(this, android.R.color.holo_red_dark))
    }

    private fun setupDrawing() {
        // Use custom DrawingView if available in layout, else Compose
        // For simplicity in this edit, assuming XML ColumnDrawingView exists
        binding.columnDrawingView.setDimensions(
            width = result.width / 1000.0,
            depth = result.depth / 1000.0,
            barDiameter = result.reinforcement.diameter,
            tiesSpacing = result.stirrups.spacing
        )
    }

    private fun setupExportButtons() {
        binding.btnExportPDF.setOnClickListener {
            try {
                val bitmap = captureView(binding.columnDrawingView)
                val inputs = mapOf(
                    "Code" to result.code.displayName,
                    "Width" to "${result.width} mm",
                    "Depth" to "${result.depth} mm",
                    "Height" to String.format("%.2f m", result.slenderness * 0.3 * result.width / 1000.0) // Approx back-calc
                )
                val resultsMap = mapOf(
                    "Reinforcement" to result.reinforcement.barString,
                    "Ties" to result.stirrups.description,
                    "Concrete Vol" to String.format(Locale.getDefault(), "%.3f m3", result.concreteVolume),
                    "Steel Weight" to String.format(Locale.getDefault(), "%.2f kg", result.steelWeight)
                )
                
                val file = PdfGenerator.generateProfessionalReport(
                    this, 
                    "Column Design Report (تقرير تصميم عمود)", 
                    "COLUMN", 
                    inputs, 
                    resultsMap, 
                    result.safetyChecks,
                    result.isSafe,
                    bitmap
                )
                ExportUtils.openPdf(this, file)
            } catch (e: Exception) {
                Toast.makeText(this, "PDF Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun captureView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(100), view.height.coerceAtLeast(100), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}
