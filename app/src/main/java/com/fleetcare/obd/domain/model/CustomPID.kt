package com.fleetcare.obd.domain.model

/**
 * Modelo de dominio para PIDs personalizados.
 *
 * Sprint 6: Gestión de PIDs Personalizados
 *
 * Representa un PID descubierto o personalizado por el usuario,
 * con su fórmula de decodificación asociada.
 */
data class CustomPID(
    val id: Long = 0, // 0 para nuevos PIDs
    val pid: String, // Hex: "0C", "2F", "F123", etc.
    val name: String, // "RPM Motor", "Nivel de Combustible Personalizado"
    val command: String, // "010C", "012F", "22F123"
    val formula: String, // Expresión: "(A * 256 + B) / 4", "A - 40"
    val unit: String, // "RPM", "km/h", "°C", "%", "V"
    val category: PIDCategory,
    val vehicleModels: List<String> = emptyList(), // VINs compatibles
    val discoveryDate: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis(),
    val confidence: Float = 0.5f, // 0.0-1.0
    val source: PIDSource = PIDSource.USER,
    val notes: String = "",
    val isEnabled: Boolean = true,
    val byteCount: Int = 1, // Número de bytes esperados en respuesta
    val minValue: Double? = null, // Rango esperado (opcional)
    val maxValue: Double? = null
) {
    /**
     * Aplica la fórmula a un array de bytes.
     */
    fun applyFormula(bytes: ByteArray): Double? {
        if (bytes.size < byteCount) return null

        return try {
            evaluateFormula(formula, bytes)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Valida que el PID esté correctamente configurado.
     */
    fun isValid(): Boolean {
        return pid.isNotBlank() &&
                name.isNotBlank() &&
                command.isNotBlank() &&
                formula.isNotBlank() &&
                unit.isNotBlank() &&
                byteCount > 0 &&
                confidence in 0.0f..1.0f
    }

    /**
     * Verifica si este PID es compatible con un vehículo específico.
     */
    fun isCompatibleWithVehicle(vin: String): Boolean {
        if (vehicleModels.isEmpty()) return true // Compatible con todos
        return vehicleModels.any { it == vin || vin.startsWith(it.take(11)) } // Match por VIN completo o WMI
    }

    /**
     * Obtiene el modo OBD (01, 22, etc.)
     */
    fun getMode(): String {
        return if (command.length >= 2) command.take(2) else "01"
    }

    /**
     * Obtiene la categoría legible.
     */
    fun getCategoryName(): String {
        return category.displayName
    }

    /**
     * Obtiene el origen legible.
     */
    fun getSourceName(): String {
        return when (source) {
            PIDSource.USER -> "Usuario"
            PIDSource.AUTO_DETECTED -> "Auto-detectado"
            PIDSource.COMMUNITY -> "Comunidad"
            PIDSource.MANUFACTURER -> "Fabricante"
            PIDSource.IMPORTED -> "Importado"
        }
    }

    /**
     * Obtiene nivel de confianza legible.
     */
    fun getConfidenceLevel(): String {
        return when {
            confidence >= 0.9f -> "Muy Alta"
            confidence >= 0.7f -> "Alta"
            confidence >= 0.5f -> "Media"
            confidence >= 0.3f -> "Baja"
            else -> "Muy Baja"
        }
    }

    /**
     * Convierte a mapa para JSON.
     */
    fun toJsonMap(): Map<String, Any?> {
        return mapOf(
            "pid" to pid,
            "name" to name,
            "command" to command,
            "formula" to formula,
            "unit" to unit,
            "category" to category.name,
            "vehicleModels" to vehicleModels,
            "discoveryDate" to discoveryDate,
            "confidence" to confidence,
            "source" to source.name,
            "notes" to notes,
            "byteCount" to byteCount,
            "minValue" to minValue,
            "maxValue" to maxValue
        )
    }

    /**
     * Evaluador simple de fórmulas.
     */
    private fun evaluateFormula(expression: String, bytes: ByteArray): Double? {
        var expr = expression

        // Reemplazar variables A, B, C, D por valores de bytes
        for (i in bytes.indices) {
            val variable = ('A' + i).toString()
            val value = bytes[i].toUByte().toInt()
            expr = expr.replace(variable, value.toString())
        }

        // Evaluar expresión matemática simple
        return try {
            evaluateExpression(expr)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Evalúa una expresión matemática simple.
     * Soporta: +, -, *, /, (, ), números decimales.
     */
    private fun evaluateExpression(expr: String): Double {
        // Eliminar espacios
        val cleanExpr = expr.replace(" ", "")

        // Evaluar usando algoritmo de shunting-yard simplificado
        val output = mutableListOf<Double>()
        val operators = mutableListOf<Char>()
        var i = 0

        while (i < cleanExpr.length) {
            val char = cleanExpr[i]

            when {
                char.isDigit() || char == '.' -> {
                    // Leer número completo
                    var numStr = ""
                    while (i < cleanExpr.length && (cleanExpr[i].isDigit() || cleanExpr[i] == '.')) {
                        numStr += cleanExpr[i]
                        i++
                    }
                    output.add(numStr.toDouble())
                    continue
                }
                char == '(' -> {
                    operators.add(char)
                }
                char == ')' -> {
                    while (operators.isNotEmpty() && operators.last() != '(') {
                        applyOperator(output, operators.removeLast())
                    }
                    if (operators.isNotEmpty()) operators.removeLast() // Remove '('
                }
                char in "+-*/" -> {
                    while (operators.isNotEmpty() && precedence(operators.last()) >= precedence(char)) {
                        applyOperator(output, operators.removeLast())
                    }
                    operators.add(char)
                }
            }
            i++
        }

        while (operators.isNotEmpty()) {
            applyOperator(output, operators.removeLast())
        }

        return if (output.isNotEmpty()) output[0] else 0.0
    }

    private fun precedence(op: Char): Int {
        return when (op) {
            '+', '-' -> 1
            '*', '/' -> 2
            else -> 0
        }
    }

    private fun applyOperator(output: MutableList<Double>, op: Char) {
        if (output.size < 2) return
        val b = output.removeLast()
        val a = output.removeLast()
        val result = when (op) {
            '+' -> a + b
            '-' -> a - b
            '*' -> a * b
            '/' -> if (b != 0.0) a / b else 0.0
            else -> 0.0
        }
        output.add(result)
    }

    companion object {
        /**
         * Crea un CustomPID desde un mapa JSON.
         */
        fun fromJsonMap(map: Map<String, Any?>): CustomPID? {
            return try {
                CustomPID(
                    pid = map["pid"] as? String ?: return null,
                    name = map["name"] as? String ?: return null,
                    command = map["command"] as? String ?: return null,
                    formula = map["formula"] as? String ?: return null,
                    unit = map["unit"] as? String ?: return null,
                    category = PIDCategory.valueOf(map["category"] as? String ?: "GENERAL"),
                    vehicleModels = (map["vehicleModels"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                    discoveryDate = (map["discoveryDate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    confidence = (map["confidence"] as? Number)?.toFloat() ?: 0.5f,
                    source = PIDSource.valueOf(map["source"] as? String ?: "IMPORTED"),
                    notes = map["notes"] as? String ?: "",
                    byteCount = (map["byteCount"] as? Number)?.toInt() ?: 1,
                    minValue = (map["minValue"] as? Number)?.toDouble(),
                    maxValue = (map["maxValue"] as? Number)?.toDouble()
                )
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Crea un CustomPID desde un FormulaCandidate.
         */
        fun fromFormulaCandidate(
            pid: String,
            command: String,
            candidate: FormulaCandidate,
            vehicleModels: List<String> = emptyList()
        ): CustomPID {
            return CustomPID(
                pid = pid,
                name = candidate.name,
                command = command,
                formula = candidate.formulaExpression,
                unit = candidate.unit ?: "",
                category = when (candidate.category) {
                    FormulaCategory.TEMPERATURE -> PIDCategory.TEMPERATURE
                    FormulaCategory.SPEED -> PIDCategory.SPEED
                    FormulaCategory.PERCENTAGE -> PIDCategory.ENGINE
                    FormulaCategory.RPM -> PIDCategory.ENGINE
                    FormulaCategory.VOLTAGE -> PIDCategory.ELECTRICAL
                    FormulaCategory.PRESSURE -> PIDCategory.ENGINE
                    FormulaCategory.FLOW_RATE -> PIDCategory.FUEL
                    FormulaCategory.FUEL -> PIDCategory.FUEL
                    FormulaCategory.DISTANCE -> PIDCategory.GENERAL
                    FormulaCategory.TIME -> PIDCategory.GENERAL
                    FormulaCategory.TORQUE -> PIDCategory.ENGINE
                    FormulaCategory.POWER -> PIDCategory.ENGINE
                    FormulaCategory.RATIO -> PIDCategory.ENGINE
                    FormulaCategory.BITFIELD -> PIDCategory.GENERAL
                    FormulaCategory.SIMPLE -> PIDCategory.GENERAL
                    FormulaCategory.COMPOSITE -> PIDCategory.GENERAL
                    FormulaCategory.MANUFACTURER -> PIDCategory.PROPRIETARY
                    FormulaCategory.UNKNOWN -> PIDCategory.GENERAL
                },
                vehicleModels = vehicleModels,
                confidence = when (candidate.confidenceLevel) {
                    ConfidenceLevel.VERY_HIGH -> 0.95f
                    ConfidenceLevel.HIGH -> 0.8f
                    ConfidenceLevel.MEDIUM -> 0.6f
                    ConfidenceLevel.LOW -> 0.4f
                    ConfidenceLevel.VERY_LOW -> 0.2f
                    else -> 0.3f
                },
                source = PIDSource.AUTO_DETECTED,
                byteCount = candidate.requiredByteCount
            )
        }
    }
}

/**
 * Categoría de PID personalizado.
 */
enum class PIDCategory(val displayName: String) {
    ENGINE("Motor"),
    FUEL("Combustible"),
    TEMPERATURE("Temperatura"),
    SPEED("Velocidad"),
    ELECTRICAL("Eléctrico"),
    EMISSION("Emisiones"),
    TRANSMISSION("Transmisión"),
    GENERAL("General"),
    PROPRIETARY("Propietario")
}

/**
 * Origen del PID personalizado.
 */
enum class PIDSource {
    USER,           // Creado manualmente por el usuario
    AUTO_DETECTED,  // Detectado automáticamente por análisis
    COMMUNITY,      // Importado de la comunidad
    MANUFACTURER,   // Documentado por el fabricante
    IMPORTED        // Importado de un archivo
}
