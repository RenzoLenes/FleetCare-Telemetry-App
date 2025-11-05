package com.fleetcare.obd.ui.universal_scanner

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.fleetcare.obd.databinding.DialogQrCodeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.io.FileOutputStream

/**
 * Dialog para mostrar código QR generado.
 */
class QrCodeDisplayDialog(
    private val qrBitmap: Bitmap,
    private val qrData: String
) : DialogFragment() {

    private var _binding: DialogQrCodeBinding? = null
    private val binding get() = _binding!!

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

        // Display QR code
        binding.qrCodeImageView.setImageBitmap(qrBitmap)

        // Hide error text
        binding.errorText.visibility = View.GONE

        // Set title
        binding.dialogTitle.text = "QR Code"

        // Set description with data size
        binding.descriptionText.visibility = View.VISIBLE
        binding.descriptionText.text = "Data size: ${qrData.length} characters"

        // Share button
        binding.shareButton.setOnClickListener {
            shareQrCode()
        }

        // Close button
        binding.closeButton.setOnClickListener {
            dismiss()
        }

        // Hide save button (not needed for this use case)
        binding.saveButton.visibility = View.GONE
    }

    private fun shareQrCode() {
        try {
            // Save QR to temp file
            val cachePath = File(requireContext().cacheDir, "qr_codes")
            cachePath.mkdirs()

            val file = File(cachePath, "qr_code_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            // Get URI
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            // Share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share QR Code"))

        } catch (e: Exception) {
            android.widget.Toast.makeText(
                requireContext(),
                "Error sharing QR code: ${e.message}",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "QrCodeDisplayDialog"
    }
}
