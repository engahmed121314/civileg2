package com.civileg.app.ui.stairs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.civileg.app.databinding.FragmentStaircaseDesignBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.StairViewModel
import com.civileg.app.domain.calculations.base.StaircaseInput
import com.civileg.app.domain.calculations.base.DomainStairType
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class StaircaseDesignFragment : Fragment() {

    private var _binding: FragmentStaircaseDesignBinding? = null
    private val binding get() = _binding!!
    private val viewModel: StairViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStaircaseDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnCalculate.setOnClickListener {
            val span = binding.etSpan.text.toString().toDoubleOrNull() ?: 3.0
            val rise = binding.etRise.text.toString().toDoubleOrNull() ?: 1.5
            val load = binding.etLoad.text.toString().toDoubleOrNull() ?: 5.0
            val fc = binding.etFc.text.toString().toDoubleOrNull() ?: 25.0
            val fy = binding.etFy.text.toString().toDoubleOrNull() ?: 400.0
            val ts = binding.etThickness.text.toString().toDoubleOrNull() ?: 150.0

            viewModel.calculateStairPro(
                type = CalculatorEngine.StairType.STRAIGHT,
                span = span,
                riser = 150.0,
                tread = 300.0,
                deadLoad = load * 0.8,
                liveLoad = load * 0.2,
                fcu = fc,
                fy = fy,
                preferredDiameter = 12,
                code = CalculatorEngine.AppDesignCode.EGYPTIAN
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
