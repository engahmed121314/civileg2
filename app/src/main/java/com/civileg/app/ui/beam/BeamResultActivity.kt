package com.civileg.app.ui.beam

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
import com.civileg.app.databinding.ActivityBeamResultBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.PdfGenerator
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class BeamResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBeamResultBinding
    private lateinit var result: CalculatorEngine.BeamResult

    companion object {
        private const val EXTRA_RESULT = "extra_beam_result"

        fun newIntent(context: Context, result: CalculatorEngine.BeamResult): Intent {
            return Intent(context, BeamResultActivity::class.java).apply {
                putExtra(EXTRA_RESULT, result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeamResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val receivedResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT, CalculatorEngine.BeamResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<CalculatorEngine.BeamResult>(EXTRA_RESULT)
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
        binding.tvDimensions.text = String.format(Locale.getDefault(), "Dimensions: %.0f x %.0f mm", result.width, result.depth)
        binding.tvReinforcement.text = "Main Bars: ${result.reinforcementBottom.barString}"
        binding.tvStirrups.text = "Stirrups: ${result.stirrups.description}"
        binding.tvConcrete.text = String.format(Locale.getDefault(), "Concrete: %.3f m³", result.concreteVolume)
        binding.tvSteel.text = String.format(Locale.getDefault(), "Steel: %.2f kg", result.steelWeight)
        binding.tvCost.text = String.format(Locale.getDefault(), "Est. Cost: %.2f", result.cost)
        
        binding.tvStatus.text = if (result.isSafe) "SAFE / آمن" else "UNSAFE / غير آمن"
        binding.tvStatus.setTextColor(if (result.isSafe) 
            ContextCompat.getColor(this, android.R.color.holo_green_dark) 
            else ContextCompat.getColor(this, android.R.color.holo_red_dark))
    }

    private fun setupDrawing() {
        binding.beamDrawingView.post {
            binding.beamDrawingView.setDetails(
                w = result.width.toFloat(),
                d = result.depth.toFloat(),
                bottom = result.reinforcementBottom.numBars,
                top = result.reinforcementTop.numBars,
                dia = result.reinforcementBottom.diameter
            )
        }
    }

    private fun setupExportButtons() {
        binding.btnExportPDF.setOnClickListener {
            try {
                val bitmap = captureView(binding.beamDrawingView)
                val inputs = mapOf(
                    "Code" to result.code.displayName,
                    "Width" to "${result.width} mm",
                    "Depth" to "${result.depth} mm"
                )
                val resultsMap = mapOf(
                    "Bottom Steel" to result.reinforcementBottom.barString,
                    "Top Steel" to result.reinforcementTop.barString,
                    "Stirrups" to result.stirrups.description,
                    "Concrete Vol" to String.format(Locale.getDefault(), "%.3f m3", result.concreteVolume),
                    "Steel Weight" to String.format(Locale.getDefault(), "%.2f kg", result.steelWeight)
                )
                
                val file = PdfGenerator.generateProfessionalReport(
                    this, 
                    "Beam Design Report (تقرير تصميم كمرة)", 
                    "BEAM", 
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
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
}
