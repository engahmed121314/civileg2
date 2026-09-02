package com.civileg.app.ui.seismic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.civileg.app.databinding.FragmentSeismicAnalysisBinding
import com.civileg.app.utils.CalculatorEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SeismicAnalysisFragment : Fragment() {
    
    private var _binding: FragmentSeismicAnalysisBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var calculatorEngine: CalculatorEngine
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSeismicAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnCalculate.setOnClickListener {
            calculatorEngine.calculateSeismicLoads(
                code = CalculatorEngine.AppDesignCode.EGYPTIAN,
                height = 30.0,
                numStories = 10,
                totalWeight = 10000.0,
                zone = "1",
                importance = 1.0,
                responseReduction = 5.0
            )
            Toast.makeText(requireContext(), "Analysis Success", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
