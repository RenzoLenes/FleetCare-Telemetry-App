package com.fleetcare.obd.ui.manager

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.ItemCustomPidBinding
import com.fleetcare.obd.domain.model.CustomPID

/**
 * Adapter para la lista de PIDs personalizados.
 *
 * Sprint 6: Gestión de PIDs Personalizados
 */
class CustomPIDAdapter(
    private val onItemClick: (CustomPID) -> Unit,
    private val onToggleEnabled: (CustomPID) -> Unit,
    private val onEdit: (CustomPID) -> Unit,
    private val onShare: (CustomPID) -> Unit,
    private val onShareQR: (CustomPID) -> Unit,
    private val onDelete: (CustomPID) -> Unit
) : ListAdapter<CustomPID, CustomPIDAdapter.CustomPIDViewHolder>(CustomPIDDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomPIDViewHolder {
        val binding = ItemCustomPidBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CustomPIDViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CustomPIDViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CustomPIDViewHolder(
        private val binding: ItemCustomPidBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(customPID: CustomPID) {
            // Name and PID
            binding.pidNameText.text = customPID.name
            binding.pidHexText.text = "PID ${customPID.pid.uppercase()}"
            binding.commandText.text = customPID.command.uppercase()

            // Formula
            binding.formulaText.text = customPID.formula

            // Category
            binding.categoryChip.text = customPID.getCategoryName()
            binding.categoryChip.chipBackgroundColor = ContextCompat.getColorStateList(
                binding.root.context,
                when (customPID.category.name) {
                    "ENGINE" -> R.color.md_theme_light_primaryContainer
                    "FUEL" -> R.color.md_theme_light_secondaryContainer
                    "TEMPERATURE" -> R.color.md_theme_light_tertiaryContainer
                    else -> R.color.md_theme_light_surfaceVariant
                }
            )

            // Source
            binding.sourceChip.text = customPID.getSourceName()

            // Confidence
            binding.confidenceChip.text = "Confianza: ${customPID.getConfidenceLevel()}"
            binding.confidenceChip.chipBackgroundColor = ContextCompat.getColorStateList(
                binding.root.context,
                when {
                    customPID.confidence >= 0.8f -> R.color.md_theme_light_primaryContainer
                    customPID.confidence >= 0.5f -> R.color.md_theme_light_secondaryContainer
                    else -> R.color.md_theme_light_errorContainer
                }
            )

            // Unit and Byte Count
            binding.unitText.text = customPID.unit
            binding.byteCountText.text = customPID.byteCount.toString()

            // Notes
            if (customPID.notes.isNotBlank()) {
                binding.notesText.visibility = View.VISIBLE
                binding.notesText.text = customPID.notes
            } else {
                binding.notesText.visibility = View.GONE
            }

            // Enabled switch
            binding.enabledSwitch.isChecked = customPID.isEnabled
            binding.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked != customPID.isEnabled) {
                    onToggleEnabled(customPID)
                }
            }

            // Card click
            binding.root.setOnClickListener {
                onItemClick(customPID)
            }

            // Action buttons
            binding.editButton.setOnClickListener {
                onEdit(customPID)
            }

            binding.shareButton.setOnClickListener {
                onShare(customPID)
            }

            // Sprint 6.7: QR Code button
            binding.shareQrButton.setOnClickListener {
                onShareQR(customPID)
            }

            binding.deleteButton.setOnClickListener {
                onDelete(customPID)
            }

            // Visual feedback for disabled PIDs
            binding.root.alpha = if (customPID.isEnabled) 1.0f else 0.6f
        }
    }
}

/**
 * DiffUtil callback para optimizar actualizaciones de la lista.
 */
class CustomPIDDiffCallback : DiffUtil.ItemCallback<CustomPID>() {
    override fun areItemsTheSame(oldItem: CustomPID, newItem: CustomPID): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: CustomPID, newItem: CustomPID): Boolean {
        return oldItem == newItem
    }
}
