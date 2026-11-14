package com.langhost.ui.screens.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.langhost.data.NetworkRepository
import com.langhost.data.model.Device
import com.langhost.data.model.NetworkInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val networkInfo: NetworkInfo = NetworkInfo(),
    val devices: List<Device> = emptyList(),
    val isScanning: Boolean = false,
    val lastScanTime: Long = 0L,
    val error: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NetworkRepository(application)

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refreshNetworkInfo()
    }

    fun refreshNetworkInfo() {
        val info = repository.getCurrentNetworkInfo()
        _uiState.update { it.copy(networkInfo = info) }
    }

    fun startScan() {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, error = null) }
            try {
                val devices = repository.scanDevices()
                _uiState.update {
                    it.copy(
                        devices = devices,
                        isScanning = false,
                        lastScanTime = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        error = e.message ?: "Scan failed"
                    )
                }
            }
        }
    }

    fun getDevices(): List<Device> = _uiState.value.devices.ifEmpty {
        repository.getCachedDevices()
    }
}
