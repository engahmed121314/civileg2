package com.civileg.app.ui.project

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.civileg.app.R
import com.civileg.app.databinding.FragmentProjectListBinding
import com.civileg.app.db.Project
import com.civileg.app.viewmodel.ProjectViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProjectListFragment : Fragment() {

    private var _binding: FragmentProjectListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProjectViewModel by viewModels()
    private lateinit var adapter: ProjectAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFab()
        observeProjects()
    }

    private fun setupRecyclerView() {
        adapter = ProjectAdapter(
            onProjectClick = { project ->
                navigateToProjectDetail(project)
            },
            onProjectLongClick = { project ->
                showProjectOptions(project)
                true
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showAddProjectDialog()
        }
    }

    private fun observeProjects() {
        viewModel.allProjects.observe(viewLifecycleOwner) { projects ->
            adapter.submitList(projects)
            binding.tvEmpty.visibility = if (projects.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun showAddProjectDialog() {
        val dialogBinding = com.civileg.app.databinding.DialogAddProjectBinding.inflate(layoutInflater)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("New Project")
            .setView(dialogBinding.root)
            .setPositiveButton("Create") { _, _ ->
                val project = Project(
                    name = dialogBinding.etName.text.toString(),
                    location = dialogBinding.etLocation.text.toString(),
                    clientName = dialogBinding.etClient.text.toString(),
                    description = dialogBinding.etDescription.text.toString()
                )
                viewModel.insert(project)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToProjectDetail(project: Project) {
        val bundle = Bundle().apply { putLong("projectId", project.id) }
        findNavController().navigate(R.id.nav_project_detail, bundle)
    }

    private fun showProjectOptions(project: Project) {
        val options = arrayOf("Edit", "Delete", "Export Report")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(project.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editProject(project)
                    1 -> deleteProject(project)
                    2 -> exportProjectReport(project)
                }
            }
            .show()
    }

    private fun editProject(project: Project) {
        // Show edit dialog
    }

    private fun deleteProject(project: Project) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Project")
            .setMessage("Are you sure you want to delete ${project.name}?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.delete(project)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportProjectReport(project: Project) {
        // Generate comprehensive PDF report
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}