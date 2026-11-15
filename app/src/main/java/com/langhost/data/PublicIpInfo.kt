package com.langhost.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
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

    suspend fun fetch(): PublicNetworkInfo = withContext(Dispatchers.IO) {
        withTimeoutOrNull(5000L) {
            try {
                // ipapi.co — no key required for basic use
                val url = URL("https://ipapi.co/json/")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "LanGhost/0.3")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(body)
                PublicNetworkInfo(
                    ip = json.optString("ip", ""),
                    isp = json.optString("org", json.optString("asn", "")),
                    org = json.optString("org", ""),
                    country = json.optString("country_name", json.optString("country", "")),
                    city = json.optString("city", ""),
                    region = json.optString("region", "")
                )
            } catch (_: Exception) {
                // Fallback: plain IP only
                try {
                    val url = URL("https://api.ipify.org?format=json")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 3000
                        readTimeout = 3000
                    }
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    PublicNetworkInfo(ip = JSONObject(body).optString("ip", ""))
                } catch (_: Exception) {
                    PublicNetworkInfo()
                }
            }
        } ?: PublicNetworkInfo()
    }
}
