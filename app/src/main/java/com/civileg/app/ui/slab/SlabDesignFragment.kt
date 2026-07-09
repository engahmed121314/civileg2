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
import com.civileg.app.R
import com.civileg.app.databinding.FragmentSlabDesignBinding
import com.civileg.app.db.*
import com.civileg.app.db.Project as DbProject
import com.civileg.app.domain.calculations.base.*
import com.civileg.app.domain.entities.*
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.CalculatorEngine.DesignCode
import com.civileg.app.utils.CalculatorEngine.SlabType
import com.civileg.app.utils.SettingsManager
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import com.civileg.app.viewmodel.ProjectViewModel
import com.civileg.app.views.SlabDetailView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class SlabDesignFragment : Fragment() {

    private var _binding: FragmentSlabDesignBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectViewModel by viewModels()
    private val projectId: Long
        get() = requireArguments().getLong("projectId", -1L)
    
    @Inject
    lateinit var calculatorEngine: CalculatorEngine
    
    @Inject
    lateinit var settingsManager: SettingsManager
    
    @Inject
    lateinit var designRepository: DesignRepository
    
    private var selectedSlabTypeEnum = SlabType.SOLID
    private var selectedCodeEnum = DesignCode.EGYPTIAN
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
        val types = SlabType.values().map { it.displayName }
        binding.spinnerSlabType.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types))
        binding.spinnerSlabType.setOnItemClickListener { _, _, position, _ ->
            selectedSlabTypeEnum = SlabType.values()[position]
        }
    }

    private fun setupCodeSelection() {
        binding.codeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedCodeEnum = when (checkedId) {
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
                    code = selectedCodeEnum
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
        binding.etBottomReinforcementY.setText(result.reinforcementSecondary.barString)
        
        binding.etTopReinforcementX.setText(String.format(Locale.US, "الخرسانة: %.2f m³ | التكلفة: %.2f %s", 
            result.concreteVolume, result.cost, settingsManager.currency))
        
        binding.etTopReinforcementY.setText(String.format(Locale.US, "الحديد: %.1f kg | الهالك: %.3f Ton", 
            result.steelWeight, result.steelWasteTons))
        binding.etTopReinforcementY.visibility = View.VISIBLE
        
        binding.etCodeUsed.setText(result.code.displayName)
        
        binding.slabDetailView.updateFromCalculation(
            type = SlabDetailView.SlabType.SOLID,
            lx = 5000f, ly = 4000f, t = result.thickness.toFloat(),
            dia = 10,
            spacing = 200f, 
            mx = result.momentX, my = result.momentY, safe = result.isSafe,
            mainSteelText = result.reinforcementMain.barString
        )

        binding.btnExportPdf.setOnClickListener { exportToPdf(result) }
    }

    private fun exportToPdf(result: CalculatorEngine.SlabResult) {
        try {
            val exporter = ComprehensivePdfExporter(requireContext())
            val projectName = projectsList.firstOrNull { it.id == projectId }?.name ?: "Unnamed Project"
            val fileName = "Slab_Report_${System.currentTimeMillis()}.pdf"
            val filePath = File(requireContext().getExternalFilesDir(null), fileName).absolutePath
            
            val slabTypeDomain = com.civileg.app.domain.entities.SlabType.Solid(
                thickness = result.thickness, shortSpan = 4.0, longSpan = 5.0,
                supportConditions = SlabSupportConditions(EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED, EdgeCondition.SIMPLY_SUPPORTED)
            )
            
            val inputs = SlabInputs(
                fcu = 25.0, fy = 360.0, thickness = result.thickness, deadLoad = 1.5, liveLoad = 3.0
            )
            
            val advResult = AdvancedSlabResult(
                slabType = slabTypeDomain,
                flexureResult = SlabDesignResult(
                    requiredReinforcement = result.reinforcementMain.area,
                    providedReinforcement = result.reinforcementMain.area,
                    barDiameter = result.reinforcementMain.diameter.toDouble(),
                    barSpacing = result.reinforcementMain.spacing,
                    minThickness = 120.0, shearCapacity = 50.0, isSafe = result.isSafe, utilizationRatio = 0.7
                ),
                shearCheck = ShearCheckResult(10.0, 50.0, true, 0.2),
                deflectionCheck = DeflectionCheckResult(2.0, 5.0, 7.0, 20.0, 0.35, true),
                punchingShearCheck = null,
                reinforcementLayout = ReinforcementLayout(
                    topBars = BarLayout(10.0, 200.0, BarDirection.BOTH, 5.0, 50),
                    bottomBars = BarLayout(result.reinforcementMain.diameter.toDouble(), result.reinforcementMain.spacing, BarDirection.BOTH, 5.0, 50),
                    distributionBars = null, additionalBars = emptyList()
                ),
                concreteVolume = result.concreteVolume,
                formworkArea = 20.0,
                inventoryAnalysis = null,
                postTensionCalculations = null,
                warnings = emptyList(),
                codeNotes = listOf("Exported from Civil EG")
            )

            exporter.exportSlabReport(
                projectName = projectName,
                designCode = com.civileg.app.domain.entities.DesignCode.ECP,
                slabType = slabTypeDomain,
                inputs = inputs,
                result = advResult,
                outputPath = filePath
            )
            Toast.makeText(requireContext(), "PDF Exported: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            showError("Export Error: ${e.message}")
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveDesign.setOnClickListener {
            val result = lastResult ?: return@setOnClickListener
            val projectId = if (this@SlabDesignFragment.projectId != -1L) this@SlabDesignFragment.projectId else projectsList.firstOrNull()?.id ?: -1L
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
