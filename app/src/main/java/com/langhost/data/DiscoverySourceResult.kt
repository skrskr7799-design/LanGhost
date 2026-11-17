package com.langhost.data

import com.langhost.data.model.DiscoverySourceStatus

/**
 * A privacy-safe aggregate outcome from an optional discovery source.
 * The value contains only the source's bounded result set, never raw packets
 * or exception details.
 */
internal data class DiscoverySourceResult<T>(
    val value: T,
    val status: DiscoverySourceStatus
)
