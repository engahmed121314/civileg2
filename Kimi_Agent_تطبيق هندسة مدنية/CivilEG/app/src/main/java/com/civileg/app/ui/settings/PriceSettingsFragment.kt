package com.civileg.app.ui.settings

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
import com.civileg.app.databinding.FragmentPriceSettingsBinding
import com.civileg.app.pricing.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class PriceSettingsFragment : Fragment() {
    
    private var _binding: FragmentPriceSettingsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var adapter: PricesAdapter
    private var currentCategory = MaterialCategory.CONCRETE
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPriceSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupCurrencySpinner()
        setupCategoryTabs()
        setupRecyclerView()
        setupButtons()
        loadPrices()
    }
    
    private fun setupToolbar() {
        binding.toolbar.title = getString(R.string.price_settings)
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
    }
    
    private fun setupCurrencySpinner() {
        val currencies = Currency.values().map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCurrency.adapter = adapter
        
        // Set current currency
        val currentCurrency = PriceManager.getCurrency(requireContext())
        binding.spinnerCurrency.setSelection(currencies.indexOf(currentCurrency.name))
        
        binding.spinnerCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = Currency.valueOf(currencies[position])
                PriceManager.setCurrency(requireContext(), selected)
                loadPrices()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupCategoryTabs() {
        val tabs = listOf(
            binding.tabConcrete to MaterialCategory.CONCRETE,
            binding.tabSteel to MaterialCategory.STEEL,
            binding.tabCement to MaterialCategory.CEMENT,
            binding.tabAggregate to MaterialCategory.AGGREGATE,
            binding.tabBricks to MaterialCategory.BRICKS,
            binding.tabFormwork to MaterialCategory.FORMWORK,
            binding.tabFinishing to MaterialCategory.FINISHING,
            binding.tabLabor to MaterialCategory.LABOR
        )
        
        tabs.forEach { (tab, category) ->
            tab.setOnClickListener {
                currentCategory = category
                highlightTab(tab)
                loadPrices()
            }
        }
        
        // Select first tab
        highlightTab(binding.tabConcrete)
    }
    
    private fun highlightTab(selected: View) {
        listOf(
            binding.tabConcrete, binding.tabSteel, binding.tabCement,
            binding.tabAggregate, binding.tabBricks, binding.tabFormwork,
            binding.tabFinishing, binding.tabLabor
        ).forEach { it.alpha = if (it == selected) 1.0f else 0.5f }
    }
    
    private fun setupRecyclerView() {
        adapter = PricesAdapter { materialId, newPrice ->
            PriceManager.updatePrice(requireContext(), materialId, newPrice)
            Snackbar.make(binding.root, getString(R.string.save_success), Snackbar.LENGTH_SHORT).show()
        }
        
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
    }
    
    private fun setupButtons() {
        // Auto update switch
        binding.switchAutoUpdate.isChecked = PriceManager.isAutoUpdateEnabled(requireContext())
        binding.switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            PriceManager.setAutoUpdate(requireContext(), isChecked)
        }
        
        // Update from internet
        binding.btnUpdateFromInternet.setOnClickListener {
            showCountrySelectionDialog()
        }
        
        // Reset to defaults
        binding.btnResetDefaults.setOnClickListener {
            confirmReset()
        }
        
        // Export prices
        binding.btnExport.setOnClickListener {
            exportPrices()
        }
        
        // Import prices
        binding.btnImport.setOnClickListener {
            importPrices()
        }
        
        // Last update info
        binding.tvLastUpdate.text = getString(R.string.last_update) + ": " + 
            PriceManager.getLastUpdateFormatted(requireContext())
    }
    
    private fun loadPrices() {
        val allPrices = PriceManager.getAllPrices(requireContext())
        val filteredPrices = allPrices.filter { it.value.category == currentCategory }
        adapter.submitList(filteredPrices.values.toList())
    }
    
    private fun showCountrySelectionDialog() {
        val countries = arrayOf("Egypt", "Saudi Arabia", "UAE")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.select_country))
            .setItems(countries) { _, which ->
                val country = when (which) {
                    0 -> "egypt"
                    1 -> "saudi"
                    2 -> "uae"
                    else -> "egypt"
                }
                updatePricesFromInternet(country)
            }
            .show()
    }
    
    private fun updatePricesFromInternet(country: String) {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val success = PriceManager.updatePricesFromInternet(requireContext(), country)
            
            binding.progressBar.visibility = View.GONE
            
            if (success) {
                loadPrices()
                binding.tvLastUpdate.text = getString(R.string.last_update) + ": " + 
                    PriceManager.getLastUpdateFormatted(requireContext())
                Snackbar.make(binding.root, getString(R.string.update_success), Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(binding.root, "Update failed. Please try again.", Snackbar.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun confirmReset() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.reset))
            .setMessage("Are you sure you want to reset all prices to defaults?")
            .setPositiveButton(getString(R.string.reset)) { _, _ ->
                PriceManager.resetToDefaults(requireContext())
                loadPrices()
                Snackbar.make(binding.root, "Prices reset to defaults", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    private fun exportPrices() {
        val json = PriceManager.exportPrices(requireContext())
        
        // Save to file or share
        // For now, just show a message
        Snackbar.make(binding.root, "Prices exported successfully", Snackbar.LENGTH_SHORT).show()
    }
    
    private fun importPrices() {
        // Show dialog to paste JSON
        val input = android.widget.EditText(requireContext())
        input.hint = "Paste JSON here"
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Import Prices")
            .setView(input)
            .setPositiveButton("Import") { _, _ ->
                val json = input.text.toString()
                if (PriceManager.importPrices(requireContext(), json)) {
                    loadPrices()
                    Snackbar.make(binding.root, "Prices imported successfully", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(binding.root, "Invalid JSON format", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// Adapter for prices
class PricesAdapter(
    private val onPriceChanged: (String, Double) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<PricesAdapter.ViewHolder>() {
    
    private var prices = listOf<MaterialPrice>()
    
    fun submitList(newPrices: List<MaterialPrice>) {
        prices = newPrices
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = com.civileg.app.databinding.ItemPriceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(prices[position])
    }
    
    override fun getItemCount() = prices.size
    
    inner class ViewHolder(
        private val binding: com.civileg.app.databinding.ItemPriceBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        
        fun bind(price: MaterialPrice) {
            val isArabic = binding.root.context.resources.configuration.locale.language == "ar"
            
            binding.tvMaterialName.text = if (isArabic) price.nameAr else price.name
            binding.tvUnit.text = if (isArabic) price.unitAr else price.unit
            binding.etPrice.setText(price.price.toString())
            
            binding.etPrice.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val newPrice = binding.etPrice.text.toString().toDoubleOrNull()
                    if (newPrice != null && newPrice != price.price) {
                        onPriceChanged(price.id, newPrice)
                    }
                }
            }
        }
    }
}
