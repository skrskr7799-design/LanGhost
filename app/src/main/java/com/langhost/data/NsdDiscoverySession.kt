package com.langhost.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs one NSD listener session with a single, unconditional stop attempt.
 *
 * Startup, discovery and resolution grace share one bounded window. Stop is
 * attempted exactly once even when startup fails, times out or is cancelled.
 */
internal suspend fun runNsdDiscoverySession(
    startDiscovery: () -> Unit,
    awaitStarted: suspend () -> Boolean,
    runDiscoveryWindow: suspend () -> Unit,
    stopDiscovery: () -> Unit,
    awaitStopped: suspend () -> Unit,
    sessionTimeoutMs: Long,
    stopTimeoutMs: Long
): Boolean {
    return try {
        startDiscovery()
        withTimeoutOrNull(sessionTimeoutMs) {
            if (!awaitStarted()) {
                false
            } else {
                runDiscoveryWindow()
                true
            }
        } ?: false
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    } finally {
        // A failed start callback is not a separate stop path: cleanup owns
        // the one stop attempt for every session.
        try {
            stopDiscovery()
        } catch (_: Exception) {
            // Best-effort platform cleanup; preserve the discovery outcome.
        }
        try {
            withTimeoutOrNull(stopTimeoutMs) { awaitStopped() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The stop callback is advisory and bounded.
        }
    }
}
