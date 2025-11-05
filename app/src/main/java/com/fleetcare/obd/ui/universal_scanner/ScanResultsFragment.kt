package com.fleetcare.obd.ui.universal_scanner

import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.fleetcare.obd.R
import com.fleetcare.obd.databinding.FragmentScanResultsBinding
import com.fleetcare.obd.domain.model.ExportFormat
import com.fleetcare.obd.utils.Logger
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

/**
 * Fragment para visualización de resultados de escaneo.
 */
@AndroidEntryPoint
class ScanResultsFragment : Fragment() {

    private var _binding: FragmentScanResultsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScanResultsViewModel by viewModels()
    private val args: ScanResultsFragmentArgs by navArgs()

    private lateinit var adapter: ScanResultsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScanResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupUI()
        observeState()

        // Cargar sesión
        viewModel.loadSession(args.sessionId)
    }

    private fun setupRecyclerView() {
        adapter = ScanResultsAdapter(
            onItemClick = { result ->
                showResultDetails(result)
            }
        )

        binding.recyclerViewResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ScanResultsFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupUI() {
        // Filter chips
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            applyFilters(checkedIds)
        }

        // Sort spinner
        binding.spinnerSort.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val sortBy = when (position) {
                    0 -> SortByOption.PID_ASC
                    1 -> SortByOption.PID_DESC
                    2 -> SortByOption.MODE_ASC
                    3 -> SortByOption.RESPONSE_TIME_ASC
                    4 -> SortByOption.RESPONSE_TIME_DESC
                    5 -> SortByOption.TIMESTAMP
                    else -> SortByOption.PID_ASC
                }
                viewModel.setSortBy(sortBy)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                // No action needed
            }
        }

        // Group by spinner
        binding.spinnerGroupBy.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val groupBy = when (position) {
                    0 -> GroupByOption.NONE
                    1 -> GroupByOption.MODE
                    2 -> GroupByOption.DATA_TYPE
                    3 -> GroupByOption.SUCCESS
                    else -> GroupByOption.NONE
                }
                viewModel.setGroupBy(groupBy)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                // No action needed
            }
        }

        // Search
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val currentFilter = viewModel.filter.value
                viewModel.applyFilter(currentFilter.copy(searchQuery = newText ?: ""))
                return true
            }
        })
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.session.collect { session ->
                        if (session != null) {
                            updateHeader(session)
                        }
                    }
                }

                launch {
                    viewModel.filteredResults.collect { results ->
                        adapter.submitList(results)
                        binding.tvResultCount.text = "${results.size} results"
                    }
                }

                launch {
                    viewModel.statistics.collect { stats ->
                        if (stats != null) {
                            updateStatistics(stats)
                        }
                    }
                }

                launch {
                    viewModel.groupedResults.collect { grouped ->
                        // TODO: Update adapter to show grouped results
                        Logger.d("Grouped results: ${grouped.keys}")
                    }
                }
            }
        }
    }

    private fun updateHeader(session: com.fleetcare.obd.domain.model.ScanSession) {
        binding.tvSessionId.text = "Session: ${session.sessionId}"
        binding.tvVehicleId.text = "Vehicle: ${session.vehicleId}"
        binding.tvDuration.text = "Duration: ${session.getFormattedDuration()}"
        binding.tvTotalResults.text = "${session.results.size} PIDs scanned"
        binding.tvSuccessful.text = "${session.getSuccessfulResults().size} successful"
    }

    private fun updateStatistics(stats: com.fleetcare.obd.domain.model.ScanStatistics) {
        binding.tvSuccessRate.text = "Success Rate: ${stats.getSuccessRatePercentage()}"
        binding.tvAvgResponseTime.text = "Avg Response: ${stats.averageResponseTime}ms"
        binding.tvQualityScore.text = "Quality: ${stats.qualityScore}/100"

        // Mostrar distribución por modo
        val modeDistribution = stats.pidsByMode.entries.joinToString(", ") { (mode, count) ->
            "Mode $mode: $count"
        }
        binding.tvModeDistribution.text = modeDistribution
    }

    private fun applyFilters(checkedIds: List<Int>) {
        val currentFilter = viewModel.filter.value

        val successOnly = R.id.chipSuccessOnly in checkedIds
        val mode = when {
            R.id.chipMode01 in checkedIds -> "01"
            R.id.chipMode02 in checkedIds -> "02"
            R.id.chipMode09 in checkedIds -> "09"
            R.id.chipMode22 in checkedIds -> "22"
            else -> null
        }

        viewModel.applyFilter(
            currentFilter.copy(
                successOnly = successOnly,
                mode = mode
            )
        )
    }

    private fun showResultDetails(result: com.fleetcare.obd.domain.model.ScanResult) {
        val details = buildString {
            appendLine("Mode: ${result.mode}")
            appendLine("PID: ${result.pid}")
            appendLine("Command: ${result.command}")
            appendLine("Success: ${result.success}")
            appendLine("Response: ${result.rawResponse}")
            appendLine("Response Time: ${result.responseTime}ms")
            appendLine()

            result.metadata?.let { metadata ->
                appendLine("Name: ${metadata.name}")
                appendLine("Unit: ${metadata.unit}")
                appendLine("Formula: ${metadata.formula}")
                appendLine("Type: ${metadata.detectedType}")
                appendLine("Data Length: ${metadata.responseLength} bytes")
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("PID ${result.mode}-${result.pid} Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_scan_results, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_json -> {
                exportResults(ExportFormat.JSON)
                true
            }
            R.id.action_export_csv -> {
                exportResults(ExportFormat.CSV)
                true
            }
            R.id.action_share -> {
                shareResults()
                true
            }
            R.id.action_reset_filters -> {
                viewModel.resetFilters()
                binding.chipGroupFilter.clearCheck()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportResults(format: ExportFormat) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.exportResults(format).collect { content ->
                    saveToFile(content, format)
                }
            } catch (e: Exception) {
                Logger.e("Error exporting results", e)
                Snackbar.make(
                    binding.root,
                    "Error exporting: ${e.message}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveToFile(content: String, format: ExportFormat) {
        try {
            val fileName = "scan_results_${System.currentTimeMillis()}.${format.name.lowercase()}"
            val file = File(requireContext().getExternalFilesDir(null), fileName)
            file.writeText(content)

            Snackbar.make(
                binding.root,
                "Exported to: $fileName",
                Snackbar.LENGTH_LONG
            ).setAction("Share") {
                shareFile(file)
            }.show()

            Logger.d("Results exported to: ${file.absolutePath}")
        } catch (e: Exception) {
            Logger.e("Error saving file", e)
            Snackbar.make(
                binding.root,
                "Error saving file: ${e.message}",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(intent, "Share Results"))
        } catch (e: Exception) {
            Logger.e("Error sharing file", e)
        }
    }

    private fun shareResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                viewModel.exportResults(ExportFormat.JSON).collect { content ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, content)
                    }
                    startActivity(Intent.createChooser(intent, "Share Results"))
                }
            } catch (e: Exception) {
                Logger.e("Error sharing results", e)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
