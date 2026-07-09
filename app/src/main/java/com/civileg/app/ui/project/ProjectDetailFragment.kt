package com.civileg.app.ui.project

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.civileg.app.R
import com.civileg.app.adapter.DesignHistoryAdapter
import com.civileg.app.databinding.FragmentProjectDetailBinding
import com.civileg.app.viewmodel.ProjectViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProjectDetailFragment : Fragment() {

    private var _binding: FragmentProjectDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProjectViewModel by viewModels()
    private var projectId: Long = -1L
    private lateinit var adapter: DesignHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Read projectId from arguments (passed via Bundle, not Safe Args)
        projectId = arguments?.getLong("projectId", -1L) ?: -1L
        setupRecyclerView()
        setupToolbar()
        setupFab()
        observeData()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_right)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        adapter = DesignHistoryAdapter { design ->
            Toast.makeText(context, "Opening ${design.name}", Toast.LENGTH_SHORT).show()
        }
        binding.rvItems.layoutManager = LinearLayoutManager(context)
        binding.rvItems.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAddDesign.setOnClickListener {
            showAddDesignOptions()
        }
    }

    private fun showAddDesignOptions() {
        val options = arrayOf(
            getString(R.string.beam_design),
            getString(R.string.column_design),
            getString(R.string.footing_design),
            getString(R.string.slab_design),
            getString(R.string.stairs_design),
            getString(R.string.retaining_wall),
            getString(R.string.water_tank)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Design to Project")
            .setItems(options) { _, which ->
                val bundle = Bundle().apply { putLong("projectId", projectId) }
                when (which) {
                    0 -> findNavController().navigate(R.id.nav_beam, bundle)
                    1 -> findNavController().navigate(R.id.nav_column, bundle)
                    2 -> findNavController().navigate(R.id.nav_footing, bundle)
                    3 -> findNavController().navigate(R.id.nav_slab, bundle)
                    4 -> findNavController().navigate(R.id.nav_stairs, bundle)
                    5 -> findNavController().navigate(R.id.nav_retaining, bundle)
                    6 -> findNavController().navigate(R.id.nav_watertank, bundle)
                }
            }
            .show()
    }

    private fun observeData() {
        viewModel.allProjects.observe(viewLifecycleOwner) { projects ->
            val project = projects.find { it.id == projectId }
            project?.let {
                binding.toolbar.title = it.name
                binding.tvProjectInfo.text = "${it.clientName} | ${it.location}"
            }
        }

        viewModel.getDesignsForProject(projectId).observe(viewLifecycleOwner) { designs ->
            binding.tvDesignCount.text = "${designs.size} Designs"
            adapter.submitList(designs)
        }

        viewModel.getMaterialsForProject(projectId).observe(viewLifecycleOwner) { materials ->
            val total = materials.sumOf { it.totalPrice }
            binding.tvTotalCost.text = String.format("Total: %,.0f EGP", total)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}