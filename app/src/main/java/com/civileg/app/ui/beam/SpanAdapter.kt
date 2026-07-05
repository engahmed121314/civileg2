package com.civileg.app.ui.beam

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.civileg.app.databinding.ItemBeamSpanBinding
import com.civileg.app.utils.ContinuousBeamAnalysis

class SpanAdapter(
    private val spans: MutableList<ContinuousBeamAnalysis.Span>,
    private val onSpanChanged: () -> Unit
) : RecyclerView.Adapter<SpanAdapter.SpanViewHolder>() {

    inner class SpanViewHolder(val binding: ItemBeamSpanBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SpanViewHolder {
        val binding = ItemBeamSpanBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SpanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SpanViewHolder, position: Int) {
        val span = spans[position]
        holder.binding.tvSpanIndex.text = "S${position + 1}"
        
        // Remove listeners to avoid trigger during binding
        holder.binding.etSpanLength.setText(span.length.toString())
        holder.binding.etSpanLoad.setText(span.load.toString())

        holder.binding.etSpanLength.doAfterTextChanged { 
            val newVal = it.toString().toDoubleOrNull() ?: 0.0
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                spans[pos] = spans[pos].copy(length = newVal)
                onSpanChanged()
            }
        }

        holder.binding.etSpanLoad.doAfterTextChanged {
            val newVal = it.toString().toDoubleOrNull() ?: 0.0
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                spans[pos] = spans[pos].copy(load = newVal)
                onSpanChanged()
            }
        }

        holder.binding.btnRemoveSpan.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION && spans.size > 1) {
                spans.removeAt(pos)
                notifyDataSetChanged()
                onSpanChanged()
            }
        }
    }

    override fun getItemCount() = spans.size

    fun addSpan() {
        spans.add(ContinuousBeamAnalysis.Span(5.0, 25.0))
        notifyItemInserted(spans.size - 1)
        onSpanChanged()
    }
}
