package com.civileg.app.ui.column

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.civileg.app.R
import com.civileg.app.databinding.FragmentColumnDesignBinding
import com.civileg.app.db.*
import com.civileg.app.db.Project as DbProject
import com.civileg.app.domain.entities.*
import com.civileg.app.domain.entities.DesignCode as DomainDesignCode
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.CalculatorEngine.DesignCode
import com.civileg.app.utils.SettingsManager
import com.civileg.app.utils.exporters.ComprehensivePdfExporter
import com.civileg.app.views.ColumnSectionView
import com.civileg.app.viewmodel.ProjectViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import javax.inject.Inject
import kotlin.math.*

@AndroidEntryPoint
class ColumnDesignFragment : Fragment() {
    
    private var _binding: FragmentColumnDesignBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ProjectViewModel by viewModels()
    private val projectId: Long get() = arguments?.getLong("projectId") ?: 0L
    
    @Inject
    lateinit var calculatorEngine: CalculatorEngine
    
    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var designRepository: DesignRepository
    
    private var selectedCode = DesignCode.EGYPTIAN
    private var colType = ColumnSectionView.ColumnType.RECTANGULAR
    private var lastResult: CalculatorEngine.ColumnResult? = null
    private var projectsList: List<DbProject> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentColumnDesignBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel.allProjects.observe(viewLifecycleOwner) { projects ->
            projectsList = projects
        }
        
        setupTypeToggle()
        setupCodeSelection()
        setupCalculateButton()
        setupDiameterListener()
        updateInitialDrawing()
        setupSaveButton()
        setupTabs()
    }

    private fun setupDiameterListener() {
        binding.spinnerColBar.setOnItemClickListener { _, _, _, _ ->
            if (lastResult != null) {
                calculateColumn()
            }
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        binding.columnSectionView.visibility = View.VISIBLE
                        binding.layoutSafetyChecks.visibility = View.GONE
                    }
                    1 -> {
                        binding.columnSectionView.visibility = View.GONE
                        binding.layoutSafetyChecks.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupTypeToggle() {
        binding.rgColumnType.setOnCheckedChangeListener { _, checkedId ->
            colType = if (checkedId == R.id.rbCircular) {
                binding.layoutColumnWidth.visibility = View.GONE
                binding.layoutColumnHeight.visibility = View.GONE
                binding.layoutColumnDiameter.visibility = View.VISIBLE
                ColumnSectionView.ColumnType.CIRCULAR
            } else {
                binding.layoutColumnWidth.visibility = View.VISIBLE
                binding.layoutColumnHeight.visibility = View.VISIBLE
                binding.layoutColumnDiameter.visibility = View.GONE
                ColumnSectionView.ColumnType.RECTANGULAR
            }
            updateInitialDrawing()
        }
    }
    
    private fun setupCodeSelection() {
        binding.codeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedCode = when (checkedId) {
                    R.id.btnAmericanCode -> DesignCode.ACI
                    R.id.btnSaudiCode -> DesignCode.SAUDI
                    else -> DesignCode.EGYPTIAN
                }
                if (lastResult != null) {
                    calculateColumn()
                }
            }
        }
    }
    
    private fun setupCalculateButton() {
        binding.btnCalculate.setOnClickListener {
            calculateColumn()
        }
    }

    private fun updateInitialDrawing() {
        val width = binding.etColumnWidth.text.toString().toFloatOrNull() ?: 300f
        val height = binding.etColumnHeight.text.toString().toFloatOrNull() ?: 600f
        val diameter = binding.etColumnDiameter.text.toString().toFloatOrNull() ?: 400f
        
        binding.columnSectionView.apply {
            this.columnType = colType
            this.columnWidth = if (colType == ColumnSectionView.ColumnType.CIRCULAR) diameter else width
            this.columnHeight = height
            this.columnDiameter = diameter
            invalidate()
        }
    }
    
    private fun calculateColumn() {
        try {
            val width = binding.etColumnWidth.text.toString().toDoubleOrNull() ?: 300.0
            val depth = binding.etColumnHeight.text.toString().toDoubleOrNull() ?: 600.0
            val diameter = binding.etColumnDiameter.text.toString().toDoubleOrNull() ?: 400.0
            val axialLoad = binding.etAxialLoad.text.toString().toDoubleOrNull() ?: throw Exception("برجاء إدخال الحمل المحوري")
            val fc = binding.etConcreteStrength.text.toString().toDoubleOrNull() ?: 25.0
            val fy = binding.etSteelStrength.text.toString().toDoubleOrNull() ?: 360.0
            val preferredBar = binding.spinnerColBar.text.toString().toIntOrNull() ?: 16
            val clearHeight = (binding.etClearHeight.text.toString().toDoubleOrNull() ?: 3.0) * 1000.0 // to mm
            
            val slabType = when (binding.slabTypeToggle.checkedButtonId) {
                R.id.btnFlatSlab -> "FLAT"
                R.id.btnHordiSlab -> "HORDI"
                else -> "SOLID"
            }
            val hasCap = binding.cbHasCap.isChecked

            val result = calculatorEngine.designColumn(
                width = if (colType == ColumnSectionView.ColumnType.CIRCULAR) diameter else width,
                depth = if (colType == ColumnSectionView.ColumnType.CIRCULAR) diameter else depth,
                pu = axialLoad,
                fcu = fc,
                fy = fy,
                code = selectedCode,
                isCircular = colType == ColumnSectionView.ColumnType.CIRCULAR,
                connectedSlab = slabType,
                hasCap = hasCap,
                clearHeight = clearHeight
            )
            
            // Override result diameter if user chose one
            val finalResult = if (preferredBar != 16) {
                val areaOneBar = PI * preferredBar.toDouble().pow(2.0) / 4.0
                val n = ceil(result.reinforcementArea / areaOneBar).toInt().coerceAtLeast(if (colType == ColumnSectionView.ColumnType.CIRCULAR) 6 else 4)
                result.copy(reinforcement = CalculatorEngine.ReinforcementBar(n, preferredBar))
            } else result

            lastResult = finalResult
            showResults(finalResult)
            populateSafetyChecks(finalResult)
            showAlternatives(finalResult)
            Toast.makeText(requireContext(), "تم التصميم بنجاح", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError(e.message ?: "خطأ غير معروف")
        }
    }
    
    private fun populateSafetyChecks(result: CalculatorEngine.ColumnResult) {
        binding.checksContainer.removeAllViews()
        result.safetyChecks.forEach { check ->
            val view = TextView(requireContext()).apply {
                text = getString(R.string.data_sheet_row, check.name, 
                    String.format(Locale.US, "%.2f / %.2f %s -> %s", 
                        check.value, check.limit, check.unit, 
                        if(check.isSafe) "✅ Safe" else "❌ Unsafe"))
                setPadding(0, 8, 0, 8)
                setTextColor(if(check.isSafe) resources.getColor(R.color.success, null) else resources.getColor(R.color.error, null))
            }
            binding.checksContainer.addView(view)
        }
    }

    private fun showResults(result: CalculatorEngine.ColumnResult) {
        binding.resultsCard.visibility = View.VISIBLE
        
        binding.etLongitudinalBars.setText(getString(R.string.reinforcement_format, result.reinforcement.numBars, result.reinforcement.diameter))
        
        val summary = String.format(Locale.US, "Cap: %.1f kN | Ratio: %.2f%% | Conc: %.2f m³ | Steel: %.1f kg | Waste: %.1f kg", 
            result.axialCapacity, result.reinforcementRatio, result.concreteVolume, result.steelWeight - result.steelWasteKg, result.steelWasteKg)
        binding.etCapacity.setText(summary)
        
        binding.columnSectionView.updateFromCalculation(
            type = colType,
            width = result.width.toFloat(),
            height = result.depth.toFloat(),
            diameter = result.width.toFloat(),
            coverValue = 40f,
            corners = result.reinforcement.numBars,
            cornerDia = result.reinforcement.diameter,
            sideX = 0, // simplified for now
            sideY = 0,
            sideDia = 0,
            tieDia = result.stirrups.diameter,
            spiral = false,
            axialCap = result.axialCapacity,
            mxCap = 0.0,
            myCap = 0.0,
            appAxial = result.pu,
            appMx = 0.0,
            appMy = 0.0,
            safe = result.isSafe
        )

        binding.btnExportPdf.setOnClickListener { exportToPdf(result) }
    }

    private fun showAlternatives(result: CalculatorEngine.ColumnResult) {
        if (result.rebarAlternatives.isEmpty()) return
        
        val altStrings = result.rebarAlternatives.map { getString(R.string.reinforcement_format, it.numBars, it.diameter) }
        // We could show this in a dialog or a horizontal scroll
        binding.etLongitudinalBars.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reinforcement Alternatives")
                .setItems(altStrings.toTypedArray()) { _, which ->
                    val chosen = result.rebarAlternatives[which]
                    binding.etLongitudinalBars.setText(getString(R.string.reinforcement_format, chosen.numBars, chosen.diameter))
                    // Update drawing with chosen alternative
                    binding.columnSectionView.cornerBars = chosen.numBars
                    binding.columnSectionView.cornerBarDiameter = chosen.diameter
                    binding.columnSectionView.invalidate()
                }
                .show()
        }
    }

    private fun exportToPdf(result: CalculatorEngine.ColumnResult) {
        try {
            val exporter = ComprehensivePdfExporter(requireContext())
            val project = projectsList.firstOrNull { it.id == projectId }
            val projectName = project?.name ?: "Unnamed Project"
            
            val fileName = "Column_Report_${System.currentTimeMillis()}.pdf"
            val filePath = File(requireContext().getExternalFilesDir(null) ?: requireContext().cacheDir, fileName).absolutePath
            
            // Convert CalculatorEngine result to AdvancedColumnResult for the exporter
            val columnType = if (result.columnType == "CIRCULAR") ColumnType.Circular(result.width) else ColumnType.Rectangular(result.width, result.depth)
            
            val advResult = AdvancedColumnResult(
                columnType = columnType,
                axialCapacity = result.axialCapacity,
                momentCapacityX = 0.0,
                momentCapacityY = 0.0,
                slendernessRatio = result.slenderness,
                isSlender = result.isSlender,
                effectiveLength = 3000.0,
                reinforcementResult = ReinforcementResult(
                    astRequired = result.reinforcement.area,
                    astProvided = result.reinforcement.area,
                    barDiameter = result.reinforcement.diameter.toDouble(),
                    numberOfBars = result.reinforcement.numBars,
                    tiesDiameter = result.stirrups.diameter.toDouble(),
                    tiesSpacing = result.stirrups.spacing,
                    isSafe = result.isSafe,
                    utilizationRatio = result.pu / result.axialCapacity.coerceAtLeast(1.0)
                ),
                inventoryAnalysis = null,
                biaxialCheck = null,
                punchingCheck = PunchingCheckResult(result.pu, 1000.0, result.punchingSafe, false, 2000.0),
                warnings = emptyList(),
                codeNotes = listOf("Exported from Civil EG App")
            )

            val inputs = ColumnInputs(
                fcu = 25.0, fy = 360.0, axialLoad = result.pu, momentX = 0.0, momentY = 0.0,
                loadCombination = LoadCombination.DEAD_LIVE, columnType = columnType
            )

            exporter.exportColumnReport(
                projectName = projectName,
                designCode = DomainDesignCode.ECP,
                columnType = columnType,
                inputs = inputs,
                result = advResult,
                inventoryAnalysis = null,
                alternatives = emptyList(),
                outputPath = filePath
            )
            
            Toast.makeText(requireContext(), "PDF Exported: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            showError("PDF Export Error: ${e.message}")
        }
    }

    private fun setupSaveButton() {
        binding.btnSaveDesign.setOnClickListener {
            val result = lastResult ?: return@setOnClickListener
            saveDesign(result)
        }
    }

    private fun saveDesign(result: CalculatorEngine.ColumnResult) {
        val effectiveProjectId = if (projectId != -1L) projectId else projectsList.firstOrNull()?.id ?: -1L
        if (effectiveProjectId != -1L) {
            lifecycleScope.launch {
                val inputData = org.json.JSONObject().apply {
                    put("width", result.width)
                    put("depth", result.depth)
                    put("pu", result.pu)
                    put("isCircular", colType == ColumnSectionView.ColumnType.CIRCULAR)
                }.toString()

                val design = Design(
                    projectId = effectiveProjectId,
                    type = DesignType.COLUMN,
                    name = "Column ${result.width}x${result.depth}",
                    inputData = inputData,
                    results = result.toString(),
                    isSafe = result.isSafe,
                    codeUsed = result.code.displayName,
                    concreteVolume = result.concreteVolume,
                    steelWeight = result.steelWeight,
                    totalCost = result.cost
                )
                viewModel.saveDesign(design)
                Toast.makeText(requireContext(), "تم حفظ التصميم بنجاح", Toast.LENGTH_SHORT).show()
            }
        } else {
            showError("يرجى اختيار أو إنشاء مشروع أولاً")
        }
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("تنبيه")
            .setMessage(message)
            .setPositiveButton("موافق", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
