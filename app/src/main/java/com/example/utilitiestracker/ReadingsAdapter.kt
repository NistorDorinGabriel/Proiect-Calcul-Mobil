package com.example.utilitiestracker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.utilitiestracker.databinding.ItemReadingBinding

class ReadingsAdapter(
    private var items: List<ReadingRow>,
    private val onItemClick: (ReadingRow) -> Unit
) : RecyclerView.Adapter<ReadingsAdapter.VH>() {

    class VH(val binding: ItemReadingBinding) : RecyclerView.ViewHolder(binding.root)

    fun updateItems(newItems: List<ReadingRow>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemReadingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.tvLine1.text = item.title
        holder.binding.tvLine2.text = item.details
        holder.binding.root.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
