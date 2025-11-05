package com.fleetcare.obd.ui.analysis

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.ItemFormulaCandidateBinding
import com.fleetcare.obd.domain.model.ConfidenceLevel
import com.fleetcare.obd.domain.model.FormulaCandidate

/**
 * Adapter para mostrar fórmulas candidatas rankeadas.
 *
 * Sprint 4: UI de Análisis de Bytes
 *
 * Muestra:
 * - Nombre y categoría de la fórmula
 * - Expresión matemática
 * - Score/precisión
 * - Nivel de confianza
 * - Resultado de ejemplo
 * - Botón "Usar Esta"
 */
class FormulaCandidateAdapter(
    private val onUseFormula: (FormulaCandidate) -> Unit
) : ListAdapter<FormulaCandidate, FormulaCandidateAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFormulaCandidateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onUseFormula)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    class ViewHolder(
        private val binding: ItemFormulaCandidateBinding,
        private val onUseFormula: (FormulaCandidate) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(formula: FormulaCandidate, position: Int) {
            val context = binding.root.context

            // Nombre
            binding.formulaNameText.text = formula.name

            // Categoría
            binding.formulaCategoryText.text = formula.category.name.replace("_", " ")

            // Score como porcentaje
            val scorePercent = (formula.score * 100).toInt()
            binding.formulaScoreText.text = "$scorePercent%"

            // Color del score según valor
            val scoreColor = when {
                scorePercent >= 90 -> R.color.md_theme_light_primaryContainer
                scorePercent >= 70 -> R.color.md_theme_light_secondaryContainer
                scorePercent >= 50 -> R.color.md_theme_light_tertiaryContainer
                else -> R.color.md_theme_light_errorContainer
            }
            (binding.formulaScoreText.parent as View).setBackgroundColor(
                ContextCompat.getColor(context, scoreColor)
            )

            // Expresión de la fórmula
            binding.formulaExpressionText.text = formula.formulaExpression

            // Descripción
            var description = formula.description
            formula.unit?.let { unit ->
                description += " | Unidad: $unit"
            }
            binding.formulaDescriptionText.text = description

            // Nivel de confianza
            binding.formulaConfidenceText.text = getConfidenceText(formula.confidenceLevel)
            binding.formulaConfidenceText.setTextColor(
                ContextCompat.getColor(context, getConfidenceColor(formula.confidenceLevel))
            )

            // Resultado de ejemplo
            if (formula.sampleResults.isNotEmpty()) {
                val sample = formula.sampleResults.first()
                binding.sampleResultLayout.visibility = View.VISIBLE
                binding.sampleResultText.text = sample.toDebugString()
            } else {
                binding.sampleResultLayout.visibility = View.GONE
            }

            // Botón "Usar Esta"
            binding.useFormulaButton.setOnClickListener {
                onUseFormula(formula)
            }

            // Destacar el top 1
            if (position == 0) {
                binding.root.strokeWidth = 3
                binding.root.strokeColor = ContextCompat.getColor(context, R.color.md_theme_light_primary)
            } else {
                binding.root.strokeWidth = 0
            }
        }

        private fun getConfidenceText(level: ConfidenceLevel): String {
            return when (level) {
                ConfidenceLevel.VERY_HIGH -> "MUY ALTA"
                ConfidenceLevel.HIGH -> "ALTA"
                ConfidenceLevel.MEDIUM -> "MEDIA"
                ConfidenceLevel.LOW -> "BAJA"
                ConfidenceLevel.VERY_LOW -> "MUY BAJA"
                ConfidenceLevel.UNKNOWN -> "DESCONOCIDA"
            }
        }

        private fun getConfidenceColor(level: ConfidenceLevel): Int {
            return when (level) {
                ConfidenceLevel.VERY_HIGH -> R.color.md_theme_light_primary
                ConfidenceLevel.HIGH -> R.color.md_theme_light_secondary
                ConfidenceLevel.MEDIUM -> R.color.md_theme_light_tertiary
                ConfidenceLevel.LOW -> R.color.md_theme_light_error
                ConfidenceLevel.VERY_LOW -> R.color.md_theme_light_error
                ConfidenceLevel.UNKNOWN -> R.color.md_theme_light_onSurfaceVariant
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<FormulaCandidate>() {
        override fun areItemsTheSame(oldItem: FormulaCandidate, newItem: FormulaCandidate): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: FormulaCandidate, newItem: FormulaCandidate): Boolean {
            return oldItem == newItem
        }
    }
}
