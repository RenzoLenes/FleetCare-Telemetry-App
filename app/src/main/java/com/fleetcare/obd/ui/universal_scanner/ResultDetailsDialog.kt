package com.fleetcare.obd.ui.universal_scanner

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.fleetcare.obd.domain.model.ScanResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialog para mostrar detalles completos de un resultado de scan.
 */
class ResultDetailsDialog(
    private val result: ScanResult
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val details = buildDetailsText()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Detalles PID ${result.mode}-${result.pid}")
            .setMessage(details)
            .setPositiveButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Copiar") { _, _ ->
                copyToClipboard(details)
            }
            .create()
    }

    private fun buildDetailsText(): String {
        return buildString {
            appendLine("═══════════════════════════")
            appendLine("📊 INFORMACIÓN BÁSICA")
            appendLine("═══════════════════════════")
            appendLine("Mode: ${result.mode}")
            appendLine("PID: ${result.pid}")
            appendLine("Command: ${result.command}")
            appendLine("Estado: ${if (result.success) "✅ Exitoso" else "❌ Fallido"}")
            appendLine()

            appendLine("═══════════════════════════")
            appendLine("📡 RESPUESTA")
            appendLine("═══════════════════════════")
            appendLine("Raw Response: ${result.rawResponse}")

            if (result.dataBytes.isNotEmpty()) {
                appendLine("Data Bytes: ${result.dataBytes.joinToString(" ") { "%02X".format(it) }}")
                appendLine("Byte Count: ${result.byteCount}")
            }

            if (!result.interpretation.isNullOrEmpty()) {
                appendLine("Interpretación: ${result.interpretation}")
            }
            appendLine()

            appendLine("═══════════════════════════")
            appendLine("⏱️ TIMING")
            appendLine("═══════════════════════════")
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            appendLine("Timestamp: ${dateFormat.format(Date(result.timestamp))}")
            appendLine("Latencia: ${result.latencyMs}ms")

            val latencyQuality = when {
                result.latencyMs < 200 -> "Excelente"
                result.latencyMs < 500 -> "Bueno"
                else -> "Lento"
            }
            appendLine("Calidad: $latencyQuality")
            appendLine()

            if (result.metadata != null) {
                appendLine("═══════════════════════════")
                appendLine("📋 METADATA")
                appendLine("═══════════════════════════")
                appendLine("Nombre: ${result.metadata.name ?: "N/A"}")
                appendLine("Descripción: ${result.metadata.description ?: "N/A"}")
                appendLine("Unidad: ${result.metadata.unit}")
                appendLine("Tipo: ${result.metadata.detectedType}")
                appendLine("Longitud: ${result.metadata.responseLength} bytes")
                appendLine("Estándar: ${if (result.metadata.isStandard) "Sí" else "No"}")

                if (result.metadata.formula != null) {
                    appendLine("Fórmula: ${result.metadata.formula}")
                }

                if (result.metadata.minValue != null && result.metadata.maxValue != null) {
                    appendLine("Rango: ${result.metadata.minValue} - ${result.metadata.maxValue}")
                }
            }

            if (!result.success) {
                appendLine()
                appendLine("═══════════════════════════")
                appendLine("❌ ERROR")
                appendLine("═══════════════════════════")
                appendLine("PID no responde o no es soportado")
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Detalles PID", text)
        clipboard.setPrimaryClip(clip)

        android.widget.Toast.makeText(
            requireContext(),
            "Detalles copiados al portapapeles",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        const val TAG = "ResultDetailsDialog"
    }
}
