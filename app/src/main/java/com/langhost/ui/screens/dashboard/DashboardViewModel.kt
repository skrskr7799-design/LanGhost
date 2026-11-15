package com.langhost.ui.screens.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.langhost.data.AppSettings
import com.langhost.data.NetworkRepository
import com.langhost.data.model.Device
import com.langhost.data.model.NetworkInfo
import com.langhost.data.model.ScanCoverage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val networkInfo: NetworkInfo = NetworkInfo(),
    val devices: List<Device> = emptyList(),
    val isScanning: Boolean = false,
    val lastScanTime: Long = 0L,
    val scanCoverage: ScanCoverage = ScanCoverage()
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NetworkRepository.getInstance(application)
    private val appSettings = AppSettings(application)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.networkInfo,
        repository.devices,
        repository.isScanning,
        repository.lastScanTime,
        repository.scanCoverage
    ) { networkInfo, devices, isScanning, lastScanTime, scanCoverage ->
        DashboardUiState(
            networkInfo = networkInfo,
            devices = devices,
            isScanning = isScanning,
            lastScanTime = lastScanTime,
            scanCoverage = scanCoverage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    init {
        repository.refreshNetworkInfo()
        viewModelScope.launch {
            val settings = appSettings.getOnce()
            if (settings.autoScanOnOpen) {
                repository.scanDevices()
            }
        }
    }

    fun refreshNetworkInfo() {
        repository.refreshNetworkInfo()
    }

    fun startScan() {
        viewModelScope.launch {
            repository.scanDevices()
        }
    }

    fun getDevice(ip: String): Device? = repository.getDevice(ip)
}
