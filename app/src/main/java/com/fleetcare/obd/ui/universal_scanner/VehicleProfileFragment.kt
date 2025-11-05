package com.fleetcare.obd.ui.universal_scanner

import android.os.Bundle
import android.view.*
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.FragmentVehicleProfileBinding
import com.fleetcare.obd.domain.model.PIDMetadata
import com.fleetcare.obd.utils.Logger
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment para visualización de perfil de vehículo.
 */
@AndroidEntryPoint
class VehicleProfileFragment : Fragment() {

    private var _binding: FragmentVehicleProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VehicleProfileViewModel by viewModels()
    private val args: VehicleProfileFragmentArgs by navArgs()

    private lateinit var metadataAdapter: PIDMetadataAdapter
    private lateinit var scanHistoryAdapter: ScanHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVehicleProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMenu()
        setupRecyclerViews()
        setupUI()
        observeState()

        // Cargar perfil
        viewModel.loadProfile(args.vehicleId)
    }

    private fun setupMenu() {
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_vehicle_profile, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_delete_profile -> {
                        confirmDeleteProfile()
                        true
                    }
                    R.id.action_export_profile -> {
                        exportProfile()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerViews() {
        // PID Metadata RecyclerView
        metadataAdapter = PIDMetadataAdapter(
            onItemClick = { metadata ->
                showMetadataDetails(metadata)
            }
        )

        binding.recyclerViewPidMetadata.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = metadataAdapter
            setHasFixedSize(true)
        }

        // Scan History RecyclerView
        scanHistoryAdapter = ScanHistoryAdapter(
            onItemClick = { session ->
                navigateToResults(session.sessionId)
            }
        )

        binding.recyclerViewScanHistory.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = scanHistoryAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupUI() {
        // Edit vehicle info button
        binding.btnEditVehicleInfo.setOnClickListener {
            showEditVehicleInfoDialog()
        }

        // Tabs
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showOverviewTab()
                    1 -> showPIDMetadataTab()
                    2 -> showScanHistoryTab()
                }
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.profile.collect { profile ->
                        if (profile != null) {
                            updateProfileInfo(profile)
                        }
                    }
                }

                launch {
                    viewModel.pidMetadata.collect { metadata ->
                        metadataAdapter.submitList(metadata)
                    }
                }

                launch {
                    viewModel.scanHistory.collect { sessions ->
                        scanHistoryAdapter.submitList(sessions)
                    }
                }

                launch {
                    viewModel.supportedPIDsCount.collect { count ->
                        binding.tvSupportedPidsCount.text = "$count PIDs"
                    }
                }

                launch {
                    viewModel.highQualityPIDsCount.collect { count ->
                        binding.tvHighQualityPidsCount.text = "$count High Quality"
                    }
                }

                launch {
                    viewModel.realTimeMonitoringPIDsCount.collect { count ->
                        binding.tvRealTimePidsCount.text = "$count Real-time Ready"
                    }
                }
            }
        }
    }

    private fun updateProfileInfo(profile: com.fleetcare.obd.domain.model.VehicleProfile) {
        binding.apply {
            // Vehicle info
            tvVehicleName.text = profile.getDisplayName()
            tvVin.text = if (profile.vin.isNotEmpty()) profile.vin else "No VIN"
            tvProtocol.text = profile.protocolName
            tvProtocolType.text = if (profile.isLegacyVehicle) "Legacy" else "Modern"

            // ECU info
            if (profile.ecuInfo.hasData()) {
                layoutEcuInfo.visibility = View.VISIBLE
                tvEcuName.text = profile.ecuInfo.name
                tvEcuCalibrationId.text = profile.ecuInfo.calibrationId
                tvEcuCvn.text = profile.ecuInfo.calibrationVerificationNumber
            } else {
                layoutEcuInfo.visibility = View.GONE
            }

            // Stats
            tvTotalScans.text = "${profile.totalScans} scans"
            tvAverageQuality.text = "${profile.averageQualityScore}/100"

            if (profile.lastScanned > 0) {
                val lastScanDate = java.text.SimpleDateFormat(
                    "MMM dd, yyyy HH:mm",
                    java.util.Locale.getDefault()
                ).format(java.util.Date(profile.lastScanned))
                tvLastScanned.text = lastScanDate
            } else {
                tvLastScanned.text = "Never"
            }

            // Completeness indicator
            if (profile.isComplete()) {
                ivCompletenessIndicator.setImageResource(R.drawable.ic_check_circle)
                tvCompletenessStatus.text = "Profile Complete"
            } else {
                ivCompletenessIndicator.setImageResource(R.drawable.ic_warning)
                tvCompletenessStatus.text = "Profile Incomplete"
            }
        }
    }

    private fun showOverviewTab() {
        binding.layoutOverview.visibility = View.VISIBLE
        binding.recyclerViewPidMetadata.visibility = View.GONE
        binding.recyclerViewScanHistory.visibility = View.GONE
    }

    private fun showPIDMetadataTab() {
        binding.layoutOverview.visibility = View.GONE
        binding.recyclerViewPidMetadata.visibility = View.VISIBLE
        binding.recyclerViewScanHistory.visibility = View.GONE
    }

    private fun showScanHistoryTab() {
        binding.layoutOverview.visibility = View.GONE
        binding.recyclerViewPidMetadata.visibility = View.GONE
        binding.recyclerViewScanHistory.visibility = View.VISIBLE
    }

    private fun showEditVehicleInfoDialog() {
        val profile = viewModel.profile.value ?: return

        val view = layoutInflater.inflate(R.layout.dialog_edit_vehicle_info, null)
        val etVin = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVin)
        val etMake = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etMake)
        val etModel = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etModel)
        val etYear = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etYear)

        etVin.setText(profile.vin)
        etMake.setText(profile.make)
        etModel.setText(profile.model)
        etYear.setText(profile.year?.toString() ?: "")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Vehicle Info")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val vin = etVin.text.toString()
                val make = etMake.text.toString()
                val model = etModel.text.toString()
                val year = etYear.text.toString().toIntOrNull()

                viewModel.updateVehicleInfo(args.vehicleId, vin, make, model, year)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMetadataDetails(metadata: PIDMetadata) {
        val details = buildString {
            appendLine("Mode: ${metadata.mode}")
            appendLine("PID: ${metadata.pid}")
            appendLine("Name: ${metadata.name}")
            appendLine("Unit: ${metadata.unit}")
            appendLine("Formula: ${metadata.formula}")
            appendLine("Type: ${metadata.detectedType}")
            appendLine()
            appendLine("Response Length: ${metadata.responseLength} bytes")
            appendLine("Success Rate: ${(metadata.successRate * 100).toInt()}%")
            appendLine("Avg Response Time: ${metadata.averageResponseTime}ms")
            appendLine()

            if (metadata.minValue != null && metadata.maxValue != null) {
                appendLine("Value Range: ${metadata.minValue} - ${metadata.maxValue}")
            }

            appendLine("Standard: ${if (metadata.isStandard) "Yes" else "No"}")
            appendLine("Vehicle Specific: ${if (metadata.vehicleSpecific) "Yes" else "No"}")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("PID ${metadata.mode}-${metadata.pid}")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun navigateToResults(sessionId: String) {
        val action = VehicleProfileFragmentDirections.actionProfileToResults(sessionId)
        findNavController().navigate(action)
    }

    private fun confirmDeleteProfile() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Profile?")
            .setMessage("This will delete all scan history and metadata for this vehicle.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteProfile(args.vehicleId)
                requireActivity().onBackPressed()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportProfile() {
        // TODO: Implement profile export
        Logger.d("Export profile not implemented yet")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Adapter for PID Metadata RecyclerView
 */
class PIDMetadataAdapter(
    private val onItemClick: (PIDMetadata) -> Unit
) : androidx.recyclerview.widget.ListAdapter<PIDMetadata, PIDMetadataAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<PIDMetadata>() {
        override fun areItemsTheSame(oldItem: PIDMetadata, newItem: PIDMetadata) =
            oldItem.getUniqueId() == newItem.getUniqueId()
        override fun areContentsTheSame(oldItem: PIDMetadata, newItem: PIDMetadata) =
            oldItem == newItem
    }
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = com.fleetcare.obd.databinding.ItemPidMetadataBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: com.fleetcare.obd.databinding.ItemPidMetadataBinding,
        private val onItemClick: (PIDMetadata) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(metadata: PIDMetadata) {
            binding.apply {
                // PID ID and name
                tvPidId.text = "PID ${metadata.mode}-${metadata.pid}"
                tvName.text = metadata.name

                // Success rate
                val successRatePercent = (metadata.successRate * 100).toInt()
                tvSuccessRate.text = "$successRatePercent%"

                // Response time
                tvResponseTime.text = "${metadata.averageResponseTime}ms avg"

                // Data info
                tvDataInfo.text = buildString {
                    append("${metadata.responseLength} bytes")
                    append(" • ${metadata.detectedType}")
                    if (metadata.unit.isNotEmpty()) {
                        append(" • ${metadata.unit}")
                    }
                }

                // Value range (optional)
                if (metadata.minValue != null && metadata.maxValue != null) {
                    tvValueRange.visibility = View.VISIBLE
                    tvValueRange.text = "Range: ${metadata.minValue} - ${metadata.maxValue}"
                } else {
                    tvValueRange.visibility = View.GONE
                }

                // Quality indicator
                if (metadata.isHighQuality()) {
                    ivQualityIndicator.setImageResource(com.fleetcare.obd.R.drawable.ic_star)
                    ivQualityIndicator.setColorFilter(
                        androidx.core.content.ContextCompat.getColor(root.context, com.fleetcare.obd.R.color.success_green)
                    )
                } else {
                    ivQualityIndicator.setImageResource(com.fleetcare.obd.R.drawable.ic_warning)
                    ivQualityIndicator.setColorFilter(
                        androidx.core.content.ContextCompat.getColor(root.context, com.fleetcare.obd.R.color.warning_yellow)
                    )
                }

                // Tags
                chipStandard.visibility = if (metadata.isStandard) View.VISIBLE else View.GONE
                chipVehicleSpecific.visibility = if (metadata.vehicleSpecific) View.VISIBLE else View.GONE
                chipRealTime.visibility = if (metadata.isSuitableForRealTimeMonitoring()) View.VISIBLE else View.GONE

                // Click listener
                root.setOnClickListener { onItemClick(metadata) }
            }
        }
    }
}

/**
 * Adapter for Scan History RecyclerView
 */
class ScanHistoryAdapter(
    private val onItemClick: (com.fleetcare.obd.domain.model.ScanSession) -> Unit
) : androidx.recyclerview.widget.ListAdapter<com.fleetcare.obd.domain.model.ScanSession, ScanHistoryAdapter.ViewHolder>(
    object : androidx.recyclerview.widget.DiffUtil.ItemCallback<com.fleetcare.obd.domain.model.ScanSession>() {
        override fun areItemsTheSame(
            oldItem: com.fleetcare.obd.domain.model.ScanSession,
            newItem: com.fleetcare.obd.domain.model.ScanSession
        ) = oldItem.sessionId == newItem.sessionId

        override fun areContentsTheSame(
            oldItem: com.fleetcare.obd.domain.model.ScanSession,
            newItem: com.fleetcare.obd.domain.model.ScanSession
        ) = oldItem == newItem
    }
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = com.fleetcare.obd.databinding.ItemScanHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: com.fleetcare.obd.databinding.ItemScanHistoryBinding,
        private val onItemClick: (com.fleetcare.obd.domain.model.ScanSession) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {

        fun bind(session: com.fleetcare.obd.domain.model.ScanSession) {
            binding.apply {
                // Scan type - Determinar basado en el estado o config
                val scanType = when {
                    session.config.modes.size > 3 -> "Deep Scan"
                    session.config.modes.contains(com.fleetcare.obd.domain.model.ScanMode.MODE_22_MANUFACTURER) -> "Manufacturer Scan"
                    session.config.modes.size == 1 -> "Quick Scan"
                    else -> "Full Scan"
                }
                tvScanType.text = scanType
                tvSessionId.text = session.sessionId

                // Icon based on scan type
                ivScanTypeIcon.setImageResource(com.fleetcare.obd.R.drawable.ic_play_arrow)

                // Date and time - usar startTime en vez de timestamp
                val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                val date = java.util.Date(session.startTime)

                tvDate.text = dateFormat.format(date)
                tvTime.text = timeFormat.format(date)

                // Statistics - usar statistics en vez de propiedades directas
                val successfulPIDs = session.statistics?.successfulPIDs ?: 0
                val failedPIDs = session.statistics?.failedPIDs ?: 0
                val qualityScore = session.statistics?.qualityScore ?: 0

                tvPidsFound.text = successfulPIDs.toString()

                // Duration - usar getDuration() en vez de property
                val durationMs = session.getDuration()
                val durationSeconds = durationMs / 1000
                val minutes = durationSeconds / 60
                val seconds = durationSeconds % 60
                tvDuration.text = String.format("%02d:%02d", minutes, seconds)

                // Quality score
                tvQualityScore.text = qualityScore.toString()

                // Quality icon color
                val qualityColor = when {
                    qualityScore >= 80 -> com.fleetcare.obd.R.color.quality_high
                    qualityScore >= 50 -> com.fleetcare.obd.R.color.quality_medium
                    else -> com.fleetcare.obd.R.color.quality_low
                }
                ivQualityIcon.setColorFilter(
                    androidx.core.content.ContextCompat.getColor(root.context, qualityColor)
                )

                // Success rate
                val totalResults = successfulPIDs + failedPIDs
                val successRate = if (totalResults > 0) {
                    (successfulPIDs.toFloat() / totalResults * 100).toInt()
                } else {
                    0
                }
                tvSuccessRate.text = "Success Rate: $successRate% ($successfulPIDs/$totalResults PIDs)"

                // Mode distribution - usar pidsByMode en vez de modeDistribution
                tvModeDistribution.text = buildString {
                    val modes = (session.statistics?.pidsByMode ?: emptyMap()).entries.sortedBy { it.key }
                    modes.forEachIndexed { index, (mode, count) ->
                        if (index > 0) append(", ")
                        append("Mode $mode: $count")
                    }
                }

                // Click listener
                root.setOnClickListener { onItemClick(session) }
            }
        }
    }
}
