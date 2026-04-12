package com.civileg.app.ui.column

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.civileg.app.R
import com.civileg.app.databinding.FragmentColumnDesignBinding
import com.civileg.app.db.*
import com.civileg.app.db.Project as DbProject
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.CalculatorEngine.DesignCode
import com.civileg.app.utils.SettingsManager
import com.civileg.app.views.ColumnSectionView
import com.civileg.app.viewmodel.ProjectViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ColumnDesignFragment : Fragment() {
    
    private var _binding: FragmentColumnDesignBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ProjectViewModel by viewModels()
    private val args: ColumnDesignFragmentArgs by navArgs()
    
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
        updateInitialDrawing()
        setupSaveButton()
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
        val height = binding.etColumnHeight.text.toString().toFloatOrNull() ?: 300f
        val diameter = binding.etColumnDiameter.text.toString().toFloatOrNull() ?: 300f
        
        binding.columnSectionView.apply {
            this.columnType = colType
            this.columnWidth = width
            this.columnHeight = height
            this.columnDiameter = diameter
            invalidate()
        }
    }
    
    private fun calculateColumn() {
        try {
            val width = binding.etColumnWidth.text.toString().toDoubleOrNull() ?: 300.0
            val depth = binding.etColumnHeight.text.toString().toDoubleOrNull() ?: 300.0
            val diameter = binding.etColumnDiameter.text.toString().toDoubleOrNull() ?: 300.0
            val axialLoad = binding.etAxialLoad.text.toString().toDoubleOrNull() ?: throw Exception("برجاء إدخال الحمل المحوري")
            val fc = binding.etConcreteStrength.text.toString().toDoubleOrNull() ?: 25.0
            val fy = binding.etSteelStrength.text.toString().toDoubleOrNull() ?: 360.0

            val result = calculatorEngine.designColumn(
                width = if (colType == ColumnSectionView.ColumnType.CIRCULAR) diameter else width,
                depth = if (colType == ColumnSectionView.ColumnType.CIRCULAR) diameter else depth,
                height = 3000.0,
                fcu = fc,
                fy = fy,
                pu = axialLoad,
                preferredDiameter = 16,
                code = selectedCode
            )
            
            lastResult = result
            showResults(result)
            Toast.makeText(requireContext(), "تم التصميم بنجاح", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            showError(e.message ?: "خطأ غير معروف")
        }
    }
    
    private fun showResults(result: CalculatorEngine.ColumnResult) {
        binding.resultsCard.visibility = View.VISIBLE
        
        binding.etLongitudinalBars.setText("${result.totalBars}Ø${result.barDiameter}")
        
        val summary = String.format(Locale.US, "التكلفة: %.2f %s | خرسانة: %.2f m³", 
            result.cost, settingsManager.currency, result.concreteVolume)
        binding.etCapacity.setText(summary)
        
        binding.columnSectionView.apply {
            this.columnType = colType
            this.columnWidth = result.width.toFloat()
            this.columnHeight = result.depth.toFloat()
            this.columnDiameter = result.width.toFloat()
            this.cornerBars = result.totalBars
            this.cornerBarDiameter = result.barDiameter
            invalidate()
        }

        binding.btnExportPdf.setOnClickListener { showDetailedReport(result) }
    }

    private fun showDetailedReport(result: CalculatorEngine.ColumnResult) {
        val details = StringBuilder()
        details.append("--- النوتة الحسابية ---\n")
        details.append("Design Code: ${result.code.displayName}\n")
        details.append("Applied Axial: ${String.format("%.2f", result.pu)} kN\n")
        
        details.append("\n--- حصر الكميات (BOQ) ---\n")
        details.append("الخرسانة: ${String.format("%.2f", result.concreteVolume)} m³\n")
        details.append("الحديد الكلي: ${String.format("%.1f", result.steelWeight)} kg\n")
        
        details.append("\n--- فحص الأمان ---\n")
        details.append("${if (result.isSafe) "✅" else "❌"} ${if (result.isSafe) "Safe Design" else "Unsafe - Check Section"}\n")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("نتائج تصميم العمود")
            .setMessage(details.toString())
            .setPositiveButton("إغلاق", null)
            .setNeutralButton("حفظ التصميم") { _, _ -> saveDesign(result) }
            .show()
    }

    private fun setupSaveButton() {
        binding.btnSaveDesign.setOnClickListener {
            val result = lastResult ?: return@setOnClickListener
            saveDesign(result)
        }
    }

    private fun saveDesign(result: CalculatorEngine.ColumnResult) {
        val projectId = if (args.projectId != -1L) args.projectId else projectsList.firstOrNull()?.id ?: -1L
        if (projectId != -1L) {
            lifecycleScope.launch {
                val design = Design(
                    projectId = projectId, type = DesignType.COLUMN,
                    name = "Column - ${System.currentTimeMillis() % 1000}",
                    inputData = "{}",
                    results = result.toString(),
                    isSafe = result.isSafe, codeUsed = result.code.displayName,
                    concreteVolume = result.concreteVolume, steelWeight = result.steelWeight,
                    totalCost = result.cost
                )
                viewModel.saveDesign(design)
                Toast.makeText(requireContext(), "تم الحفظ في المشروع", Toast.LENGTH_SHORT).show()
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
