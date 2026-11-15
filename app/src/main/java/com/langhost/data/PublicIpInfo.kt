package com.langhost.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class PublicNetworkInfo(
    val ip: String = "",
    val isp: String = "",
    val org: String = "",
    val country: String = "",
    val city: String = "",
    val region: String = ""
)

object PublicIpLookup {
    internal const val MAX_RESPONSE_BYTES = 16 * 1024

    suspend fun fetch(): PublicNetworkInfo = withContext(Dispatchers.IO) {
        withTimeoutOrNull(5_500L) {
            val primary = try {
                request("https://ipapi.co/json/", 2_500)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            val parsedPrimary = if (primary == null) null else {
                try {
                    parsePrimaryResponse(primary).takeIf { it.ip.isNotBlank() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
            parsedPrimary ?: fallback()
        } ?: PublicNetworkInfo()
    }

    private suspend fun fallback(): PublicNetworkInfo {
        currentCoroutineContext().ensureActive()
        return try {
            parseFallbackResponse(request("https://api.ipify.org?format=json", 1_500))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PublicNetworkInfo()
        }
    }

    private fun request(address: String, timeoutMs: Int): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "GET"
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("User-Agent", "LanGhost/0.4")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "close")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("Unexpected HTTP status: $status")
            return connection.inputStream.use { readLimitedText(it, MAX_RESPONSE_BYTES) }
        } finally {
            connection.disconnect()
        }
    }

    internal fun readLimitedText(input: InputStream, maxBytes: Int): String {
        require(maxBytes > 0)
        val output = ByteArrayOutputStream(minOf(maxBytes, 4096))
        val buffer = ByteArray(2048)
        var total = 0
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, maxBytes - total + 1))
            if (count < 0) break
            total += count
            if (total > maxBytes) throw IllegalStateException("Response exceeds $maxBytes bytes")
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    internal fun parsePrimaryResponse(body: String): PublicNetworkInfo {
        val json = JSONObject(body)
        return PublicNetworkInfo(
            ip = json.optString("ip", ""),
            isp = json.optString("org", json.optString("asn", "")),
            org = json.optString("org", ""),
            country = json.optString("country_name", json.optString("country", "")),
            city = json.optString("city", ""),
            region = json.optString("region", "")
        )
    }

    internal fun parseFallbackResponse(body: String): PublicNetworkInfo =
        PublicNetworkInfo(ip = JSONObject(body).optString("ip", ""))
}
