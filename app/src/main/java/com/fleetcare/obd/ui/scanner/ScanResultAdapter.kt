package com.fleetcare.obd.ui.scanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.ItemScanResultBinding
import com.fleetcare.obd.domain.model.ScanResult

/**
 * Adapter para mostrar resultados del escaneo de PIDs.
 *
 * Sprint 5: Escáner de PIDs Completo
 *
 * Muestra cada resultado del escaneo en una card con:
 * - PID en hex y decimal
 * - Categoría
 * - Interpretación o descripción
 * - Respuesta RAW
 * - Latencia y conteo de bytes
 * - Indicador de éxito/fallo con color
 */
class ScanResultAdapter(
    private val onItemClick: (ScanResult) -> Unit
) : ListAdapter<ScanResult, ScanResultAdapter.ScanResultViewHolder>(ScanResultDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanResultViewHolder {
        val binding = ItemScanResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ScanResultViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ScanResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ScanResultViewHolder(
        private val binding: ItemScanResultBinding,
        private val onItemClick: (ScanResult) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: ScanResult) {
            // PID en hex
            binding.pidText.text = "PID ${result.pid}"

            // PID en decimal
            binding.pidDecimalText.text = "(${result.getPIDDecimal()})"

            // Categoría
            binding.categoryChip.text = result.getCategory()

            // Comando
            binding.commandText.text = "Command: ${result.command}"

            // Interpretación o descripción
            binding.interpretationText.text = result.getDescription()

            // Respuesta RAW
            binding.rawResponseText.text = "Response: ${result.rawResponse}"

            // Latencia
            binding.latencyText.text = "${result.latencyMs}ms"

            // Conteo de bytes
            val bytesText = if (result.byteCount == 1) "1 byte" else "${result.byteCount} bytes"
            binding.byteCountText.text = bytesText

            // Chip "Estándar" visible solo para PIDs estándar exitosos
            binding.standardChip.visibility = if (result.isStandardPID && result.success) {
                View.VISIBLE
            } else {
                View.GONE
            }

            // Indicador de éxito/fallo con color
            val statusColor = if (result.success) {
                ContextCompat.getColor(binding.root.context, R.color.md_theme_light_primary)
            } else {
                ContextCompat.getColor(binding.root.context, R.color.md_theme_light_error)
            }
            binding.statusIndicator.setBackgroundColor(statusColor)

            // Color de fondo según éxito/fallo
            if (!result.success) {
                binding.root.setCardBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.md_theme_light_errorContainer)
                )
                binding.interpretationText.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.md_theme_light_onErrorContainer)
                )
            } else {
                binding.root.setCardBackgroundColor(
                    ContextCompat.getColor(binding.root.context, R.color.md_theme_light_surface)
                )
                binding.interpretationText.setTextColor(
                    ContextCompat.getColor(binding.root.context, R.color.md_theme_light_onSurface)
                )
            }

            // Click listener
            binding.root.setOnClickListener {
                onItemClick(result)
            }
        }
    }

    private class ScanResultDiffCallback : DiffUtil.ItemCallback<ScanResult>() {
        override fun areItemsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean {
            return oldItem.pid == newItem.pid
        }

        override fun areContentsTheSame(oldItem: ScanResult, newItem: ScanResult): Boolean {
            return oldItem == newItem
        }
    }
}
