package com.civileg.app.ui.slab

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
import com.civileg.app.databinding.ActivitySlabResultBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.PdfGenerator
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class SlabResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySlabResultBinding
    private lateinit var result: CalculatorEngine.SlabResult

    companion object {
        private const val EXTRA_RESULT = "extra_slab_result"

        fun newIntent(context: Context, result: CalculatorEngine.SlabResult): Intent {
            return Intent(context, SlabResultActivity::class.java).apply {
                putExtra(EXTRA_RESULT, result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySlabResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val receivedResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT, CalculatorEngine.SlabResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<CalculatorEngine.SlabResult>(EXTRA_RESULT)
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
        binding.tvReinforcement.text = "Main Reinforcement: ${result.reinforcementMain.barString}"
        binding.tvConcrete.text = String.format(Locale.getDefault(), "Concrete: %.3f m³", result.concreteVolume)
        binding.tvSteel.text = String.format(Locale.getDefault(), "Steel: %.2f kg", result.steelWeight)
        binding.tvCost.text = String.format(Locale.getDefault(), "Est. Cost: %.2f", result.cost)
        
        // Status display
        binding.tvStatus.text = if (result.isSafe) "SAFE / آمن" else "UNSAFE / غير آمن"
        binding.tvStatus.setTextColor(if (result.isSafe) 
            ContextCompat.getColor(this, android.R.color.holo_green_dark) 
            else ContextCompat.getColor(this, android.R.color.holo_red_dark))
    }

    private fun setupDrawing() {
        binding.drawingView.setDetails(
            lx = 5f, 
            ly = 4f,
            t = result.thickness.toFloat(),
            dia = result.reinforcementMain.diameter,
            s = result.reinforcementMain.spacing.toFloat()
        )
    }

    private fun setupExportButtons() {
        binding.btnExportPDF.setOnClickListener {
            try {
                val bitmap = captureView(binding.drawingView)
                val inputs = mapOf(
                    "Design Code" to result.code.displayName,
                    "Thickness" to "${result.thickness} mm"
                )
                val resultsMap = mapOf(
                    "Main Steel" to result.reinforcementMain.barString,
                    "Secondary Steel" to result.reinforcementSecondary.barString,
                    "Punching Check" to if(result.punchingSafe) "Passed" else "Failed",
                    "Concrete Vol" to String.format(Locale.getDefault(), "%.3f m3", result.concreteVolume),
                    "Total Cost" to String.format(Locale.getDefault(), "%.2f", result.cost)
                )
                
                val file = PdfGenerator.generateProfessionalReport(
                    this, 
                    "Slab Design Report (تقرير تصميم بلاطة)", 
                    "SLAB", 
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
