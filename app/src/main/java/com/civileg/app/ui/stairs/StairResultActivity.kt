package com.civileg.app.ui.stairs

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
import com.civileg.app.databinding.ActivityStairResultBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.PdfGenerator
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class StairResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStairResultBinding
    private lateinit var result: CalculatorEngine.StairResult

    companion object {
        private const val EXTRA_RESULT = "extra_stair_result"

        fun newIntent(context: Context, result: CalculatorEngine.StairResult): Intent {
            return Intent(context, StairResultActivity::class.java).apply {
                putExtra(EXTRA_RESULT, result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStairResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val receivedResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT, CalculatorEngine.StairResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<CalculatorEngine.StairResult>(EXTRA_RESULT)
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
        binding.tvThickness.text = String.format(Locale.getDefault(), "Thickness: %.0f mm", result.thickness)
        binding.tvReinforcement.text = "Main Bars: ${result.reinforcement.barString}"
        binding.tvConcrete.text = String.format(Locale.getDefault(), "Concrete: %.3f m³", result.concreteVolume)
        binding.tvSteel.text = String.format(Locale.getDefault(), "Steel: %.2f kg", result.steelWeight)
        binding.tvCost.text = String.format(Locale.getDefault(), "Est. Cost: %.2f", result.cost)
        
        binding.tvStatus.text = if (result.isSafe) "SAFE / آمن" else "UNSAFE / غير آمن"
        binding.tvStatus.setTextColor(if (result.isSafe) 
            ContextCompat.getColor(this, android.R.color.holo_green_dark) 
            else ContextCompat.getColor(this, android.R.color.holo_red_dark))
    }

    private fun setupDrawing() {
        binding.drawingView.setDetails(
            riser = 150f,
            tread = 300f,
            reinforcement = result.reinforcement.barString,
            volume = result.concreteVolume.toFloat(),
            safe = result.isSafe
        )
    }

    private fun setupExportButtons() {
        binding.btnExportPDF.setOnClickListener {
            try {
                val bitmap = captureView(binding.drawingView)
                val inputs = mapOf(
                    "Code" to result.code.displayName,
                    "Thickness" to "${result.thickness} mm"
                )
                val resultsMap = mapOf(
                    "Reinforcement" to result.reinforcement.barString,
                    "Distribution" to result.distributionReinforcement.barString,
                    "Concrete Vol" to String.format(Locale.getDefault(), "%.3f m3", result.concreteVolume),
                    "Steel Weight" to String.format(Locale.getDefault(), "%.2f kg", result.steelWeight)
                )
                
                val file = PdfGenerator.generateProfessionalReport(
                    this, 
                    "Staircase Design Report (تقرير تصميم سلم)", 
                    "STAIR", 
                    inputs, 
                    resultsMap, 
                    result.safetyChecks,
                    result.isSafe,
                    bitmap
                )
                ExportUtils.openPdf(this, file)
            } catch (e: Exception) {
                Toast.makeText(this, "Export Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
