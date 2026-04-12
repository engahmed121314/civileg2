package com.civileg.app.ui.watertank

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.civileg.app.R
import com.civileg.app.databinding.ActivityTankResultBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.PdfGenerator
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class TankResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTankResultBinding
    private lateinit var result: CalculatorEngine.TankResult

    companion object {
        private const val EXTRA_RESULT = "extra_tank_result"

        fun newIntent(context: Context, result: CalculatorEngine.TankResult): Intent {
            return Intent(context, TankResultActivity::class.java).apply {
                putExtra(EXTRA_RESULT, result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTankResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val receivedResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT, CalculatorEngine.TankResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<CalculatorEngine.TankResult>(EXTRA_RESULT)
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
        binding.tvCapacity.text = String.format(Locale.getDefault(), "Capacity: %.1f m³", result.capacity)
        binding.tvWallT.text = String.format(Locale.getDefault(), "Wall Thickness: %.0f mm", result.wallThick)
        binding.tvBaseT.text = String.format(Locale.getDefault(), "Base Thickness: %.0f mm", result.baseThick)
        binding.tvReinforcement.text = "Wall Steel: ${result.wallSteel.barString}"
        binding.tvConcrete.text = String.format(Locale.getDefault(), "Concrete Vol: %.2f m³", result.concreteVolume)
        binding.tvSteel.text = String.format(Locale.getDefault(), "Steel Weight: %.1f kg", result.steelWeight)
        binding.tvCost.text = String.format(Locale.getDefault(), "Total Cost: %.2f", result.cost)
        
        binding.tvStatus.text = if (result.isSafe) "SAFE / آمن" else "UNSAFE / غير آمن"
        binding.tvStatus.setTextColor(if (result.isSafe) 
            ContextCompat.getColor(this, android.R.color.holo_green_dark) 
            else ContextCompat.getColor(this, android.R.color.holo_red_dark))
    }

    private fun setupDrawing() {
        binding.tankDrawingView.setDetails(
            l = 5f, // Placeholder length
            h = (result.capacity / (5*3)).toFloat().coerceAtLeast(3f), // Approx height
            wallT = result.wallThick.toFloat(),
            baseT = result.baseThick.toFloat(),
            type = result.type,
            reinf = result.wallSteel.barString
        )
    }

    private fun setupExportButtons() {
        binding.btnExportPDF.setOnClickListener {
            try {
                val bitmap = captureView(binding.tankDrawingView)
                val inputs = mapOf(
                    "Code" to result.code.displayName,
                    "Tank Type" to result.type.name,
                    "Target Capacity" to "${result.capacity} m3"
                )
                val resultsMap = mapOf(
                    "Wall Reinforcement" to result.wallSteel.barString,
                    "Base Reinforcement" to result.baseSteel.barString,
                    "Water Pressure" to String.format("%.2f kN/m2", result.waterPressure),
                    "Max Moment" to String.format("%.2f kN.m", result.mu),
                    "Concrete Vol" to String.format("%.2f m3", result.concreteVolume),
                    "Total Cost" to String.format("%.2f", result.cost)
                )
                
                val file = PdfGenerator.generateProfessionalReport(
                    this, 
                    "Water Tank Design Report (تقرير تصميم خزان مياه)", 
                    "TANK", 
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
