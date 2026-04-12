package com.civileg.app.ui.slab

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.civileg.app.R
import com.civileg.app.databinding.ActivitySlabInputBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.SlabInputData
import com.civileg.app.viewmodel.SlabViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SlabInputActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySlabInputBinding
    private val viewModel: SlabViewModel by viewModels()
    private var projectId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySlabInputBinding.inflate(layoutInflater)
        setContentView(binding.root)

        projectId = intent.getLongExtra("project_id", -1L)
        if (projectId == -1L) {
            Toast.makeText(this, R.string.error_no_project, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupObservers()
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnCalculate.setOnClickListener {
            val spanX = binding.etSpanX.text.toString().toDoubleOrNull()
            val spanY = binding.etSpanY.text.toString().toDoubleOrNull()
            val liveLoad = binding.etLiveLoad.text.toString().toDoubleOrNull()
            val deadLoad = binding.etDeadLoad.text.toString().toDoubleOrNull()
            val fcu = binding.etFcu.text.toString().toDoubleOrNull()
            val fy = binding.etFy.text.toString().toDoubleOrNull()

            if (spanX == null || spanY == null || liveLoad == null || deadLoad == null || fcu == null || fy == null) {
                Toast.makeText(this, R.string.enter_all_values, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.calculateSlab(spanX, spanY, liveLoad + deadLoad, fcu, fy)
        }
    }

    private fun setupObservers() {
        viewModel.result.observe(this) { result ->
            if (result != null) {
                viewModel.saveSlab(projectId, getInputData(), result)
                startActivity(SlabResultActivity.newIntent(this, result))
            }
        }

        viewModel.error.observe(this) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun getInputData(): SlabInputData {
        return SlabInputData(
            spanX = binding.etSpanX.text.toString().toDoubleOrNull() ?: 0.0,
            spanY = binding.etSpanY.text.toString().toDoubleOrNull() ?: 0.0,
            load = (binding.etLiveLoad.text.toString().toDoubleOrNull() ?: 0.0) + (binding.etDeadLoad.text.toString().toDoubleOrNull() ?: 0.0),
            fcu = binding.etFcu.text.toString().toDoubleOrNull() ?: 0.0,
            fy = binding.etFy.text.toString().toDoubleOrNull() ?: 0.0,
            type = CalculatorEngine.SlabType.SOLID
        )
    }
}
