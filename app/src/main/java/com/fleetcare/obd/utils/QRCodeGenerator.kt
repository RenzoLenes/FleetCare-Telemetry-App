package com.fleetcare.obd.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import timber.log.Timber

/**
 * Utilidad para generar códigos QR.
 *
 * Sprint 6.7: QR Code para compartir PIDs personalizados
 */
object QRCodeGenerator {

    /**
     * Genera un QR code a partir de un texto.
     *
     * @param content Contenido del QR (JSON, URL, texto, etc.)
     * @param size Tamaño del QR en píxeles (cuadrado)
     * @param errorCorrectionLevel Nivel de corrección de errores
     * @return Bitmap con el QR generado o null si hay error
     */
    fun generateQRCode(
        content: String,
        size: Int = 512,
        errorCorrectionLevel: ErrorCorrectionLevel = ErrorCorrectionLevel.M
    ): Result<Bitmap> {
        return try {
            if (content.isBlank()) {
                return Result.failure(IllegalArgumentException("El contenido no puede estar vacío"))
            }

            if (size < 100 || size > 2048) {
                return Result.failure(IllegalArgumentException("El tamaño debe estar entre 100 y 2048 píxeles"))
            }

            // Configurar hints para la generación del QR
            val hints = hashMapOf<EncodeHintType, Any>(
                EncodeHintType.ERROR_CORRECTION to errorCorrectionLevel,
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1 // Margen mínimo para ahorrar espacio
            )

            // Generar matriz de bits del QR
            val qrCodeWriter = QRCodeWriter()
            val bitMatrix = qrCodeWriter.encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )

            // Convertir matriz de bits a Bitmap
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

            Timber.d("QR Code generado exitosamente (${content.length} caracteres, $size px)")
            Result.success(bitmap)

        } catch (e: Exception) {
            Timber.e(e, "Error al generar QR Code")
            Result.failure(e)
        }
    }

    /**
     * Genera un QR code con nivel de corrección adaptativo basado en el tamaño del contenido.
     *
     * - Contenido pequeño (<100 chars): ErrorCorrectionLevel.H (30% redundancia)
     * - Contenido mediano (100-500 chars): ErrorCorrectionLevel.M (15% redundancia)
     * - Contenido grande (>500 chars): ErrorCorrectionLevel.L (7% redundancia)
     *
     * @param content Contenido del QR
     * @param size Tamaño del QR en píxeles
     * @return Bitmap con el QR generado o null si hay error
     */
    fun generateAdaptiveQRCode(
        content: String,
        size: Int = 512
    ): Result<Bitmap> {
        val errorCorrectionLevel = when {
            content.length < 100 -> ErrorCorrectionLevel.H
            content.length < 500 -> ErrorCorrectionLevel.M
            else -> ErrorCorrectionLevel.L
        }

        return generateQRCode(content, size, errorCorrectionLevel)
    }

    /**
     * Genera un QR code optimizado para PIDs personalizados.
     *
     * Utiliza tamaño 512px y nivel de corrección adaptativo.
     *
     * @param pidJson JSON del PID personalizado
     * @return Bitmap con el QR generado
     */
    fun generatePIDQRCode(pidJson: String): Result<Bitmap> {
        return generateAdaptiveQRCode(pidJson, size = 512)
    }

    /**
     * Calcula el tamaño recomendado del QR según la longitud del contenido.
     *
     * @param contentLength Longitud del contenido en caracteres
     * @return Tamaño recomendado en píxeles
     */
    fun getRecommendedSize(contentLength: Int): Int {
        return when {
            contentLength < 100 -> 256
            contentLength < 300 -> 384
            contentLength < 500 -> 512
            contentLength < 1000 -> 768
            else -> 1024
        }
    }

    /**
     * Valida si un contenido puede ser codificado en un QR Code.
     *
     * El límite práctico es ~2950 caracteres para QR Code versión 40.
     *
     * @param content Contenido a validar
     * @return true si el contenido puede ser codificado
     */
    fun canEncode(content: String): Boolean {
        return content.length <= 2950
    }

    /**
     * Estima el nivel de corrección de errores óptimo según el tamaño del contenido.
     *
     * @param contentLength Longitud del contenido
     * @return Nivel de corrección recomendado
     */
    fun getRecommendedErrorCorrection(contentLength: Int): ErrorCorrectionLevel {
        return when {
            contentLength < 100 -> ErrorCorrectionLevel.H // 30% redundancia
            contentLength < 500 -> ErrorCorrectionLevel.M // 15% redundancia
            contentLength < 1500 -> ErrorCorrectionLevel.L // 7% redundancia
            else -> ErrorCorrectionLevel.L
        }
    }
}
