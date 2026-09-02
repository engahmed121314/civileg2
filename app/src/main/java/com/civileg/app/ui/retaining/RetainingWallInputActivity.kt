package com.civileg.app.ui.retaining

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.civileg.app.databinding.ActivityRetainingWallInputBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.RetainingWallViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RetainingWallInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRetainingWallInputBinding
    private val viewModel: RetainingWallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRetainingWallInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCalculate.setOnClickListener {
            viewModel.calculateRetainingWallPro(
                height = 5.0,
                soilDensity = 18.0,
                frictionAngle = 30.0,
                surcharge = 5.0,
                fcu = 25.0,
                fy = 400.0,
                preferredDiameter = 16,
                code = CalculatorEngine.AppDesignCode.EGYPTIAN
            )
        }

        viewModel.result.observe(this) { result ->
            if (result != null) {
                startActivity(RetainingWallResultActivity.newIntent(this, result))
            }
        }
    }
}
