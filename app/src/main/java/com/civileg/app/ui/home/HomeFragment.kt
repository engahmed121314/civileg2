package com.civileg.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.core.os.bundleOf
import com.civileg.app.R
import com.civileg.app.databinding.FragmentHomeBinding
import com.civileg.app.utils.SettingsManager
import com.civileg.app.viewmodel.ProjectViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class HomeFragment : Fragment() {
    
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ProjectViewModel by viewModels()

    @Inject
    lateinit var settingsManager: SettingsManager
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        checkDisclaimer()
        setupToolbar()
        setupStatistics()
        setupGridItems()
        setupManagementCards()
    }

    private fun checkDisclaimer() {
        if (!settingsManager.isDisclaimerAccepted) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.disclaimer_title)
                .setMessage(R.string.disclaimer_text)
                .setCancelable(false)
                .setPositiveButton(R.string.accept) { dialog, _ ->
                    settingsManager.isDisclaimerAccepted = true
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun setupToolbar() {
        binding.btnLanguage.setOnClickListener {
            toggleLanguage()
        }
    }
    
    private fun setupStatistics() {
        viewModel.activeProjectCount.observe(viewLifecycleOwner) { count ->
            binding.tvActiveProjects.text = count.toString()
        }
        
        viewModel.allDesigns.observe(viewLifecycleOwner) { designs ->
            binding.tvEstimates.text = designs.size.toString()
        }
    }

    private fun setupGridItems() {
        // Slab
        binding.cardSlab.ivIcon.setImageResource(R.drawable.ic_slab)
        binding.cardSlab.tvTitle.text = getString(R.string.slab_design)
        binding.cardSlab.root.setOnClickListener { 
            findNavController().navigate(R.id.action_home_to_slab, bundleOf("projectId" to -1L))
        }

        // Beam
        binding.cardBeam.ivIcon.setImageResource(R.drawable.ic_beam)
        binding.cardBeam.tvTitle.text = getString(R.string.beam_design)
        binding.cardBeam.root.setOnClickListener { 
            findNavController().navigate(R.id.action_home_to_beam, bundleOf("projectId" to -1L))
        }

        // Column
        binding.cardColumn.ivIcon.setImageResource(R.drawable.ic_column)
        binding.cardColumn.tvTitle.text = getString(R.string.column_design)
        binding.cardColumn.root.setOnClickListener { 
            findNavController().navigate(R.id.action_home_to_column, bundleOf("projectId" to -1L))
        }

        // Footing
        binding.cardFooting.ivIcon.setImageResource(R.drawable.ic_footing)
        binding.cardFooting.tvTitle.text = getString(R.string.footing_design)
        binding.cardFooting.root.setOnClickListener { 
            findNavController().navigate(R.id.action_home_to_footing, bundleOf("projectId" to -1L))
        }

        // Stairs
        binding.cardStairs.ivIcon.setImageResource(R.drawable.ic_stairs)
        binding.cardStairs.tvTitle.text = getString(R.string.stairs_design)
        binding.cardStairs.root.setOnClickListener { 
            findNavController().navigate(R.id.action_home_to_stairs, bundleOf("projectId" to -1L))
        }

        // More
        binding.cardMore.ivIcon.setImageResource(R.drawable.ic_more)
        binding.cardMore.tvTitle.text = getString(R.string.nav_more)
        binding.cardMore.root.setOnClickListener { showMoreToolsDialog() }
    }
    
    private fun setupManagementCards() {
        binding.cardQuantity.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_quantity)
        }
        
        binding.cardConverter.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_converter)
        }
    }

    private fun showMoreToolsDialog() {
        val options = arrayOf(
            getString(R.string.retaining_wall),
            getString(R.string.water_tank),
            getString(R.string.pile_foundation),
            getString(R.string.seismic_analysis),
            getString(R.string.nav_projects),
            if (com.civileg.app.utils.LocaleHelper.getLocale(requireContext()) == "ar") "الآلة الحاسبة العلمية" else "Scientific Calculator"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.nav_more))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> findNavController().navigate(R.id.action_home_to_retaining, bundleOf("projectId" to -1L))
                    1 -> findNavController().navigate(R.id.action_home_to_watertank, bundleOf("projectId" to -1L))
                    2 -> findNavController().navigate(R.id.action_home_to_pile)
                    3 -> findNavController().navigate(R.id.action_home_to_seismic, bundleOf("projectId" to -1L))
                    4 -> findNavController().navigate(R.id.action_home_to_projects)
                    5 -> findNavController().navigate(R.id.action_home_to_calculator)
                }
            }
            .show()
    }
    
    private fun toggleLanguage() {
        val currentLang = com.civileg.app.utils.LocaleHelper.getLocale(requireContext())
        val newLang = if (currentLang == "en") "ar" else "en"
        (activity as? com.civileg.app.MainActivity)?.setLocale(newLang)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
