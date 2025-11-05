package com.fleetcare.obd.ui.universal_scanner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.ItemScanResultBinding
import com.fleetcare.obd.domain.model.ScanResult

/**
 * Adapter para mostrar resultados de escaneo en RecyclerView.
 */
class ScanResultsAdapter(
    private val onItemClick: (ScanResult) -> Unit
) : ListAdapter<ScanResult, ScanResultsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScanResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemScanResultBinding,
        private val onItemClick: (ScanResult) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: ScanResult) {
            binding.apply {
                // PID info
                pidText.text = "PID: ${result.pid}"
                pidDecimalText.text = "(${result.getPIDDecimal()})"
                commandText.text = "Command: ${result.command}"

                // Interpretation/Name
                interpretationText.text = result.interpretation
                    ?: result.metadata?.name
                    ?: "Unknown PID"

                // Raw Response
                rawResponseText.text = "Response: ${result.rawResponse}"

                // Category Chip
                result.metadata?.let { metadata ->
                    categoryChip.text = result.getCategory()
                    categoryChip.visibility = android.view.View.VISIBLE
                } ?: run {
                    categoryChip.visibility = android.view.View.GONE
                }

                // Success indicator
                if (result.success) {
                    statusIndicator.setImageResource(R.drawable.ic_check_circle)
                    statusIndicator.setColorFilter(
                        ContextCompat.getColor(root.context, R.color.success_green)
                    )
                    byteCountText.text = "${result.byteCount} bytes"
                } else {
                    statusIndicator.setImageResource(R.drawable.ic_error)
                    statusIndicator.setColorFilter(
                        ContextCompat.getColor(root.context, R.color.error_red)
                    )
                    byteCountText.text = "NO DATA"
                }

                // Latency
                latencyText.text = "${result.latencyMs}ms"

                // Latency color (green < 200ms, yellow < 500ms, red >= 500ms)
                val timeColor = when {
                    result.latencyMs < 200 -> R.color.success_green
                    result.latencyMs < 500 -> R.color.warning_yellow
                    else -> R.color.error_red
                }
                latencyText.setTextColor(
                    ContextCompat.getColor(root.context, timeColor)
                )

                // Standard PID Chip
                if (result.isStandardPID) {
                    standardChip.visibility = android.view.View.VISIBLE
                } else {
                    standardChip.visibility = android.view.View.GONE
                }

                // Click listener
                root.setOnClickListener {
                    onItemClick(result)
                }
            }
        }

    }

    class DiffCallback : DiffUtil.ItemCallback<ScanResult>() {
        override fun areItemsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean {
            return oldItem.getUniqueId() == newItem.getUniqueId() &&
                   oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean {
            return oldItem == newItem
        }
    }
}
