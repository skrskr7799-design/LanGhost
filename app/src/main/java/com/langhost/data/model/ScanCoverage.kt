package com.langhost.data.model

data class ScanCoverage(
    val subnetCidr: String = "",
    val checkedAddresses: Int = 0,
    val totalUsableAddresses: Long = 0L,
    val truncated: Boolean = false,
    val scannedAt: Long = 0L,
    /** Logical network identity for which this coverage was measured. */
    val networkScope: String = ""
) {
    val coveragePercent: Int
        get() = if (totalUsableAddresses <= 0L) 0 else
            ((checkedAddresses.toLong() * 100L) / totalUsableAddresses).toInt().coerceIn(0, 100)
}
