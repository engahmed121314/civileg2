package com.civileg.app.ui.retaining

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
import com.civileg.app.databinding.ActivityRetainingWallResultBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.PdfGenerator
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class RetainingWallResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRetainingWallResultBinding
    private lateinit var result: CalculatorEngine.RetainingWallResult

    companion object {
        private const val EXTRA_RESULT = "extra_wall_result"

        fun newIntent(context: Context, result: CalculatorEngine.RetainingWallResult): Intent {
            return Intent(context, RetainingWallResultActivity::class.java).apply {
                putExtra(EXTRA_RESULT, result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRetainingWallResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val receivedResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT, CalculatorEngine.RetainingWallResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<CalculatorEngine.RetainingWallResult>(EXTRA_RESULT)
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
        binding.tvStem.text = String.format(Locale.getDefault(), "Stem Thickness: %.0f mm", result.stemThick)
        binding.tvBase.text = String.format(Locale.getDefault(), "Base Width: %.2f m", result.baseWidth)
        binding.tvReinforcement.text = "Main Steel: ${result.stemSteel.barString}"
        binding.tvConcrete.text = String.format(Locale.getDefault(), "Concrete Vol: %.2f m³/m'", result.concreteVolume)
        binding.tvCost.text = String.format(Locale.getDefault(), "Est. Cost: %.2f /m'", result.cost)
        
        // Safety Status
        val statusText = if (result.isSafe) "SAFE / آمن" else "UNSAFE / غير آمن"
        Toast.makeText(this, statusText, Toast.LENGTH_SHORT).show()
    }

    private fun setupDrawing() {
        binding.drawingView.setDetails(
            h = result.height.toFloat(),
            stemT = result.stemThick.toFloat(),
            baseW = result.baseWidth.toFloat(),
            baseT = 500f, // Approx base thickness
            reinf = result.stemSteel.barString
        )
    }

    private fun setupExportButtons() {
        binding.btnExportPDF.setOnClickListener {
            try {
                val bitmap = captureView(binding.drawingView)
                val inputs = mapOf(
                    "Code" to result.code.displayName,
                    "Wall Height" to "${result.height} m"
                )
                val resultsMap = mapOf(
                    "Stem Thickness" to "${result.stemThick} mm",
                    "Base Width" to String.format("%.2f m", result.baseWidth),
                    "Main Reinforcement" to result.stemSteel.barString,
                    "F.O.S Overturning" to String.format("%.2f", result.factorOfSafetyOverturning),
                    "F.O.S Sliding" to String.format("%.2f", result.factorOfSafetySliding),
                    "Concrete Volume" to String.format("%.2f m3/m", result.concreteVolume),
                    "Total Cost" to String.format("%.2f", result.cost)
                )
                
                val file = PdfGenerator.generateProfessionalReport(
                    this, 
                    "Retaining Wall Design Report (تقرير تصميم حائط ساند)", 
                    "WALL", 
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
