package com.civileg.app.ui.retaining

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.civileg.app.databinding.FragmentRetainingWallBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.viewmodel.RetainingWallViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RetainingWallFragment : Fragment() {
    
    private var _binding: FragmentRetainingWallBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: RetainingWallViewModel by viewModels()
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRetainingWallBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
            Toast.makeText(requireContext(), "Design Success", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
