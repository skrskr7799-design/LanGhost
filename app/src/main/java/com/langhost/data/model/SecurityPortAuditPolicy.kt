package com.langhost.data.model

/**
 * Single source of truth for the TCP ports required by the security service audit.
 * Keep scoring completeness and repository probing aligned through this policy.
 */
object SecurityPortAuditPolicy {
    val ports: List<Int> = listOf(23, 80, 443, 139, 445, 3389)
}
