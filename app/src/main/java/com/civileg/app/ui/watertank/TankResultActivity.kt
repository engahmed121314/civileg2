package com.civileg.app.ui.watertank

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.civileg.app.databinding.ActivityTankResultBinding
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class TankResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTankResultBinding
    private lateinit var result: CalculatorEngine.TankResult

    companion object {
        private const val EXTRA_TANK_RESULT = "extra_tank_result"

        fun newIntent(context: Context, result: CalculatorEngine.TankResult): Intent {
            return Intent(context, TankResultActivity::class.java).apply {
                putExtra(EXTRA_TANK_RESULT, result)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTankResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val receivedResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TANK_RESULT, CalculatorEngine.TankResult::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<CalculatorEngine.TankResult>(EXTRA_TANK_RESULT)
        }

        if (receivedResult == null) {
            Toast.makeText(this, "Error: Result not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        result = receivedResult

        displayResults()
    }

    private fun displayResults() {
        binding.apply {
            tvStatus.text = result.safetyCheck
            tvCapacity.text = String.format(Locale.getDefault(), "Capacity: %.1f m³", result.capacityM3)
            tvWallT.text = String.format(Locale.getDefault(), "Wall: %.0f mm", result.wallThickness)
            tvBaseT.text = String.format(Locale.getDefault(), "Base: %.0f mm", result.baseThickness)
            tvReinforcement.text = result.wallReinforcement.barString
            tvConcrete.text = String.format(Locale.getDefault(), "Concrete: %.2f m³", result.concreteVolume)
            tvCost.text = String.format(Locale.getDefault(), "Cost: %.2f", result.cost)
            
            tankDrawingView.setDetails(
                l = result.length.toFloat(),
                h = result.height.toFloat(),
                wallT = result.wallThickness.toFloat(),
                baseT = result.baseThickness.toFloat(),
                type = result.type,
                reinf = result.wallReinforcement.barString
            )
        }
    }
}
