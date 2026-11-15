package com.langhost.data.model

import java.net.ConnectException
import java.net.SocketTimeoutException

/** Result of one TCP connect probe. A timeout is not evidence that a port is closed. */
enum class PortAuditStatus {
    OPEN,
    CLOSED,
    FILTERED,
    UNKNOWN
}

data class PortAuditResult(
    val port: Int,
    val status: PortAuditStatus,
    val latencyMs: Long? = null
) {
    companion object {
        fun fromFailure(port: Int, error: Exception, latencyMs: Long? = null): PortAuditResult {
            val status = when (error) {
                is ConnectException -> PortAuditStatus.CLOSED
                is SocketTimeoutException -> PortAuditStatus.FILTERED
                else -> PortAuditStatus.UNKNOWN
            }
            return PortAuditResult(port, status, latencyMs)
        }
    }
}
