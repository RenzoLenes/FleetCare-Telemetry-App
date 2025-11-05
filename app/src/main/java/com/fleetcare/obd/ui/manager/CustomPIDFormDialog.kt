package com.fleetcare.obd.ui.manager

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.DialogCustomPidFormBinding
import com.fleetcare.obd.domain.model.CustomPID
import com.fleetcare.obd.domain.model.FormulaCandidate
import com.fleetcare.obd.domain.model.PIDCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Dialog para crear o editar un PID personalizado.
 *
 * Sprint 6: Gestión de PIDs Personalizados - Tarea 6.5
 */
@AndroidEntryPoint
class CustomPIDFormDialog : DialogFragment() {

    private var _binding: DialogCustomPidFormBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CustomPIDFormViewModel by viewModels()

    private val formulaCandidatesAdapter = FormulaCandidatesAdapter { candidate ->
        viewModel.selectFormulaCandidate(candidate)
    }

    private var editingPID: CustomPID? = null
    private var formulaCandidates: List<FormulaCandidate>? = null
    private var onSaveCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_FleetCareOBD)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCustomPidFormBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        observeData()

        // Cargar PID si estamos editando
        editingPID?.let { viewModel.loadPID(it) }

        // Cargar fórmulas candidatas si las hay
        formulaCandidates?.let { viewModel.loadFormulaCandidates(it) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .create()
    }

    private fun setupUI() {
        // Title
        binding.dialogTitle.text = if (editingPID != null) {
            "Editar PID ${editingPID!!.pid.uppercase()}"
        } else {
            "Nuevo PID Personalizado"
        }

        // Candidates RecyclerView
        binding.candidatesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = formulaCandidatesAdapter
        }

        // Text Fields
        binding.nameEditText.doAfterTextChanged { text ->
            viewModel.setName(text?.toString() ?: "")
        }

        binding.pidEditText.doAfterTextChanged { text ->
            viewModel.setPID(text?.toString() ?: "")
        }

        binding.commandEditText.doAfterTextChanged { text ->
            viewModel.setCommand(text?.toString() ?: "")
        }

        binding.formulaEditText.doAfterTextChanged { text ->
            viewModel.setFormula(text?.toString() ?: "")
        }

        binding.unitEditText.doAfterTextChanged { text ->
            viewModel.setUnit(text?.toString() ?: "")
        }

        binding.byteCountEditText.doAfterTextChanged { text ->
            val count = text?.toString()?.toIntOrNull() ?: 1
            viewModel.setByteCount(count)
        }

        binding.notesEditText.doAfterTextChanged { text ->
            viewModel.setNotes(text?.toString() ?: "")
        }

        // Category Chips
        binding.categoryChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener

            val category = when (checkedIds[0]) {
                R.id.engineChip -> PIDCategory.ENGINE
                R.id.fuelChip -> PIDCategory.FUEL
                R.id.temperatureChip -> PIDCategory.TEMPERATURE
                R.id.speedChip -> PIDCategory.SPEED
                R.id.electricalChip -> PIDCategory.ELECTRICAL
                else -> PIDCategory.GENERAL
            }
            viewModel.setCategory(category)
        }

        // Confidence Slider
        binding.confidenceSlider.addOnChangeListener { _, value, _ ->
            viewModel.setConfidence(value)
        }

        // Enabled Switch
        binding.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setEnabled(isChecked)
        }

        // Buttons
        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.saveButton.setOnClickListener {
            viewModel.savePID()
        }
    }

    private fun observeData() {
        // Name
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.name.collect { name ->
                if (binding.nameEditText.text?.toString() != name) {
                    binding.nameEditText.setText(name)
                }
            }
        }

        // PID
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pid.collect { pid ->
                if (binding.pidEditText.text?.toString() != pid) {
                    binding.pidEditText.setText(pid)
                }
            }
        }

        // Command
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.command.collect { command ->
                if (binding.commandEditText.text?.toString() != command) {
                    binding.commandEditText.setText(command)
                }
            }
        }

        // Formula
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.formula.collect { formula ->
                if (binding.formulaEditText.text?.toString() != formula) {
                    binding.formulaEditText.setText(formula)
                }
            }
        }

        // Unit
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.unit.collect { unit ->
                if (binding.unitEditText.text?.toString() != unit) {
                    binding.unitEditText.setText(unit)
                }
            }
        }

        // Byte Count
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.byteCount.collect { count ->
                if (binding.byteCountEditText.text?.toString() != count.toString()) {
                    binding.byteCountEditText.setText(count.toString())
                }
            }
        }

        // Category
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.category.collect { category ->
                val chipId = when (category) {
                    PIDCategory.ENGINE -> R.id.engineChip
                    PIDCategory.FUEL -> R.id.fuelChip
                    PIDCategory.TEMPERATURE -> R.id.temperatureChip
                    PIDCategory.SPEED -> R.id.speedChip
                    PIDCategory.ELECTRICAL -> R.id.electricalChip
                    else -> R.id.generalChip
                }
                binding.categoryChipGroup.check(chipId)
            }
        }

        // Confidence
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.confidence.collect { confidence ->
                binding.confidenceSlider.value = confidence
                binding.confidenceText.text = "${(confidence * 100).toInt()}%"
            }
        }

        // Enabled
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isEnabled.collect { enabled ->
                binding.enabledSwitch.isChecked = enabled
            }
        }

        // Notes
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.notes.collect { notes ->
                if (binding.notesEditText.text?.toString() != notes) {
                    binding.notesEditText.setText(notes)
                }
            }
        }

        // Formula Candidates
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.formulaCandidates.collect { candidates ->
                if (candidates.isNotEmpty()) {
                    binding.candidatesCard.visibility = View.VISIBLE
                    formulaCandidatesAdapter.submitList(candidates)
                } else {
                    binding.candidatesCard.visibility = View.GONE
                }
            }
        }

        // Formula Preview
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.formulaPreview.collect { preview ->
                if (preview != null && preview.success && preview.items.isNotEmpty()) {
                    binding.previewCard.visibility = View.VISIBLE
                    val previewText = preview.items.joinToString("\n") { item ->
                        "[${item.getBytesHex()}] → ${String.format("%.2f", item.result)}"
                    }
                    binding.previewText.text = previewText
                } else {
                    binding.previewCard.visibility = View.GONE
                }
            }
        }

        // Valid
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isValid.collect { isValid ->
                binding.saveButton.isEnabled = isValid && !viewModel.isSaving.value
            }
        }

        // Saving
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isSaving.collect { isSaving ->
                binding.saveButton.isEnabled = !isSaving && viewModel.isValid.value
                binding.saveButton.text = if (isSaving) "Guardando..." else "Guardar"
            }
        }

        // Success Message
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.successMessage.collect { message ->
                message?.let {
                    Timber.d("PID guardado exitosamente")
                    onSaveCallback?.invoke()
                    dismiss()
                }
            }
        }

        // Error Message
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.errorMessage.collect { error ->
                error?.let {
                    showError(it)
                    viewModel.clearMessages()
                }
            }
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(resources.getColor(R.color.md_theme_light_error, null))
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /**
         * Crea una instancia para crear un nuevo PID.
         */
        fun newInstance(
            formulaCandidates: List<FormulaCandidate>? = null,
            onSave: () -> Unit
        ): CustomPIDFormDialog {
            return CustomPIDFormDialog().apply {
                this.formulaCandidates = formulaCandidates
                this.onSaveCallback = onSave
            }
        }

        /**
         * Crea una instancia para editar un PID existente.
         */
        fun editInstance(
            pid: CustomPID,
            onSave: () -> Unit
        ): CustomPIDFormDialog {
            return CustomPIDFormDialog().apply {
                this.editingPID = pid
                this.onSaveCallback = onSave
            }
        }
    }
}

/**
 * Adapter simple para fórmulas candidatas.
 */
class FormulaCandidatesAdapter(
    private val onItemClick: (FormulaCandidate) -> Unit
) : androidx.recyclerview.widget.ListAdapter<FormulaCandidate, FormulaCandidatesAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<FormulaCandidate>() {
        override fun areItemsTheSame(oldItem: FormulaCandidate, newItem: FormulaCandidate): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: FormulaCandidate, newItem: FormulaCandidate): Boolean {
            return oldItem == newItem
        }
    }
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = com.fleetcare.obd.databinding.ItemFormulaSimpleBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: com.fleetcare.obd.databinding.ItemFormulaSimpleBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(candidate: FormulaCandidate) {
            binding.formulaNameText.text = candidate.name
            binding.formulaExpressionText.text = candidate.formulaExpression
            binding.scoreChip.text = "${(candidate.score * 100).toInt()}%"

            binding.root.setOnClickListener {
                onItemClick(candidate)
            }
        }
    }
}
