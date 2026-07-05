package com.civileg.app.ui.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.civileg.app.databinding.ItemDrawingBinding
import com.civileg.app.model.DetailDrawing

class GalleryAdapter(private val onItemClick: (DetailDrawing) -> Unit) :
    ListAdapter<DetailDrawing, GalleryAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemDrawingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(drawing: DetailDrawing, onItemClick: (DetailDrawing) -> Unit) {
            binding.tvTitle.text = drawing.title
            binding.tvDrawingNumber.text = drawing.drawingNumber
            binding.tvDescription.text = drawing.description
            
            // Set icon based on type
            val iconRes = when (drawing.type) {
                "beam" -> com.civileg.app.R.drawable.ic_beam
                "column" -> com.civileg.app.R.drawable.ic_column
                "footing" -> com.civileg.app.R.drawable.ic_footing
                "slab" -> com.civileg.app.R.drawable.ic_slab
                "stairs" -> com.civileg.app.R.drawable.ic_stairs
                else -> com.civileg.app.R.drawable.ic_design
            }
            binding.ivThumbnail.setImageResource(iconRes)
            
            binding.root.setOnClickListener { onItemClick(drawing) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDrawingBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onItemClick)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DetailDrawing>() {
        override fun areItemsTheSame(oldItem: DetailDrawing, newItem: DetailDrawing): Boolean {
            return oldItem.drawingNumber == newItem.drawingNumber
        }

        override fun areContentsTheSame(oldItem: DetailDrawing, newItem: DetailDrawing): Boolean {
            return oldItem == newItem
        }
    }
}
