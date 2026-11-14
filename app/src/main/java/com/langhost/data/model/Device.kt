package com.langhost.data.model

data class Device(
    val ip: String,
    val mac: String = "",
    val hostname: String = "",
    val vendor: String = "",
    val isLocalDevice: Boolean = false,
    val isGateway: Boolean = false,
    val firstSeen: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val openPorts: List<Int> = emptyList(),
    val osGuess: String = "",
    // User customizations
    val nickname: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val color: Long? = null,
    val isFavorite: Boolean = false,
    val isWatched: Boolean = false
) {
    val displayName: String
        get() = when {
            nickname.isNotBlank() -> nickname
            hostname.isNotBlank() && hostname != ip -> hostname
            vendor.isNotBlank() -> vendor
            else -> ip
        }

    val isOnline: Boolean
        get() = (System.currentTimeMillis() - lastSeen) < 5 * 60 * 1000 // 5 minutes
}
