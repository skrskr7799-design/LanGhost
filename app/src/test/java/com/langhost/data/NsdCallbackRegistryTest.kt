package com.langhost.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
