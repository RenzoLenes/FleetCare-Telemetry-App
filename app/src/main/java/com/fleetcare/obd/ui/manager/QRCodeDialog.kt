package com.fleetcare.obd.ui.manager

import android.app.Dialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.DialogQrCodeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import android.content.Intent

/**
 * Dialog para mostrar un código QR.
 *
 * Sprint 6.7: QR Code para compartir PIDs personalizados
 */
class QRCodeDialog : DialogFragment() {

    private var _binding: DialogQrCodeBinding? = null
    private val binding get() = _binding!!

    private var qrBitmap: Bitmap? = null
    private var title: String = "Código QR"
    private var description: String = ""
    private var fileName: String = "qr_code"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_FleetCareOBD)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogQrCodeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .create()
    }

    private fun setupUI() {
        // Title and description
        binding.dialogTitle.text = title
        if (description.isNotBlank()) {
            binding.descriptionText.text = description
            binding.descriptionText.visibility = View.VISIBLE
        } else {
            binding.descriptionText.visibility = View.GONE
        }

        // QR Code image
        qrBitmap?.let {
            binding.qrCodeImageView.setImageBitmap(it)
            binding.qrCodeImageView.visibility = View.VISIBLE
            binding.errorText.visibility = View.GONE
        } ?: run {
            binding.qrCodeImageView.visibility = View.GONE
            binding.errorText.visibility = View.VISIBLE
            binding.errorText.text = "Error al generar código QR"
        }

        // Buttons
        binding.closeButton.setOnClickListener {
            dismiss()
        }

        binding.saveButton.setOnClickListener {
            saveQRCode()
        }

        binding.shareButton.setOnClickListener {
            shareQRCode()
        }
    }

    /**
     * Guarda el QR code como imagen en el almacenamiento externo.
     */
    private fun saveQRCode() {
        val bitmap = qrBitmap ?: return

        try {
            // Guardar en caché primero
            val file = File(requireContext().cacheDir, "${fileName}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            Timber.i("QR Code guardado: ${file.absolutePath}")
            showSuccess("QR guardado en caché")

            // Compartir archivo guardado
            shareFile(file)

        } catch (e: Exception) {
            Timber.e(e, "Error al guardar QR Code")
            showError("Error al guardar QR: ${e.message}")
        }
    }

    /**
     * Comparte el QR code como imagen.
     */
    private fun shareQRCode() {
        val bitmap = qrBitmap ?: return

        try {
            // Guardar en caché temporal
            val file = File(requireContext().cacheDir, "${fileName}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            shareFile(file)

        } catch (e: Exception) {
            Timber.e(e, "Error al compartir QR Code")
            showError("Error al compartir QR: ${e.message}")
        }
    }

    /**
     * Comparte un archivo mediante FileProvider.
     */
    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PID Personalizado - FleetCare OBD")
            putExtra(Intent.EXTRA_TEXT, "Escanea este código QR para importar el PID personalizado en FleetCare OBD")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Compartir QR"))
    }

    /**
     * Muestra mensaje de error.
     */
    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(resources.getColor(R.color.md_theme_light_error, null))
            .show()
    }

    /**
     * Muestra mensaje de éxito.
     */
    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(resources.getColor(R.color.md_theme_light_primary, null))
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        qrBitmap?.recycle()
        qrBitmap = null
    }

    companion object {
        /**
         * Crea una instancia del dialog con un código QR.
         *
         * @param bitmap Bitmap del QR code
         * @param title Título del dialog
         * @param description Descripción opcional
         * @param fileName Nombre base del archivo al guardar
         */
        fun newInstance(
            bitmap: Bitmap,
            title: String = "Código QR",
            description: String = "",
            fileName: String = "qr_code"
        ): QRCodeDialog {
            return QRCodeDialog().apply {
                this.qrBitmap = bitmap
                this.title = title
                this.description = description
                this.fileName = fileName
            }
        }
    }
}
