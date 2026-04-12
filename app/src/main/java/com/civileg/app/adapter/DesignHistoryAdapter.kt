package com.civileg.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.civileg.app.R
import com.civileg.app.databinding.ItemDesignHistoryBinding
import com.civileg.app.db.Design
import com.civileg.app.db.DesignType

class DesignHistoryAdapter(
    private val onDesignClick: (Design) -> Unit
) : ListAdapter<Design, DesignHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDesignHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemDesignHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(design: Design) {
            binding.tvDesignName.text = design.name
            binding.tvDesignCode.text = design.codeUsed
            
            val iconRes = when (design.type) {
                DesignType.BEAM -> R.drawable.ic_beam
                DesignType.COLUMN -> R.drawable.ic_column
                DesignType.FOOTING -> R.drawable.ic_footing
                DesignType.SLAB -> R.drawable.ic_slab
                DesignType.STAIRCASE -> R.drawable.ic_stairs
                DesignType.RETAINING_WALL -> R.drawable.ic_wall
                DesignType.WATER_TANK -> R.drawable.ic_water
                DesignType.PILE -> R.drawable.ic_pile
                DesignType.SEISMIC -> R.drawable.ic_search // Or specific seismic icon
            }
            binding.ivDesignType.setImageResource(iconRes)

            binding.tvSafetyStatus.text = if (design.isSafe) "SAFE" else "UNSAFE"
            binding.tvSafetyStatus.setTextColor(
                if (design.isSafe) binding.root.context.getColor(R.color.success)
                else binding.root.context.getColor(R.color.danger)
            )

            binding.root.setOnClickListener { onDesignClick(design) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Design>() {
        override fun areItemsTheSame(oldItem: Design, newItem: Design) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Design, newItem: Design) = oldItem == newItem
    }
}
