package com.langhost.data.model

/**
 * Aggregate result of a discovery source. NO_RESPONSES means the source
 * completed normally; UNAVAILABLE means it could not be used for this scan.
 * NOT_REPORTED is reserved for sources whose platform adapter does not yet
 * expose enough lifecycle information to distinguish those outcomes.
 */
enum class DiscoverySourceStatus {
    NOT_RUN,
    RESPONDED,
    PARTIAL,
    NO_RESPONSES,
    UNAVAILABLE,
    NOT_REPORTED
}
