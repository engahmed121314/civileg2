package com.civileg.app.ui.footing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.civileg.app.databinding.FragmentFootingDesignBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.FootingViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FootingDesignFragment : Fragment() {
    
    private var _binding: FragmentFootingDesignBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: FootingViewModel by viewModels()
    
    @Inject
    lateinit var calculatorEngine: CalculatorEngine
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFootingDesignBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnCalculate.setOnClickListener {
            viewModel.calculateFooting(
                type = CalculatorEngine.FootingType.ISOLATED,
                p = 1000.0,
                fcu = 25.0,
                fy = 400.0,
                soil = 200.0,
                colB = 400.0,
                colT = 400.0,
                code = CalculatorEngine.AppDesignCode.EGYPTIAN,
                preferredDiameter = 16,
                preferredSpacing = 200.0
            )
            Toast.makeText(requireContext(), "Design Success", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
