package com.langhost.data

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import com.langhost.data.model.WifiNetwork
import com.langhost.data.network.WifiChannel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

internal data class WifiScanBatch(
    val results: List<WifiNetwork>,
    val updated: Boolean
)

internal fun interface WifiScanRegistration {
    fun unregister()
}

/** Platform boundary for a single Android Wi-Fi scan request. */
internal interface WifiScanPlatform {
    fun registerScanResultsReceiver(onResultsAvailable: (updated: Boolean) -> Unit): WifiScanRegistration
    fun startScan(): Boolean
    fun scanResults(): List<WifiNetwork>
}

/** Owns receiver lifecycle, timeout fallback and cancellation for one scan. */
internal class WifiScanCoordinator(
    private val platform: WifiScanPlatform,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    init { require(timeoutMs > 0L) { "timeoutMs must be positive" } }

    suspend fun scan(): WifiScanBatch =
        withTimeoutOrNull(timeoutMs) { awaitScanResults() }
            ?: WifiScanBatch(platform.scanResults(), updated = false)

    private suspend fun awaitScanResults(): WifiScanBatch =
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            val unregisterRequested = AtomicBoolean(false)
            val registrationReleased = AtomicBoolean(false)
            val registration = AtomicReference<WifiScanRegistration?>(null)

            fun unregister() {
                unregisterRequested.set(true)
                val current = registration.get()
                if (current != null && registrationReleased.compareAndSet(false, true)) current.unregister()
            }

            fun finish(results: List<WifiNetwork>, updated: Boolean) {
                if (finished.compareAndSet(false, true)) {
                    unregister()
                    if (continuation.isActive) continuation.resume(WifiScanBatch(results, updated))
                }
            }

            continuation.invokeOnCancellation {
                if (finished.compareAndSet(false, true)) unregister()
            }

            try {
                val receiverRegistration = platform.registerScanResultsReceiver { updated ->
                    finish(platform.scanResults(), updated)
                }
                registration.set(receiverRegistration)
                if (unregisterRequested.get()) unregister()
                if (!finished.get() && !platform.startScan()) {
                    finish(platform.scanResults(), updated = false)
                }
            } catch (error: Throwable) {
                if (finished.compareAndSet(false, true)) unregister()
                throw error
            }
        }

    companion object { const val DEFAULT_TIMEOUT_MS = 10_000L }
}

/** Android-specific receiver and WifiManager adapter. */
internal class AndroidWifiScanPlatform(
    private val context: Context,
    private val wifiManager: WifiManager,
    private val currentBssid: () -> String?
) : WifiScanPlatform {
    override fun registerScanResultsReceiver(
        onResultsAvailable: (updated: Boolean) -> Unit
    ): WifiScanRegistration {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                onResultsAvailable(intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) == true)
            }
        }
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        val released = AtomicBoolean(false)
        return WifiScanRegistration {
            if (released.compareAndSet(false, true)) runCatching { context.unregisterReceiver(receiver) }
        }
    }

    @SuppressLint("MissingPermission")
    override fun startScan(): Boolean {
        @Suppress("DEPRECATION")
        return wifiManager.startScan()
    }

    @SuppressLint("MissingPermission")
    override fun scanResults(): List<WifiNetwork> {
        val activeBssid = currentBssid()
        return runCatching { wifiManager.scanResults.orEmpty() }.getOrDefault(emptyList())
            .asSequence()
            .map { result ->
                val frequency = result.frequency
                WifiNetwork(
                    ssid = result.SSID.orEmpty(),
                    bssid = result.BSSID.orEmpty(),
                    signalDbm = result.level,
                    frequencyMhz = frequency,
                    channel = WifiChannel.fromFrequencyMhz(frequency),
                    security = WifiSecurity.classify(result.capabilities.orEmpty()),
                    isCurrent = !result.BSSID.isNullOrBlank() && result.BSSID == activeBssid
                )
            }
            .filter { it.bssid.isNotBlank() }
            .distinctBy { it.bssid }
            .sortedByDescending { it.signalDbm }
            .toList()
    }
}
