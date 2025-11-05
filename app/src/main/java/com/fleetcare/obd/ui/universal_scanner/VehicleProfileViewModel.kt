package com.fleetcare.obd.ui.universal_scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fleetcare.obd.domain.model.PIDMetadata
import com.fleetcare.obd.domain.model.ScanSession
import com.fleetcare.obd.domain.model.VehicleProfile
import com.fleetcare.obd.domain.repository.PIDMetadataRepository
import com.fleetcare.obd.domain.repository.UniversalScanRepository
import com.fleetcare.obd.domain.repository.VehicleProfileRepository
import com.fleetcare.obd.utils.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel para gestión de perfiles de vehículos.
 */
@HiltViewModel
class VehicleProfileViewModel @Inject constructor(
    private val profileRepository: VehicleProfileRepository,
    private val metadataRepository: PIDMetadataRepository,
    private val scanRepository: UniversalScanRepository
) : ViewModel() {

    // ========== State ==========

    private val _profile = MutableStateFlow<VehicleProfile?>(null)
    val profile: StateFlow<VehicleProfile?> = _profile.asStateFlow()

    private val _allProfiles = MutableStateFlow<List<VehicleProfile>>(emptyList())
    val allProfiles: StateFlow<List<VehicleProfile>> = _allProfiles.asStateFlow()

    private val _pidMetadata = MutableStateFlow<List<PIDMetadata>>(emptyList())
    val pidMetadata: StateFlow<List<PIDMetadata>> = _pidMetadata.asStateFlow()

    private val _scanHistory = MutableStateFlow<List<ScanSession>>(emptyList())
    val scanHistory: StateFlow<List<ScanSession>> = _scanHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ========== Computed Properties ==========

    val hasProfile: StateFlow<Boolean> = _profile.map {
        it != null
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val isComplete: StateFlow<Boolean> = _profile.map {
        it?.isComplete() ?: false
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val supportedPIDsCount: StateFlow<Int> = _profile.map {
        it?.supportedPIDsCount ?: 0
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val highQualityPIDsCount: StateFlow<Int> = _pidMetadata.map { metadata ->
        metadata.count { it.isHighQuality() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val realTimeMonitoringPIDsCount: StateFlow<Int> = _pidMetadata.map { metadata ->
        metadata.count { it.isSuitableForRealTimeMonitoring() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // ========== Public Methods ==========

    /**
     * Carga el perfil de un vehículo.
     */
    fun loadProfile(vehicleId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Cargar perfil
                val profile = profileRepository.getProfile(vehicleId)
                _profile.value = profile

                if (profile != null) {
                    // Cargar metadata de PIDs
                    loadPIDMetadata(vehicleId)

                    // Cargar historial de scans
                    loadScanHistory(vehicleId)

                    Logger.d("Profile loaded: ${profile.getDisplayName()}")
                } else {
                    // Crear perfil básico
                    val newProfile = VehicleProfile.createBasic(vehicleId)
                    profileRepository.saveProfile(newProfile)
                    _profile.value = newProfile
                    Logger.d("Created new profile for vehicle: $vehicleId")
                }

            } catch (e: Exception) {
                Logger.e("Error loading profile", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Carga todos los perfiles.
     */
    fun loadAllProfiles() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                profileRepository.getAllProfiles().collect { profiles ->
                    _allProfiles.value = profiles
                    Logger.d("Loaded ${profiles.size} profiles")
                }
            } catch (e: Exception) {
                Logger.e("Error loading all profiles", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Carga la metadata de PIDs del vehículo.
     */
    private fun loadPIDMetadata(vehicleId: String) {
        viewModelScope.launch {
            metadataRepository.getMetadataByVehicle(vehicleId).collect { metadata ->
                _pidMetadata.value = metadata
            }
        }
    }

    /**
     * Carga el historial de scans.
     */
    private fun loadScanHistory(vehicleId: String) {
        viewModelScope.launch {
            scanRepository.getSessionsByVehicle(vehicleId).collect { sessions ->
                _scanHistory.value = sessions
            }
        }
    }

    /**
     * Actualiza la información del vehículo.
     */
    fun updateVehicleInfo(
        vehicleId: String,
        vin: String,
        make: String,
        model: String,
        year: Int?
    ) {
        viewModelScope.launch {
            try {
                profileRepository.updateVehicleInfo(vehicleId, vin, make, model, year)
                loadProfile(vehicleId)
                Logger.d("Vehicle info updated")
            } catch (e: Exception) {
                Logger.e("Error updating vehicle info", e)
            }
        }
    }

    /**
     * Elimina un perfil.
     */
    fun deleteProfile(vehicleId: String) {
        viewModelScope.launch {
            try {
                profileRepository.deleteProfile(vehicleId)
                _profile.value = null
                Logger.d("Profile deleted: $vehicleId")
            } catch (e: Exception) {
                Logger.e("Error deleting profile", e)
            }
        }
    }

    /**
     * Obtiene PIDs de alta calidad para monitoreo en tiempo real.
     */
    fun getHighQualityPIDs(vehicleId: String): Flow<List<PIDMetadata>> {
        return metadataRepository.getHighQualityPIDs(vehicleId)
    }

    /**
     * Obtiene PIDs aptos para monitoreo en tiempo real.
     */
    fun getRealTimeMonitoringPIDs(vehicleId: String): Flow<List<PIDMetadata>> {
        return metadataRepository.getRealTimeMonitoringPIDs(vehicleId)
    }

    /**
     * Busca vehículos por query.
     */
    fun searchVehicles(query: String) {
        viewModelScope.launch {
            try {
                profileRepository.searchVehicles(query).collect { profiles ->
                    _allProfiles.value = profiles
                }
            } catch (e: Exception) {
                Logger.e("Error searching vehicles", e)
            }
        }
    }

    /**
     * Filtra perfiles legacy.
     */
    fun filterLegacyVehicles() {
        viewModelScope.launch {
            try {
                profileRepository.getLegacyVehicles().collect { profiles ->
                    _allProfiles.value = profiles
                }
            } catch (e: Exception) {
                Logger.e("Error filtering legacy vehicles", e)
            }
        }
    }

    /**
     * Filtra perfiles modernos.
     */
    fun filterModernVehicles() {
        viewModelScope.launch {
            try {
                profileRepository.getModernVehicles().collect { profiles ->
                    _allProfiles.value = profiles
                }
            } catch (e: Exception) {
                Logger.e("Error filtering modern vehicles", e)
            }
        }
    }
}
