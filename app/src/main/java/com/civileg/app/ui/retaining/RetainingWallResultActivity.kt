package com.civileg.app.ui.retaining

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.civileg.app.databinding.ActivityRetainingWallResultBinding
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.AndroidEntryPoint

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

        val receivedResult = intent.getParcelableExtra<CalculatorEngine.RetainingWallResult>(EXTRA_RESULT)

        if (receivedResult == null) {
            Toast.makeText(this, "Error: Result not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        result = receivedResult
        Toast.makeText(this, "Design Loaded", Toast.LENGTH_SHORT).show()
    }
}
