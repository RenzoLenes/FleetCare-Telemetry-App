package com.fleetcare.obd.ui.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fleetcare.obd.databinding.ItemSupportedPidBinding
import com.fleetcare.obd.domain.model.PIDRangeCategory

/**
 * Adapter para mostrar PIDs soportados agrupados por categoría.
 *
 * Sprint 2: UI de PIDs soportados
 */
class SupportedPIDsAdapter : ListAdapter<PIDCategoryItem, SupportedPIDsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSupportedPidBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemSupportedPidBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PIDCategoryItem) {
            binding.categoryNameText.text = item.categoryName
            binding.pidCountText.text = "${item.pidCount} PIDs"
            binding.pidListText.text = item.pidListFormatted
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<PIDCategoryItem>() {
        override fun areItemsTheSame(oldItem: PIDCategoryItem, newItem: PIDCategoryItem): Boolean {
            return oldItem.category == newItem.category
        }

        override fun areContentsTheSame(oldItem: PIDCategoryItem, newItem: PIDCategoryItem): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * Item de categoría de PIDs para mostrar en la lista.
 *
 * Usa PIDRangeCategory (clasificación por rango hex)
 * en lugar de PIDCategory (clasificación por función).
 */
data class PIDCategoryItem(
    val category: PIDRangeCategory,
    val categoryName: String,
    val pidCount: Int,
    val pidListFormatted: String
)
