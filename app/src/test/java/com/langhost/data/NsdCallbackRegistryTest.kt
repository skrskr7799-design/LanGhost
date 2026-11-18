package com.langhost.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class NsdCallbackRegistryTest {
    @Test
    fun trackAndReleaseMaintainActiveCallbackSet() {
        val registry = NsdCallbackRegistry<String>()

        assertTrue(registry.track("callback-1"))
        assertTrue(registry.track("callback-2"))
        assertEquals(2, registry.size())

        assertTrue(registry.release("callback-1"))
        assertFalse(registry.release("callback-1"))
        assertEquals(1, registry.size())
        assertFalse(registry.isEmpty())
    }

    @Test
    fun closeReturnsOutstandingCallbacksAndRejectsLateRegistrations() {
        val registry = NsdCallbackRegistry<String>()
        registry.track("callback-1")
        registry.track("callback-2")

        assertEquals(setOf("callback-1", "callback-2"), registry.closeAndSnapshot().toSet())
        assertTrue(registry.isClosed())
        assertFalse(registry.track("late-callback"))
        assertEquals(2, registry.size())
    }

    @Test
    fun callbackCanStillReleaseAfterRegistryIsClosed() {
        val registry = NsdCallbackRegistry<String>()
        registry.track("callback")
        registry.closeAndSnapshot()

        assertTrue(registry.release("callback"))
        assertTrue(registry.isEmpty())
    }

    @Test
    fun registrationActionRunsBeforeCloseCanSnapshotCallback() {
        val registry = NsdCallbackRegistry<String>()
        val events = mutableListOf<String>()

        val registered = registry.trackAndRun("callback") {
            events += "registered"
            assertEquals(listOf("callback"), registry.closeAndSnapshot())
            events += "closed"
        }

        assertTrue(registered)
        assertEquals(listOf("registered", "closed"), events)
        assertTrue(registry.isClosed())
    }

    @Test
    fun closeWaitsForInFlightRegistrationBeforeTakingSnapshot() {
        val registry = NsdCallbackRegistry<String>()
        val registrationEntered = CountDownLatch(1)
        val allowRegistrationToFinish = CountDownLatch(1)
        val registrationResult = AtomicReference<Boolean>()
        val snapshot = AtomicReference<List<String>>()

        val registrationThread = Thread {
            registrationResult.set(
                registry.trackAndRun("callback") {
                    registrationEntered.countDown()
                    check(allowRegistrationToFinish.await(2, TimeUnit.SECONDS))
                }
            )
        }
        registrationThread.start()
        assertTrue("Registration did not enter its critical section", registrationEntered.await(2, TimeUnit.SECONDS))

        val closeThread = Thread {
            snapshot.set(registry.closeAndSnapshot())
        }
        closeThread.start()

        // closeAndSnapshot must block on the registry monitor until registration exits.
        assertEquals(null, snapshot.get())
        allowRegistrationToFinish.countDown()
        registrationThread.join(2_000)
        closeThread.join(2_000)

        assertFalse(registrationThread.isAlive)
        assertFalse(closeThread.isAlive)
        assertEquals(true, registrationResult.get())
        assertEquals(listOf("callback"), snapshot.get())
        assertTrue(registry.isClosed())
    }

    @Test
    fun closedRegistryDoesNotExecuteLateRegistrationAction() {
        val registry = NsdCallbackRegistry<String>()
        registry.closeAndSnapshot()
        var registrationAttempted = false

        val registered = registry.trackAndRun("late") {
            registrationAttempted = true
        }

        assertFalse(registered)
        assertFalse(registrationAttempted)
        assertTrue(registry.isEmpty())
    }

}
