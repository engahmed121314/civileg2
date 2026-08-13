package com.civileg.app.ui.quantity

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.civileg.app.databinding.FragmentQuantitySurveyingBinding
import com.civileg.app.db.MaterialCategory
import com.civileg.app.db.MaterialItem
import com.civileg.app.utils.CalculatorEngine
import com.civileg.app.utils.ExportUtils
import com.civileg.app.utils.PdfGenerator
import com.civileg.app.viewmodel.ProjectViewModel
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

@AndroidEntryPoint
class QuantitySurveyingFragment : Fragment() {
    
    private var _binding: FragmentQuantitySurveyingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProjectViewModel by viewModels()
    
    private var currentMaterials: List<MaterialItem> = emptyList()
    private var currentProjectName: String = "Project"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQuantitySurveyingBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPieChart()
        setupButtons()
        observeData()
    }

    private fun setupPieChart() {
        binding.costPieChart.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            setExtraOffsets(5f, 10f, 5f, 5f)
            dragDecelerationFrictionCoef = 0.95f
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
            transparentCircleRadius = 61f
            holeRadius = 58f
            setDrawCenterText(true)
            centerText = "Cost Breakup"
            rotationAngle = 0f
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
            animateY(1400, Easing.EaseInOutQuad)
            legend.isEnabled = true
        }
    }
    
    private fun setupButtons() {
        binding.btnGenerateBoq.setOnClickListener {
            refreshData()
        }
        
        binding.btnExportPdf.setOnClickListener {
            if (currentMaterials.isEmpty()) {
                Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            exportBoqPdf()
        }
    }

    private fun exportBoqPdf() {
        var totalConcreteVol = 0.0
        var totalSteelWeight = 0.0
        var totalCost = 0.0
        val items = mutableListOf<Pair<String, Double>>()

        currentMaterials.forEach { item ->
            totalCost += item.totalPrice
            items.add(Pair(item.name, item.totalPrice))
            
            when (item.category) {
                MaterialCategory.CONCRETE -> totalConcreteVol += item.quantity
                MaterialCategory.STEEL -> totalSteelWeight += item.quantity
                else -> {}
            }
        }

        val file = PdfGenerator.generateBOQReport(
            requireContext(),
            currentProjectName,
            totalCost,
            totalConcreteVol,
            totalSteelWeight,
            items
        )
        ExportUtils.openPdf(requireContext(), file)
    }

    private fun refreshData() {
        viewModel.allProjects.observe(viewLifecycleOwner) { projects ->
            if (projects.isNotEmpty()) {
                val projectId = projects.first().id
                currentProjectName = projects.first().name
                loadProjectData(projectId)
            } else {
                Toast.makeText(context, "No projects found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeData() {
        viewModel.allProjects.observe(viewLifecycleOwner) { projects ->
            if (projects.isNotEmpty()) {
                currentProjectName = projects.first().name
                loadProjectData(projects.first().id)
            }
        }
    }

    private fun loadProjectData(projectId: Long) {
        viewModel.getMaterialsForProject(projectId).observe(viewLifecycleOwner) { materials ->
            currentMaterials = materials
            updateUIWithMaterials(materials)
        }
    }

    private fun updateUIWithMaterials(materials: List<MaterialItem>) {
        var totalConcreteVol = 0.0
        var totalSteelWeight = 0.0
        var concreteCost = 0.0
        var steelCost = 0.0

        materials.forEach { item ->
            when (item.category) {
                MaterialCategory.CONCRETE -> {
                    totalConcreteVol += item.quantity
                    concreteCost += item.totalPrice
                }
                MaterialCategory.STEEL -> {
                    totalSteelWeight += item.quantity
                    steelCost += item.totalPrice
                }
                else -> {}
            }
        }

        val totalCost = concreteCost + steelCost
        
        binding.tvTotalBudget.text = String.format(Locale.getDefault(), "%,.2f EGP", totalCost)
        binding.tvConcreteTotal.text = String.format(Locale.getDefault(), "%.2f m³", totalConcreteVol)
        binding.tvSteelTotal.text = String.format(Locale.getDefault(), "%.2f Tons", totalSteelWeight / 1000.0)
        
        binding.tvSlabConcrete.text = String.format(Locale.getDefault(), "Concrete: %.2f m³", totalConcreteVol)
        binding.tvSlabSteel.text = String.format(Locale.getDefault(), "Steel: %.0f kg", totalSteelWeight)

        updatePieChart(concreteCost, steelCost)
    }

    private fun updatePieChart(concreteCost: Double, steelCost: Double) {
        val entries = ArrayList<PieEntry>()
        if (concreteCost > 0) entries.add(PieEntry(concreteCost.toFloat(), "Concrete"))
        if (steelCost > 0) entries.add(PieEntry(steelCost.toFloat(), "Steel"))

        if (entries.isEmpty()) {
            binding.costPieChart.clear()
            return
        }

        val dataSet = PieDataSet(entries, "Costs")
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f
        
        val colors = ArrayList<Int>()
        colors.add(Color.parseColor("#2196F3")) // Concrete Color
        colors.add(Color.parseColor("#FF5722")) // Steel Color
        dataSet.colors = colors

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(binding.costPieChart))
        data.setValueTextSize(11f)
        data.setValueTextColor(Color.WHITE)
        
        binding.costPieChart.data = data
        binding.costPieChart.highlightValues(null)
        binding.costPieChart.invalidate()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
