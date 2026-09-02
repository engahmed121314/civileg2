package com.civileg.app.ui.column

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.civileg.app.R
import com.civileg.app.databinding.FragmentColumnDesignBinding
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.domain.entities.ConnectedSlabType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.*

@AndroidEntryPoint
class ColumnDesignFragment : Fragment() {
    
    private var _binding: FragmentColumnDesignBinding? = null
    private val binding get() = _binding!!
    
    @Inject
    lateinit var calculatorEngine: CalculatorEngine
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentColumnDesignBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupCalculateButton()
    }

    private fun setupCalculateButton() {
        binding.btnCalculate.setOnClickListener {
            val width = binding.etColumnWidth.text.toString().toDoubleOrNull() ?: 300.0
            val depth = binding.etColumnHeight.text.toString().toDoubleOrNull() ?: 600.0
            val axialLoad = binding.etAxialLoad.text.toString().toDoubleOrNull() ?: 1000.0
            val fc = binding.etConcreteStrength.text.toString().toDoubleOrNull() ?: 25.0
            val fy = binding.etSteelStrength.text.toString().toDoubleOrNull() ?: 400.0

            val result = calculatorEngine.designColumn(
                width = width,
                depth = depth,
                pu = axialLoad,
                mx = 0.0,
                my = 0.0,
                fcu = fc,
                fy = fy,
                code = CalculatorEngine.AppDesignCode.EGYPTIAN,
                connectedSlab = ConnectedSlabType.SOLID
            )
            Toast.makeText(requireContext(), "Design Success", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
