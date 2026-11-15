package com.langhost.ui.screens.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.langhost.data.DeviceCustomization
import com.langhost.data.NetworkRepository
import com.langhost.data.model.Device
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceDetailUiState(
    val device: Device? = null,
    val isScanningPorts: Boolean = false,
    val openPorts: List<Int> = emptyList(),
    val nickname: String = "",
    val notes: String = "",
    val isFavorite: Boolean = false,
    val isWatched: Boolean = false,
    val isSaving: Boolean = false
)

class DeviceDetailViewModel(
    application: Application,
    private val deviceIp: String
) : AndroidViewModel(application) {

    private val repository = NetworkRepository.getInstance(application)

    private val _uiState = MutableStateFlow(DeviceDetailUiState())
    val uiState: StateFlow<DeviceDetailUiState> = _uiState.asStateFlow()

    init {
        loadDevice()
    }

    private fun loadDevice() {
        val device = repository.getDevice(deviceIp)
        _uiState.update {
            it.copy(
                device = device,
                nickname = device?.nickname ?: "",
                notes = device?.notes ?: "",
                isFavorite = device?.isFavorite ?: false,
                isWatched = device?.isWatched ?: false,
                openPorts = device?.openPorts ?: emptyList()
            )
        }
    }

    fun scanPorts() {
        if (_uiState.value.isScanningPorts) return

        val scanTarget = _uiState.value.device ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningPorts = true) }
            try {
                val ports = repository.scanDevicePorts(scanTarget)
                val updatedDevice = repository.getDevice(deviceIp)
                _uiState.update {
                    it.copy(
                        openPorts = ports ?: updatedDevice?.openPorts.orEmpty(),
                        device = updatedDevice
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the last known result; the UI must not turn a failed scan into "no open ports".
            } finally {
                _uiState.update { it.copy(isScanningPorts = false) }
            }
        }
    }

    fun updateNickname(value: String) {
        _uiState.update { it.copy(nickname = value) }
    }

    fun updateNotes(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun toggleFavorite() {
        _uiState.update { it.copy(isFavorite = !it.isFavorite) }
        saveCustomization()
    }

    fun toggleWatched() {
        _uiState.update { it.copy(isWatched = !it.isWatched) }
        saveCustomization()
    }

    fun saveCustomization() {
        viewModelScope.launch {
            val device = _uiState.value.device ?: return@launch
            _uiState.update { it.copy(isSaving = true) }
            try {
                val state = _uiState.value
                repository.updateDeviceCustomization(
                    device.stableId,
                    DeviceCustomization(
                        nickname = state.nickname.trim(),
                        notes = state.notes.trim(),
                        isFavorite = state.isFavorite,
                        isWatched = state.isWatched,
                        firstSeen = device.firstSeen
                    )
                )
                val updated = repository.getDevice(deviceIp)
                _uiState.update {
                    it.copy(
                        device = updated ?: device,
                        isSaving = false
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(isSaving = false) }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }
}
