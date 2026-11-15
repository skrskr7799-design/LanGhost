package com.langhost.data

import com.langhost.data.model.PortAuditResult
import com.langhost.data.model.PortAuditStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.io.IOException

class PortAuditTest {
    @Test
    fun classifiesConnectionRefusalAsClosed() {
        assertEquals(
            PortAuditStatus.CLOSED,
            PortAuditResult.fromFailure(23, ConnectException("Connection refused")).status
        )
    }

    @Test
    fun classifiesConnectTimeoutAsFilteredRatherThanClosed() {
        assertEquals(
            PortAuditStatus.FILTERED,
            PortAuditResult.fromFailure(23, SocketTimeoutException("connect timed out")).status
        )
    }

    @Test
    fun classifiesRoutingAndOtherIoErrorsAsUnknown() {
        assertEquals(
            PortAuditStatus.UNKNOWN,
            PortAuditResult.fromFailure(23, NoRouteToHostException("No route to host")).status
        )
        assertEquals(
            PortAuditStatus.UNKNOWN,
            PortAuditResult.fromFailure(23, IOException("Unexpected socket failure")).status
        )
    }
}
