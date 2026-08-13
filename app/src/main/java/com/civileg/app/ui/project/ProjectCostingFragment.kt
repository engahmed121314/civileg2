package com.civileg.app.ui.project

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.civileg.app.databinding.FragmentProjectCostingBinding
import com.civileg.app.viewmodel.ProjectViewModel
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProjectCostingFragment : Fragment() {

    private var _binding: FragmentProjectCostingBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProjectViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectCostingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        observeProjectCosts()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun observeProjectCosts() {
        viewModel.allProjects.observe(viewLifecycleOwner) { projects ->
            if (projects.isNotEmpty()) {
                val projectId = projects.first().id
                loadProjectData(projectId)
            }
        }
    }

    private fun loadProjectData(projectId: Long) {
        viewModel.getMaterialsForProject(projectId).observe(viewLifecycleOwner) { materials ->
            var totalConcreteCost = 0.0
            var totalSteelCost = 0.0
            var totalConcreteVol = 0.0
            var totalSteelWeight = 0.0

            materials.forEach { item ->
                if (item.name.contains("Concrete", true)) {
                    totalConcreteCost += item.totalPrice
                    totalConcreteVol += item.quantity
                } else if (item.name.contains("Steel", true)) {
                    totalSteelCost += item.totalPrice
                    totalSteelWeight += item.quantity
                }
            }

            val totalBudget = totalConcreteCost + totalSteelCost
            binding.tvTotalBudget.text = String.format("%,.2f EGP", totalBudget)
            binding.tvConcreteTotal.text = String.format("%.2f m³", totalConcreteVol)
            binding.tvSteelTotal.text = String.format("%.2f Tons", totalSteelWeight / 1000.0)

            setupChart(totalConcreteCost, totalSteelCost)
        }
    }

    private fun setupChart(concrete: Double, steel: Double) {
        val entries = ArrayList<PieEntry>()
        if (concrete > 0) entries.add(PieEntry(concrete.toFloat(), "Concrete"))
        if (steel > 0) entries.add(PieEntry(steel.toFloat(), "Steel"))

        if (entries.isEmpty()) return

        val dataSet = PieDataSet(entries, "Cost Breakdown")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextColor = Color.WHITE
        dataSet.valueTextSize = 14f

        val data = PieData(dataSet)
        binding.pieChart.data = data
        binding.pieChart.description.isEnabled = false
        binding.pieChart.centerText = "Project Budget"
        binding.pieChart.animateY(1000)
        binding.pieChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
