package com.civileg.app.ui.project

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.civileg.app.databinding.ItemProjectBinding
import com.civileg.app.db.Project
import com.civileg.app.db.ProjectStatus
import java.text.SimpleDateFormat
import java.util.Locale

class ProjectAdapter(
    private val onProjectClick: (Project) -> Unit,
    private val onProjectLongClick: (Project) -> Boolean
) : ListAdapter<Project, ProjectAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProjectBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemProjectBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        fun bind(project: Project) {
            binding.tvProjectName.text = project.name
            binding.tvClient.text = project.clientName
            binding.tvLocation.text = project.location
            binding.tvStatus.text = project.status.name
            binding.tvDate.text = dateFormat.format(project.createdAt)

            val statusColor = when (project.status) {
                ProjectStatus.ACTIVE -> android.graphics.Color.parseColor("#4CAF50")
                ProjectStatus.COMPLETED -> android.graphics.Color.parseColor("#2196F3")
                ProjectStatus.ON_HOLD -> android.graphics.Color.parseColor("#FFC107")
                ProjectStatus.CANCELLED -> android.graphics.Color.parseColor("#F44336")
            }
            binding.viewStatus.setBackgroundColor(statusColor)

            binding.root.setOnClickListener { onProjectClick(project) }
            binding.root.setOnLongClickListener { onProjectLongClick(project) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Project>() {
        override fun areItemsTheSame(oldItem: Project, newItem: Project) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Project, newItem: Project) = oldItem == newItem
    }
}
