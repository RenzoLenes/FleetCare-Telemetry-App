package com.fleetcare.obd.utils

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

/**
 * Archivo de funciones de extensión de Kotlin para simplificar código repetitivo.
 *
 * Las extensiones permiten añadir funcionalidad a clases existentes sin heredar de ellas.
 */

// Extensions para View
/**
 * Hace visible una View.
 */
fun View.visible() {
    visibility = View.VISIBLE
}

/**
 * Hace invisible una View (ocupa espacio pero no es visible).
 */
fun View.invisible() {
    visibility = View.INVISIBLE
}

/**
 * Oculta una View (no ocupa espacio).
 */
fun View.gone() {
    visibility = View.GONE
}

/**
 * Cambia la visibilidad de una View basándose en una condición.
 * Si la condición es true, la View se hace visible, si no, se oculta (gone).
 */
fun View.visibleIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}

// Extensions para Context
/**
 * Muestra un Toast corto con el mensaje especificado.
 */
fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

/**
 * Muestra un Toast corto desde un recurso de string.
 */
fun Context.showToast(@StringRes messageRes: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, messageRes, duration).show()
}

/**
 * Oculta el teclado virtual.
 */
fun Context.hideKeyboard(view: View) {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(view.windowToken, 0)
}

// Extensions para Fragment
/**
 * Muestra un Snackbar con el mensaje especificado.
 */
fun Fragment.showSnackbar(
    message: String,
    duration: Int = Snackbar.LENGTH_SHORT,
    actionText: String? = null,
    action: (() -> Unit)? = null
) {
    view?.let { view ->
        val snackbar = Snackbar.make(view, message, duration)
        if (actionText != null && action != null) {
            snackbar.setAction(actionText) { action() }
        }
        snackbar.show()
    }
}

/**
 * Muestra un Snackbar desde un recurso de string.
 */
fun Fragment.showSnackbar(
    @StringRes messageRes: Int,
    duration: Int = Snackbar.LENGTH_SHORT,
    actionText: String? = null,
    action: (() -> Unit)? = null
) {
    view?.let { view ->
        val snackbar = Snackbar.make(view, messageRes, duration)
        if (actionText != null && action != null) {
            snackbar.setAction(actionText) { action() }
        }
        snackbar.show()
    }
}

// Extensions para String
/**
 * Verifica si una cadena es una dirección MAC válida.
 * Formato: XX:XX:XX:XX:XX:XX donde X es un dígito hexadecimal.
 */
fun String.isValidMacAddress(): Boolean {
    val macPattern = "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$".toRegex()
    return macPattern.matches(this)
}

/**
 * Convierte una cadena hexadecimal a un entero.
 * Retorna null si la conversión falla.
 */
fun String.hexToInt(): Int? {
    return try {
        this.toInt(16)
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * Convierte una cadena hexadecimal a un ByteArray.
 */
fun String.hexToByteArray(): ByteArray {
    val cleanHex = this.replace(" ", "").replace("\n", "").replace("\r", "")
    return cleanHex.chunked(2)
        .map { it.toInt(16).toByte() }
        .toByteArray()
}

// Extensions para conversión de unidades
/**
 * Convierte kilómetros por hora a millas por hora.
 */
fun Double.kmhToMph(): Double = this * 0.621371

/**
 * Convierte millas por hora a kilómetros por hora.
 */
fun Double.mphToKmh(): Double = this * 1.60934

/**
 * Convierte Celsius a Fahrenheit.
 */
fun Double.celsiusToFahrenheit(): Double = (this * 9.0 / 5.0) + 32.0

/**
 * Convierte Fahrenheit a Celsius.
 */
fun Double.fahrenheitToCelsius(): Double = (this - 32.0) * 5.0 / 9.0

/**
 * Formatea un número Double a un string con un número específico de decimales.
 */
fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

/**
 * Formatea un número Float a un string con un número específico de decimales.
 */
fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
