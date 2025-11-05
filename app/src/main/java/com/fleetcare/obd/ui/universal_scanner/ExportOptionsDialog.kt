package com.fleetcare.obd.ui.universal_scanner

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.fleetcare.obd.R
import com.fleetcare.obd.domain.model.ExportFormat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialog para seleccionar formato de exportación.
 */
class ExportOptionsDialog(
    private val onFormatSelected: (ExportFormat) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val options = arrayOf(
            "JSON - Formato completo con metadata",
            "CSV - Tabla simple para Excel",
            "QR Code - Compartir PIDs descubiertos"
        )

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Exportar Resultados")
            .setItems(options) { _, which ->
                val format = when (which) {
                    0 -> ExportFormat.JSON
                    1 -> ExportFormat.CSV
                    2 -> ExportFormat.QR_CODE
                    else -> ExportFormat.JSON
                }
                onFormatSelected(format)
                dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }

    companion object {
        const val TAG = "ExportOptionsDialog"
    }
}
