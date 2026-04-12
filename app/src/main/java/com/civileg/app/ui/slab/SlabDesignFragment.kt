package com.civileg.app.ui.slab

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.civileg.app.R
import com.civileg.app.databinding.FragmentSlabDesignBinding
import com.civileg.app.db.*
import com.civileg.app.db.Project as DbProject
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.CalculatorEngine.DesignCode
import com.civileg.app.utils.CalculatorEngine.SlabType
import com.civileg.app.utils.SettingsManager
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.views.SlabDetailView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class SlabDesignFragment : Fragment() {

    private var _binding: FragmentSlabDesignBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectViewModel by viewModels()
    private val args: SlabDesignFragmentArgs by navArgs()
    
    @Inject
    lateinit var calculatorEngine: CalculatorEngine
    
    @Inject
    lateinit var settingsManager: SettingsManager
    
    @Inject
    lateinit var designRepository: DesignRepository
    
    private var selectedSlabType = SlabType.SOLID
    private var selectedCode = DesignCode.EGYPTIAN
    private var lastResult: CalculatorEngine.SlabResult? = null
    private var projectsList: List<DbProject> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSlabDesignBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.allProjects.observe(viewLifecycleOwner) { projects -> projectsList = projects }
        setupSlabTypeSpinner()
        setupCodeSelection()
        setupCalculateButton()
        setupSaveButton()
    }

    private fun setupSlabTypeSpinner() {
        val types = SlabType.values().map { it.name }
        binding.spinnerSlabType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types))
        binding.spinnerSlabType.setOnItemClickListener { _, _, position, _ ->
            selectedSlabType = SlabType.values()[position]
        }
    }

    private fun setupCodeSelection() {
        binding.codeToggleGroup?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedCode = when (checkedId) {
                    R.id.btnAmericanCode -> DesignCode.ACI
                    R.id.btnSaudiCode -> DesignCode.SAUDI
                    else -> DesignCode.EGYPTIAN
                }
            }
        }
    }

    private fun setupCalculateButton() {
        binding.btnCalculate.setOnClickListener {
            try {
                val lx = binding.etSlabLengthX.text.toString().toDoubleOrNull() ?: 5.0
                val ly = binding.etSlabLengthY.text.toString().toDoubleOrNull() ?: 4.0
                val ts = binding.etSlabThickness.text.toString().toDoubleOrNull() ?: 150.0
                val dl = binding.etDeadLoad.text.toString().toDoubleOrNull() ?: 0.0
                val ll = binding.etLiveLoad.text.toString().toDoubleOrNull() ?: 0.0

                val result = calculatorEngine.designSlab(
                    lx = lx,
                    ly = ly,
                    deadLoad = dl,
                    liveLoad = ll,
                    fcu = 25.0,
                    fy = 360.0,
                    ts = ts,
                    preferredDiameter = 12,
                    code = selectedCode
                )
                lastResult = result
                showResults(result)
                Toast.makeText(requireContext(), "تم حساب النتائج بدقة", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError(e.message ?: "خطأ في المدخلات")
            }
        }
    }

    private fun showResults(result: CalculatorEngine.SlabResult) {
        binding.resultsCard.visibility = View.VISIBLE
        binding.etBottomReinforcementX.setText(result.reinforcementMain.barString)
        
        binding.etTopReinforcementX.setText(String.format(Locale.US, "الخرسانة: %.2f m³ | التكلفة: %.2f %s", 
            result.concreteVolume, result.cost, settingsManager.currency))
        
        binding.etTopReinforcementY.setText(String.format(Locale.US, "الحديد: %.1f kg", result.steelWeight))
        binding.etTopReinforcementY.visibility = View.VISIBLE
        
        binding.slabDetailView.updateFromCalculation(
            type = SlabDetailView.SlabType.SOLID,
            lx = 5000f, ly = 4000f, t = result.thickness.toFloat(),
            dia = 10,
            spacing = 200f, 
            mx = result.momentX, my = result.momentY, safe = result.isSafe,
            mainSteelText = result.reinforcementMain.barString
        )
    }

    private fun setupSaveButton() {
        binding.btnSaveDesign.setOnClickListener {
            val result = lastResult ?: return@setOnClickListener
            val projectId = if (args.projectId != -1L) args.projectId else projectsList.firstOrNull()?.id ?: -1L
            if (projectId == -1L) { showError("اختر مشروعاً للحفظ"); return@setOnClickListener }

            lifecycleScope.launch {
                val design = Design(
                    projectId = projectId, type = DesignType.SLAB,
                    name = "Slab - ${System.currentTimeMillis() % 1000}",
                    inputData = "{}",
                    results = result.toString(),
                    isSafe = result.isSafe, codeUsed = result.code.displayName,
                    concreteVolume = result.concreteVolume, steelWeight = result.steelWeight,
                    totalCost = result.cost
                )
                viewModel.saveDesign(design)
                Toast.makeText(context, "تم حفظ التقرير الهندسي بنجاح", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showError(msg: String) {
        MaterialAlertDialogBuilder(requireContext()).setTitle("تنبيه").setMessage(msg).setPositiveButton("موافق", null).show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
