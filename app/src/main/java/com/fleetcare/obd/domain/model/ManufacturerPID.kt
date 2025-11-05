package com.fleetcare.obd.domain.model

import com.fleetcare.obd.utils.obd.Mode22Constants

/**
 * Modelo de dominio para PIDs propietarios del fabricante (Modo 22).
 *
 * Sprint 7: Modo 22 y PIDs del Fabricante
 */
data class ManufacturerPID(
    val id: Long = 0,
    val pid: String,                                           // PID de 2 bytes (ej: "1106")
    val manufacturer: String,                                   // Fabricante (GM, Ford, Toyota, etc.)
    val name: String,
    val description: String,
    val dataType: String,                                       // Tipo de dato
    val unit: String,
    val formula: String,                                        // Fórmula de cálculo
    val byteCount: Int,                                         // Número de bytes esperados
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val applicableModels: List<String> = emptyList(),          // Modelos compatibles
    val notes: String = "",
    val isVerified: Boolean = false,                            // Verificado en pruebas reales
    val lastUsed: Long = 0L,                                   // Timestamp última vez usado
    val isEnabled: Boolean = true,                              // Habilitado para uso
    val source: PIDSource = PIDSource.MANUFACTURER              // Origen del PID
) {

    /**
     * Construye el comando completo para este PID.
     */
    fun buildCommand(): String {
        return Mode22Constants.buildMode22Command(pid)
    }

    /**
     * Aplica la fórmula a los bytes de datos recibidos.
     */
    fun applyFormula(bytes: ByteArray): Double? {
        if (bytes.size < byteCount) {
            return null
        }

        return try {
            evaluateFormula(formula, bytes)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Evalúa una fórmula matemática con variables A, B, C, D.
     * Reutiliza la lógica del CustomPID.
     */
    private fun evaluateFormula(formula: String, bytes: ByteArray): Double? {
        if (bytes.isEmpty()) return null

        // Reemplazar variables por valores de bytes
        var expr = formula
        if (bytes.isNotEmpty()) expr = expr.replace("A", bytes[0].toInt().and(0xFF).toString())
        if (bytes.size > 1) expr = expr.replace("B", bytes[1].toInt().and(0xFF).toString())
        if (bytes.size > 2) expr = expr.replace("C", bytes[2].toInt().and(0xFF).toString())
        if (bytes.size > 3) expr = expr.replace("D", bytes[3].toInt().and(0xFF).toString())

        // Evaluar expresión
        return try {
            evaluateExpression(expr)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Evalúa una expresión matemática simple.
     * Soporta: +, -, *, /, paréntesis
     */
    private fun evaluateExpression(expr: String): Double {
        val cleanExpr = expr.replace(" ", "")
        return parseExpression(cleanExpr)
    }

    private fun parseExpression(expr: String): Double {
        val tokens = tokenize(expr)
        return evaluateTokens(tokens)
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var currentToken = ""

        for (char in expr) {
            when (char) {
                in '0'..'9', '.' -> currentToken += char
                '+', '-', '*', '/', '(', ')' -> {
                    if (currentToken.isNotEmpty()) {
                        tokens.add(currentToken)
                        currentToken = ""
                    }
                    tokens.add(char.toString())
                }
            }
        }

        if (currentToken.isNotEmpty()) {
            tokens.add(currentToken)
        }

        return tokens
    }

    private fun evaluateTokens(tokens: List<String>): Double {
        // Implementación simplificada de shunting-yard algorithm
        val output = mutableListOf<Double>()
        val operators = mutableListOf<String>()

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]

            when {
                token.toDoubleOrNull() != null -> {
                    output.add(token.toDouble())
                }
                token == "(" -> {
                    operators.add(token)
                }
                token == ")" -> {
                    while (operators.isNotEmpty() && operators.last() != "(") {
                        applyOperator(output, operators.removeLast())
                    }
                    if (operators.isNotEmpty()) operators.removeLast() // Remove '('
                }
                token in listOf("+", "-", "*", "/") -> {
                    while (operators.isNotEmpty() &&
                        operators.last() != "(" &&
                        precedence(operators.last()) >= precedence(token)
                    ) {
                        applyOperator(output, operators.removeLast())
                    }
                    operators.add(token)
                }
            }
            i++
        }

        while (operators.isNotEmpty()) {
            applyOperator(output, operators.removeLast())
        }

        return output.firstOrNull() ?: 0.0
    }

    private fun applyOperator(output: MutableList<Double>, operator: String) {
        if (output.size < 2) return

        val b = output.removeLast()
        val a = output.removeLast()

        val result = when (operator) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b != 0.0) a / b else 0.0
            else -> 0.0
        }

        output.add(result)
    }

    private fun precedence(operator: String): Int {
        return when (operator) {
            "+", "-" -> 1
            "*", "/" -> 2
            else -> 0
        }
    }

    /**
     * Verifica si el PID es aplicable a un modelo de vehículo.
     */
    fun isApplicableToModel(model: String): Boolean {
        if (applicableModels.isEmpty()) return true
        return applicableModels.any { model.contains(it, ignoreCase = true) }
    }

    /**
     * Obtiene el nombre del fabricante formateado.
     */
    fun getManufacturerName(): String {
        return manufacturer
    }

    /**
     * Verifica si el valor está dentro del rango esperado.
     */
    fun isValueInRange(value: Double): Boolean {
        return when {
            minValue != null && value < minValue -> false
            maxValue != null && value > maxValue -> false
            else -> true
        }
    }

    /**
     * Convierte a CustomPID para guardarlo en la base de datos de PIDs personalizados.
     */
    fun toCustomPID(): CustomPID {
        return CustomPID(
            id = 0, // Nuevo ID
            pid = pid,
            name = "$manufacturer - $name",
            command = buildCommand(),
            formula = formula,
            unit = unit,
            category = determinePIDCategory(),
            vehicleModels = applicableModels,
            discoveryDate = System.currentTimeMillis(),
            lastUsed = lastUsed,
            confidence = if (isVerified) 0.9f else 0.7f,
            source = PIDSource.MANUFACTURER,
            notes = "$description\n\nFabricante: $manufacturer\n$notes",
            isEnabled = isEnabled,
            byteCount = byteCount,
            minValue = minValue,
            maxValue = maxValue
        )
    }

    /**
     * Determina la categoría del PID basándose en el nombre y descripción.
     */
    private fun determinePIDCategory(): PIDCategory {
        val text = "$name $description".lowercase()

        return when {
            text.contains("temp") || text.contains("temperatura") -> PIDCategory.TEMPERATURE
            text.contains("oil") || text.contains("aceite") -> PIDCategory.ENGINE
            text.contains("fuel") || text.contains("combustible") -> PIDCategory.FUEL
            text.contains("transmission") || text.contains("transmisión") -> PIDCategory.TRANSMISSION
            text.contains("battery") || text.contains("batería") || text.contains("volt") -> PIDCategory.ELECTRICAL
            text.contains("dpf") || text.contains("emission") || text.contains("emisión") -> PIDCategory.EMISSION
            text.contains("turbo") || text.contains("boost") -> PIDCategory.ENGINE
            text.contains("hybrid") || text.contains("híbrido") -> PIDCategory.ELECTRICAL
            else -> PIDCategory.PROPRIETARY
        }
    }

    companion object {
        /**
         * Crea un ManufacturerPID desde Mode22PID de las constantes.
         */
        fun fromMode22PID(mode22PID: Mode22Constants.Mode22PID, id: Long = 0): ManufacturerPID {
            return ManufacturerPID(
                id = id,
                pid = mode22PID.pid,
                manufacturer = mode22PID.manufacturer.displayName,
                name = mode22PID.name,
                description = mode22PID.description,
                dataType = mode22PID.dataType.description,
                unit = mode22PID.unit,
                formula = mode22PID.formula,
                byteCount = if (mode22PID.dataType.byteCount > 0) mode22PID.dataType.byteCount else 1,
                minValue = mode22PID.minValue,
                maxValue = mode22PID.maxValue,
                applicableModels = mode22PID.applicableModels,
                notes = mode22PID.notes,
                isVerified = true, // PIDs de las constantes están verificados
                source = PIDSource.MANUFACTURER
            )
        }
    }
}
