package com.example.utilitiestracker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.utilitiestracker.data.local.TariffEntity
import com.example.utilitiestracker.databinding.ItemTariffBinding
import java.util.Locale

class TariffsAdapter(
    private val items: List<TariffEntity>
) : RecyclerView.Adapter<TariffsAdapter.VH>() {

    private val ro = Locale("ro", "RO")

    class VH(val binding: ItemTariffBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTariffBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.tvType.text = item.meterType
        holder.binding.tvDetails.text =
            String.format(ro, "%.2f %s/unit • Fix %.2f %s",
                item.pricePerUnit, item.currency, item.fixedMonthly, item.currency
            )
    }

    override fun getItemCount(): Int = items.size
}
