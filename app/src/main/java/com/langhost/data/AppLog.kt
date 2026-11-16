package com.langhost.data

import android.util.Log

/**
 * Small structured logger. Only fixed component/event identifiers are accepted,
 * so callers cannot accidentally attach SSIDs, IPs, MACs, URLs or raw payloads.
 */
internal object AppLog {
    enum class Component {
        NETWORK_MONITOR,
        APP_SETTINGS,
        DEVICE_PREFERENCES,
        WATCH_WORKER,
        WIDGET_WORKER,
        NETWORK_DISCOVERY
    }

    enum class Event {
        CALLBACK_REGISTRATION_FAILED,
        SNAPSHOT_READ_FAILED,
        SETTINGS_READ_FALLBACK,
        CUSTOMIZATION_READ_FALLBACK,
        KNOWN_IDS_READ_FALLBACK,
        BACKGROUND_SCAN_RETRY,
        WIDGET_SCAN_RETRY,
        ARP_TABLE_READ_FAILED
    }

    fun warning(component: Component, event: Event) {
        Log.w(TAG, "${component.name.lowercase()}:${event.name.lowercase()}")
    }

    private const val TAG = "LanGhost"
}
