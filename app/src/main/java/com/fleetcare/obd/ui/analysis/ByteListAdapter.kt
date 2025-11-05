package com.fleetcare.obd.ui.analysis

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.ItemByteDisplayBinding
import com.fleetcare.obd.domain.model.ByteStatistic

/**
 * Adapter para mostrar bytes individuales con color coding.
 *
 * Sprint 4: UI de Análisis de Bytes
 *
 * Color coding:
 * - Verde: Bytes dinámicos (variables)
 * - Gris: Bytes estáticos (constantes)
 * - Rojo: Bytes anómalos (outliers)
 */
class ByteListAdapter(
    private val onByteClick: (ByteDisplayItem) -> Unit
) : ListAdapter<ByteDisplayItem, ByteListAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemByteDisplayBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onByteClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemByteDisplayBinding,
        private val onByteClick: (ByteDisplayItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ByteDisplayItem) {
            // Índice del byte
            binding.byteIndexText.text = "[${item.index}]"

            // Valor hexadecimal
            binding.byteHexText.text = "%02X".format(item.value)

            // Valor decimal
            binding.byteDecText.text = "(${item.value})"

            // Color coding según tipo
            val context = binding.root.context
            when (item.type) {
                ByteType.STATIC -> {
                    // Gris para bytes estáticos
                    binding.byteCard.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.md_theme_light_surfaceVariant)
                    )
                    binding.byteTypeIndicator.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.md_theme_light_outline)
                    )
                    binding.byteHexText.setTextColor(
                        ContextCompat.getColor(context, R.color.md_theme_light_onSurfaceVariant)
                    )
                }
                ByteType.DYNAMIC -> {
                    // Verde para bytes dinámicos
                    binding.byteCard.setCardBackgroundColor(Color.WHITE)
                    binding.byteTypeIndicator.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.md_theme_light_primary)
                    )
                    binding.byteHexText.setTextColor(
                        ContextCompat.getColor(context, R.color.md_theme_light_primary)
                    )
                }
                ByteType.ANOMALOUS -> {
                    // Rojo para bytes anómalos
                    binding.byteCard.setCardBackgroundColor(Color.WHITE)
                    binding.byteTypeIndicator.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.md_theme_light_error)
                    )
                    binding.byteHexText.setTextColor(
                        ContextCompat.getColor(context, R.color.md_theme_light_error)
                    )
                }
            }

            // Resaltar si está seleccionado
            if (item.isSelected) {
                binding.byteCard.strokeWidth = 4
                binding.byteCard.strokeColor = ContextCompat.getColor(context, R.color.md_theme_light_secondary)
            } else {
                binding.byteCard.strokeWidth = 0
            }

            // Click listener
            binding.root.setOnClickListener {
                onByteClick(item)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ByteDisplayItem>() {
        override fun areItemsTheSame(oldItem: ByteDisplayItem, newItem: ByteDisplayItem): Boolean {
            return oldItem.index == newItem.index
        }

        override fun areContentsTheSame(oldItem: ByteDisplayItem, newItem: ByteDisplayItem): Boolean {
            return oldItem == newItem
        }
    }
}

/**
 * Item de byte para mostrar en la lista.
 */
data class ByteDisplayItem(
    val index: Int,
    val value: Int,
    val type: ByteType,
    val isSelected: Boolean = false,
    val statistic: ByteStatistic? = null
) {
    /**
     * Genera descripción detallada para tooltip o diálogo.
     */
    fun toDetailString(): String {
        return buildString {
            appendLine("Byte [$index]")
            appendLine("Hexadecimal: 0x%02X".format(value))
            appendLine("Decimal: $value")
            appendLine("Binario: ${value.toString(2).padStart(8, '0')}")
            appendLine("ASCII: ${if (value in 32..126) value.toChar() else "?"}")
            appendLine()
            appendLine("Tipo: ${type.displayName}")

            statistic?.let { stat ->
                appendLine()
                appendLine("=== Estadísticas ===")
                appendLine("Min: ${stat.min}")
                appendLine("Max: ${stat.max}")
                appendLine("Media: ${"%.2f".format(stat.mean)}")
                appendLine("Mediana: ${"%.2f".format(stat.median)}")
                appendLine("Desv. Est: ${"%.2f".format(stat.stdDev)}")
                appendLine("Varianza: ${"%.2f".format(stat.variance)}")
                appendLine("Valores únicos: ${stat.uniqueValues}")
                appendLine("Más común: ${stat.mostCommonValue}")
            }
        }
    }
}

/**
 * Tipos de bytes según su comportamiento.
 */
enum class ByteType(val displayName: String) {
    STATIC("Estático"),
    DYNAMIC("Dinámico"),
    ANOMALOUS("Anómalo")
}
