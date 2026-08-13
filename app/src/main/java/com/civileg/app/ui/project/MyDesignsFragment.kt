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
import com.civileg.app.adapter.DesignHistoryAdapter
import com.civileg.app.databinding.FragmentMyDesignsBinding
import com.civileg.app.viewmodel.ProjectViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MyDesignsFragment : Fragment() {

    private var _binding: FragmentMyDesignsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: ProjectViewModel by viewModels()
    private lateinit var adapter: DesignHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyDesignsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupRecyclerView()
        observeData()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerView() {
        adapter = DesignHistoryAdapter { design ->
            // Open design details if needed
        }
        binding.rvAllDesigns.layoutManager = LinearLayoutManager(context)
        binding.rvAllDesigns.adapter = adapter
    }

    private fun observeData() {
        viewModel.allDesigns.observe(viewLifecycleOwner) { designs ->
            adapter.submitList(designs)
            binding.emptyView.visibility = if (designs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
