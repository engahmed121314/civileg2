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
            val rise = binding.etRiser.text.toString().toDoubleOrNull() ?: 150.0
            val load = binding.etLiveLoad.text.toString().toDoubleOrNull() ?: 3.0
            val fc = binding.etConcreteStrength.text.toString().toDoubleOrNull() ?: 25.0
            val fy = binding.etSteelStrength.text.toString().toDoubleOrNull() ?: 360.0
            val ts = rise

            viewModel.calculateStairPro(
                type = CalculatorEngine.StairType.STRAIGHT,
                span = 3.0,
                riser = rise,
                ts = ts,
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
