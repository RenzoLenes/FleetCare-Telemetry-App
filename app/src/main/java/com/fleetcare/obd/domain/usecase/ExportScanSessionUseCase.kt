package com.fleetcare.obd.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.fleetcare.obd.data.export.ScanResultExporter
import com.fleetcare.obd.domain.model.ExportFormat
import com.fleetcare.obd.domain.model.ScanSession
import com.fleetcare.obd.utils.QRCodeGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Caso de uso para exportar sesiones de scan a diferentes formatos.
 */
class ExportScanSessionUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exporter: ScanResultExporter
) {

    /**
     * Exporta una sesión de scan al formato especificado y retorna el URI del archivo.
     */
    suspend fun execute(session: ScanSession, format: ExportFormat): Result<ExportResult> = withContext(Dispatchers.IO) {
        try {
            when (format) {
                ExportFormat.JSON, ExportFormat.CSV -> exportToFile(session, format)
                ExportFormat.QR_CODE -> exportToQrCode(session)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error al exportar sesión de scan")
            Result.failure(e)
        }
    }

    /**
     * Exporta a archivo (JSON o CSV).
     */
    private suspend fun exportToFile(session: ScanSession, format: ExportFormat): Result<ExportResult> {
        val content = exporter.exportSession(session, format)
        val fileName = exporter.getSuggestedFileName(session, format)
        val mimeType = exporter.getMimeType(format)

        // Crear directorio de exportaciones si no existe
        val exportsDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }

        // Escribir archivo
        val file = File(exportsDir, fileName)
        file.writeText(content)

        // Obtener URI usando FileProvider
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        Timber.d("Sesión exportada a $format: ${file.absolutePath}")

        return Result.success(
            ExportResult.FileExport(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                filePath = file.absolutePath
            )
        )
    }

    /**
     * Exporta a código QR.
     */
    private suspend fun exportToQrCode(session: ScanSession): Result<ExportResult> = withContext(Dispatchers.Default) {
        val qrData = exporter.exportSession(session, ExportFormat.QR_CODE)

        // Validar que el contenido no sea muy grande para QR
        if (!QRCodeGenerator.canEncode(qrData)) {
            return@withContext Result.failure(
                IllegalArgumentException("El contenido es muy grande para un código QR (máx. 2950 caracteres)")
            )
        }

        // Generar QR code
        val qrCodeResult = QRCodeGenerator.generateAdaptiveQRCode(
            content = qrData,
            size = QRCodeGenerator.getRecommendedSize(qrData.length)
        )

        if (qrCodeResult.isFailure) {
            return@withContext Result.failure(qrCodeResult.exceptionOrNull()!!)
        }

        val bitmap = qrCodeResult.getOrNull()!!

        // Guardar QR como imagen
        val fileName = "qr_${session.vehicleId}_${System.currentTimeMillis()}.png"
        val exportsDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }

        val file = File(exportsDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Obtener URI usando FileProvider
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        Timber.d("QR Code generado: ${file.absolutePath}")

        Result.success(
            ExportResult.QrCodeExport(
                uri = uri,
                fileName = fileName,
                bitmap = bitmap,
                filePath = file.absolutePath,
                qrData = qrData
            )
        )
    }
}

/**
 * Resultado de exportación.
 */
sealed class ExportResult {
    /**
     * Exportación a archivo (JSON o CSV).
     */
    data class FileExport(
        val uri: android.net.Uri,
        val fileName: String,
        val mimeType: String,
        val filePath: String
    ) : ExportResult()

    /**
     * Exportación a código QR.
     */
    data class QrCodeExport(
        val uri: android.net.Uri,
        val fileName: String,
        val bitmap: Bitmap,
        val filePath: String,
        val qrData: String
    ) : ExportResult()
}
