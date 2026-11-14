package com.langhost.ui.screens.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.langhost.data.NetworkRepository
import com.langhost.data.model.Device
import com.langhost.data.model.NetworkInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val networkInfo: NetworkInfo = NetworkInfo(),
    val devices: List<Device> = emptyList(),
    val isScanning: Boolean = false,
    val lastScanTime: Long = 0L
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NetworkRepository.getInstance(application)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.networkInfo,
        repository.devices,
        repository.isScanning,
        repository.lastScanTime
    ) { networkInfo, devices, isScanning, lastScanTime ->
        DashboardUiState(
            networkInfo = networkInfo,
            devices = devices,
            isScanning = isScanning,
            lastScanTime = lastScanTime
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    init {
        repository.refreshNetworkInfo()
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
