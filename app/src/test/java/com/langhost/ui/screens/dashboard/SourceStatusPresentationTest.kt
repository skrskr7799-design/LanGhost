package com.langhost.ui.screens.dashboard

import com.langhost.data.model.DiscoverySourceStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceStatusPresentationTest {
    @Test
    fun everyDiscoveryStatusHasAnExplicitRussianLabel() {
        assertEquals("Не запускался", discoverySourceStatusLabel(DiscoverySourceStatus.NOT_RUN))
        assertEquals("Есть ответы", discoverySourceStatusLabel(DiscoverySourceStatus.RESPONDED))
        assertEquals("Частично доступен", discoverySourceStatusLabel(DiscoverySourceStatus.PARTIAL))
        assertEquals("Ответов нет", discoverySourceStatusLabel(DiscoverySourceStatus.NO_RESPONSES))
        assertEquals("Недоступен", discoverySourceStatusLabel(DiscoverySourceStatus.UNAVAILABLE))
        assertEquals("Нет данных", discoverySourceStatusLabel(DiscoverySourceStatus.NOT_REPORTED))
    }
}
