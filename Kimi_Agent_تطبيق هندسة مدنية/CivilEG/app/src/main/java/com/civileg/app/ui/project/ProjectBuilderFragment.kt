package com.civileg.app.ui.project

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.civileg.app.R
import com.civileg.app.databinding.FragmentProjectBuilderBinding
import com.civileg.app.pricing.*
import com.civileg.app.utils.PdfGenerator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.*

class ProjectBuilderFragment : Fragment() {
    
    private var _binding: FragmentProjectBuilderBinding? = null
    private val binding get() = _binding!!
    
    private val elements = mutableListOf<ProjectElement>()
    private lateinit var adapter: ProjectElementsAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectBuilderBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupRecyclerView()
        setupButtons()
        updateSummary()
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = getString(R.string.project_builder)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ProjectElementsAdapter(
            elements = elements,
            onEdit = { element -> editElement(element) },
            onDelete = { element -> deleteElement(element) },
            onDuplicate = { element -> duplicateElement(element) }
        )
        
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupButtons() {
        binding.btnAddElement.setOnClickListener {
            showAddElementDialog()
        }
        
        binding.btnGenerateReport.setOnClickListener {
            generateReport()
        }
        
        binding.btnSaveProject.setOnClickListener {
            saveProject()
        }
        
        binding.btnClearAll.setOnClickListener {
            confirmClearAll()
        }
    }
    
    private fun showAddElementDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_element, null)
        
        // Setup spinners
        val spinnerType = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerElementType)
        val spinnerConcrete = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerConcreteGrade)
        
        // Element types
        val types = arrayOf(
            getString(R.string.footing_design),
            getString(R.string.column_design),
            getString(R.string.beam_design),
            getString(R.string.slab_design),
            getString(R.string.staircase_design),
            getString(R.string.retaining_wall)
        )
        
        // Concrete grades
        val concreteGrades = arrayOf("C15", "C20", "C25", "C30", "C35", "C40", "C45", "C50")
        
        spinnerType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, types)
        spinnerConcrete.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, concreteGrades)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_element))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.add)) { _, _ ->
                val typeIndex = spinnerType.selectedItemPosition
                val concreteGrade = spinnerConcrete.selectedItem.toString()
                
                // Get dimensions based on type
                val element = createElementFromType(typeIndex, concreteGrade)
                elements.add(element)
                adapter.notifyItemInserted(elements.size - 1)
                updateSummary()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun createElementFromType(typeIndex: Int, concreteGrade: String): ProjectElement {
        return when (typeIndex) {
            0 -> { // Footing
                ProjectElement(
                    id = UUID.randomUUID().toString(),
                    name = "Footing F-${elements.size + 1}",
                    type = ElementType.FOOTING,
                    concreteGrade = concreteGrade.lowercase(),
                    volume = 12.0, // 3m x 3m x 0.4m
                    steelRatio = 0.015, // 1.5%
                    dimensions = "3000 x 3000 x 400 mm"
                )
            }
            1 -> { // Column
                ProjectElement(
                    id = UUID.randomUUID().toString(),
                    name = "Column C-${elements.size + 1}",
                    type = ElementType.COLUMN,
                    concreteGrade = concreteGrade.lowercase(),
                    volume = 0.64, // 0.4m x 0.4m x 4m
                    steelRatio = 0.025, // 2.5%
                    dimensions = "400 x 400 x 4000 mm"
                )
            }
            2 -> { // Beam
                ProjectElement(
                    id = UUID.randomUUID().toString(),
                    name = "Beam B-${elements.size + 1}",
                    type = ElementType.BEAM,
                    concreteGrade = concreteGrade.lowercase(),
                    volume = 1.08, // 0.3m x 0.6m x 6m
                    steelRatio = 0.018, // 1.8%
                    dimensions = "300 x 600 x 6000 mm"
                )
            }
            3 -> { // Slab
                ProjectElement(
                    id = UUID.randomUUID().toString(),
                    name = "Slab S-${elements.size + 1}",
                    type = ElementType.SLAB,
                    concreteGrade = concreteGrade.lowercase(),
                    volume = 7.5, // 5m x 6m x 0.25m
                    steelRatio = 0.008, // 0.8%
                    dimensions = "5000 x 6000 x 250 mm"
                )
            }
            4 -> { // Staircase
                ProjectElement(
                    id = UUID.randomUUID().toString(),
                    name = "Stair ST-${elements.size + 1}",
                    type = ElementType.STAIRCASE,
                    concreteGrade = concreteGrade.lowercase(),
                    volume = 2.5,
                    steelRatio = 0.012, // 1.2%
                    dimensions = "1200 x 3000 x 150 mm (waist)"
                )
            }
            5 -> { // Wall
                ProjectElement(
                    id = UUID.randomUUID().toString(),
                    name = "Wall W-${elements.size + 1}",
                    type = ElementType.WALL,
                    concreteGrade = concreteGrade.lowercase(),
                    volume = 4.0, // 5m x 4m x 0.2m
                    steelRatio = 0.006, // 0.6%
                    dimensions = "5000 x 4000 x 200 mm"
                )
            }
            else -> throw IllegalArgumentException("Invalid type")
        }
    }
    
    private fun editElement(element: ProjectElement) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_element, null)
        
        // Pre-fill values
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etElementName)
        val etVolume = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVolume)
        val etSteelRatio = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSteelRatio)
        val etDimensions = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etDimensions)
        
        etName.setText(element.name)
        etVolume.setText(element.volume.toString())
        etSteelRatio.setText((element.steelRatio * 100).toString())
        etDimensions.setText(element.dimensions)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_element))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val index = elements.indexOfFirst { it.id == element.id }
                if (index != -1) {
                    elements[index] = element.copy(
                        name = etName.text.toString(),
                        volume = etVolume.text.toString().toDoubleOrNull() ?: element.volume,
                        steelRatio = (etSteelRatio.text.toString().toDoubleOrNull() ?: 1.0) / 100,
                        dimensions = etDimensions.text.toString()
                    )
                    adapter.notifyItemChanged(index)
                    updateSummary()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun deleteElement(element: ProjectElement) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.delete_confirm))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                val index = elements.indexOfFirst { it.id == element.id }
                if (index != -1) {
                    elements.removeAt(index)
                    adapter.notifyItemRemoved(index)
                    updateSummary()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun duplicateElement(element: ProjectElement) {
        val newElement = element.copy(
            id = UUID.randomUUID().toString(),
            name = "${element.name} (Copy)"
        )
        elements.add(newElement)
        adapter.notifyItemInserted(elements.size - 1)
        updateSummary()
    }
    
    private fun updateSummary() {
        if (elements.isEmpty()) {
            binding.cardSummary.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            return
        }
        
        binding.cardSummary.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        
        context?.let { ctx ->
            val cost = PriceManager.calculateProjectCost(ctx, elements)
            
            val currency = PriceManager.getCurrency(ctx)
            val symbol = when (currency) {
                Currency.EGP -> "EGP"
                Currency.USD -> "$"
                Currency.SAR -> "SAR"
                Currency.AED -> "AED"
                else -> "EGP"
            }
            
            binding.tvElementCount.text = "${elements.size} ${getString(R.string.elements)}"
            binding.tvTotalConcrete.text = String.format("%.2f m³", elements.sumOf { it.volume })
            binding.tvTotalSteel.text = String.format("%.2f ton", cost.totalSteelWeight)
            
            binding.tvConcreteCost.text = String.format("%,.0f %s", cost.concreteCost, symbol)
            binding.tvSteelCost.text = String.format("%,.0f %s", cost.steelCost, symbol)
            binding.tvLaborCost.text = String.format("%,.0f %s", cost.laborCost, symbol)
            binding.tvTotalCost.text = String.format("%,.0f %s", cost.totalCost, symbol)
        }
    }
    
    private fun generateReport() {
        if (elements.isEmpty()) {
            Snackbar.make(binding.root, getString(R.string.no_elements), Snackbar.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            context?.let { ctx ->
                val cost = PriceManager.calculateProjectCost(ctx, elements)
                
                val inputs = mapOf(
                    "Project Name" to (binding.etProjectName.text?.toString() ?: "Unnamed Project"),
                    "Number of Elements" to elements.size.toString(),
                    "Total Concrete" to "%.2f m³".format(elements.sumOf { it.volume }),
                    "Total Steel" to "%.2f ton".format(cost.totalSteelWeight)
                )
                
                val results = mapOf(
                    "Concrete Cost" to "%,.0f".format(cost.concreteCost),
                    "Steel Cost" to "%,.0f".format(cost.steelCost),
                    "Labor Cost" to "%,.0f".format(cost.laborCost),
                    "Total Cost" to "%,.0f".format(cost.totalCost)
                )
                
                try {
                    val file = PdfGenerator.generateDesignReport(
                        ctx,
                        "Project Cost Report",
                        "Project",
                        inputs,
                        results,
                        true
                    )
                    
                    Snackbar.make(
                        binding.root,
                        getString(R.string.export_success),
                        Snackbar.LENGTH_LONG
                    ).setAction(getString(R.string.open)) {
                        // Open PDF
                    }.show()
                } catch (e: Exception) {
                    Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun saveProject() {
        if (elements.isEmpty()) {
            Snackbar.make(binding.root, getString(R.string.no_elements), Snackbar.LENGTH_SHORT).show()
            return
        }
        
        // Save to database
        Snackbar.make(binding.root, getString(R.string.save_success), Snackbar.LENGTH_SHORT).show()
    }
    
    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.clear_all))
            .setMessage(getString(R.string.delete_confirm))
            .setPositiveButton(getString(R.string.clear)) { _, _ ->
                elements.clear()
                adapter.notifyDataSetChanged()
                updateSummary()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Adapter for project elements
class ProjectElementsAdapter(
    private val elements: List<ProjectElement>,
    private val onEdit: (ProjectElement) -> Unit,
    private val onDelete: (ProjectElement) -> Unit,
    private val onDuplicate: (ProjectElement) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<ProjectElementsAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = com.civileg.app.databinding.ItemProjectElementBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(elements[position])
    }
    
    override fun getItemCount() = elements.size
    
    inner class ViewHolder(
        private val binding: com.civileg.app.databinding.ItemProjectElementBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        
        fun bind(element: ProjectElement) {
            binding.tvElementName.text = element.name
            binding.tvElementType.text = element.type.name
            binding.tvConcreteGrade.text = element.concreteGrade.uppercase()
            binding.tvVolume.text = "%.2f m³".format(element.volume)
            binding.tvSteelRatio.text = "%.2f%%".format(element.steelRatio * 100)
            binding.tvDimensions.text = element.dimensions
            
            // Calculate cost
            val ctx = binding.root.context
            val cost = PriceManager.calculateElementCost(
                ctx,
                element.concreteGrade,
                element.volume,
                element.steelRatio,
                element.volume
            )
            
            val currency = PriceManager.getCurrency(ctx)
            val symbol = when (currency) {
                Currency.EGP -> "EGP"
                Currency.USD -> "$"
                Currency.SAR -> "SAR"
                else -> "EGP"
            }
            
            binding.tvElementCost.text = "%,.0f %s".format(cost.totalCost, symbol)
            
            binding.btnEdit.setOnClickListener { onEdit(element) }
            binding.btnDelete.setOnClickListener { onDelete(element) }
            binding.btnDuplicate.setOnClickListener { onDuplicate(element) }
        }
    }
}
