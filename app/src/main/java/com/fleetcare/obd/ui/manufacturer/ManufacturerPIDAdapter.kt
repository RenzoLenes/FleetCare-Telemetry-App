package com.fleetcare.obd.ui.manufacturer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.ItemManufacturerPidBinding
import com.fleetcare.obd.domain.model.ManufacturerPID

/**
 * Adapter para la lista de PIDs del fabricante.
 *
 * Sprint 7: Modo 22 y PIDs del Fabricante
 */
class ManufacturerPIDAdapter(
    private val onItemClick: (ManufacturerPID) -> Unit,
    private val onTest: (ManufacturerPID) -> Unit,
    private val onSave: (ManufacturerPID) -> Unit
) : ListAdapter<ManufacturerPID, ManufacturerPIDAdapter.ManufacturerPIDViewHolder>(ManufacturerPIDDiffCallback()) {

    private val testResults = mutableMapOf<String, ManufacturerPIDsViewModel.TestResult>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManufacturerPIDViewHolder {
        val binding = ItemManufacturerPidBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ManufacturerPIDViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ManufacturerPIDViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * Actualiza los resultados de las pruebas.
     */
    fun updateTestResults(results: Map<String, ManufacturerPIDsViewModel.TestResult>) {
        testResults.clear()
        testResults.putAll(results)
        notifyDataSetChanged()
    }

    inner class ManufacturerPIDViewHolder(
        private val binding: ItemManufacturerPidBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(pid: ManufacturerPID) {
            // Name and PID
            binding.pidNameText.text = pid.name
            binding.pidHexText.text = "PID ${pid.pid.uppercase()}"
            binding.commandText.text = pid.buildCommand()

            // Manufacturer
            binding.manufacturerChip.text = pid.manufacturer

            // Description
            binding.descriptionText.text = pid.description

            // Unit and byte count
            binding.unitText.text = pid.unit
            binding.byteCountText.text = "${pid.byteCount} bytes"

            // Verified badge
            if (pid.isVerified) {
                binding.verifiedBadge.visibility = View.VISIBLE
            } else {
                binding.verifiedBadge.visibility = View.GONE
            }

            // Test result (if available)
            val testResult = testResults[pid.pid]
            if (testResult != null) {
                binding.testResultCard.visibility = View.VISIBLE

                if (testResult.success && testResult.value != null) {
                    binding.testResultText.text = "${String.format("%.2f", testResult.value)} ${testResult.unit}"
                    binding.testResultText.setTextColor(
                        ContextCompat.getColor(binding.root.context, R.color.md_theme_light_primary)
                    )
                    binding.testResultIcon.setImageResource(android.R.drawable.ic_menu_info_details)
                } else {
                    binding.testResultText.text = testResult.error ?: "Error"
                    binding.testResultText.setTextColor(
                        ContextCompat.getColor(binding.root.context, R.color.md_theme_light_error)
                    )
                    binding.testResultIcon.setImageResource(android.R.drawable.ic_dialog_alert)
                }
            } else {
                binding.testResultCard.visibility = View.GONE
            }

            // Applicable models (if any)
            if (pid.applicableModels.isNotEmpty()) {
                binding.applicableModelsText.visibility = View.VISIBLE
                binding.applicableModelsText.text = "Compatible: ${pid.applicableModels.joinToString(", ")}"
            } else {
                binding.applicableModelsText.visibility = View.GONE
            }

            // Card click
            binding.root.setOnClickListener {
                onItemClick(pid)
            }

            // Action buttons
            binding.testButton.setOnClickListener {
                onTest(pid)
            }

            binding.saveButton.setOnClickListener {
                onSave(pid)
            }
        }
    }
}

/**
 * DiffUtil callback para optimizar actualizaciones de la lista.
 */
class ManufacturerPIDDiffCallback : DiffUtil.ItemCallback<ManufacturerPID>() {
    override fun areItemsTheSame(oldItem: ManufacturerPID, newItem: ManufacturerPID): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ManufacturerPID, newItem: ManufacturerPID): Boolean {
        return oldItem == newItem
    }
}
