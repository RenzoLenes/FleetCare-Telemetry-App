package com.fleetcare.obd.ui.manager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.fleetcare.obd.databinding.ActivityQrScannerBinding
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import timber.log.Timber

/**
 * Activity para escanear códigos QR de PIDs personalizados.
 *
 * Sprint 6.7: QR Code Scanner para importar PIDs
 */
class QRScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScannerBinding
    private var barcodeView: DecoratedBarcodeView? = null

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
        const val EXTRA_QR_RESULT = "qr_result"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkCameraPermission()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        barcodeView = binding.barcodeScanner
        barcodeView?.decodeContinuous(barcodeCallback)
    }

    private val barcodeCallback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult?) {
            result?.text?.let { qrContent ->
                Timber.d("QR escaneado: ${qrContent.take(100)}...")
                handleQRResult(qrContent)
            }
        }
    }

    /**
     * Maneja el resultado del escaneo del QR.
     */
    private fun handleQRResult(qrContent: String) {
        // Detener escaneo
        barcodeView?.pause()

        // Validar que sea un JSON válido
        if (!qrContent.trim().startsWith("{") && !qrContent.trim().startsWith("[")) {
            showError("El código QR no contiene un PID válido")
            resumeScanning()
            return
        }

        // Devolver resultado a la actividad que llamó
        val intent = intent
        intent.putExtra(EXTRA_QR_RESULT, qrContent)
        setResult(RESULT_OK, intent)
        finish()
    }

    /**
     * Verifica y solicita permiso de cámara.
     */
    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Solicitar permiso
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        } else {
            // Permiso ya otorgado, iniciar escaneo
            startScanning()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning()
            } else {
                showError("Se requiere permiso de cámara para escanear códigos QR")
                finish()
            }
        }
    }

    private fun startScanning() {
        barcodeView?.resume()
    }

    private fun resumeScanning() {
        binding.root.postDelayed({
            barcodeView?.resume()
        }, 2000)
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        barcodeView?.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        barcodeView?.pause()
    }
}
