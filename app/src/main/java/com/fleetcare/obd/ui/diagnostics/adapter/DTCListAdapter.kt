package com.fleetcare.obd.ui.diagnostics.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fleetcare.obd.databinding.ItemDtcBinding
import com.fleetcare.obd.domain.model.DiagnosticTroubleCode

/**
 * Adapter para mostrar la lista de códigos de diagnóstico (DTCs).
 */
class DTCListAdapter : ListAdapter<DiagnosticTroubleCode, DTCListAdapter.DTCViewHolder>(DTCDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DTCViewHolder {
        val binding = ItemDtcBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DTCViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DTCViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DTCViewHolder(
        private val binding: ItemDtcBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(dtc: DiagnosticTroubleCode) {
            binding.dtcCode.text = dtc.code
            binding.dtcDescription.text = dtc.description
        }
    }

    private class DTCDiffCallback : DiffUtil.ItemCallback<DiagnosticTroubleCode>() {
        override fun areItemsTheSame(
            oldItem: DiagnosticTroubleCode,
            newItem: DiagnosticTroubleCode
        ): Boolean {
            return oldItem.code == newItem.code
        }

        override fun areContentsTheSame(
            oldItem: DiagnosticTroubleCode,
            newItem: DiagnosticTroubleCode
        ): Boolean {
            return oldItem == newItem
        }
    }
}
