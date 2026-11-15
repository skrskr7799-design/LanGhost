package com.langhost.data

import com.langhost.data.model.Device
import com.langhost.data.model.WifiNetwork

data class SecurityFinding(
    val severity: Severity,
    val title: String,
    val detail: String
) {
    enum class Severity { HIGH, MEDIUM, LOW, INFO }
}

data class SecurityReport(
    val score: Int, // 0-100
    val findings: List<SecurityFinding>,
    val evilTwins: List<WifiNetwork>
)

object SecurityAudit {

    fun analyze(
        devices: List<Device>,
        currentSsid: String,
        currentBssid: String,
        nearby: List<WifiNetwork>
    ): SecurityReport {
        val findings = mutableListOf<SecurityFinding>()

        // Dangerous open ports
        devices.filter { it.isOnline }.forEach { d ->
            val ports = d.openPorts.toSet()
            if (23 in ports) {
                findings += SecurityFinding(
                    SecurityFinding.Severity.HIGH,
                    "Открыт Telnet",
                    "${d.displayName} (${d.ip}) — незашифрованный удалённый доступ"
                )
            }
            if (445 in ports || 139 in ports) {
                findings += SecurityFinding(
                    SecurityFinding.Severity.MEDIUM,
                    "Открыт SMB",
                    "${d.displayName} (${d.ip}) — риск эксплойтов и утечки файлов"
                )
            }
            if (3389 in ports) {
                findings += SecurityFinding(
                    SecurityFinding.Severity.MEDIUM,
                    "Открыт RDP",
                    "${d.displayName} (${d.ip}) — убедитесь, что доступ ограничен"
                )
            }
            if (80 in ports && 443 !in ports && (d.isGateway || d.httpFingerprint.isNotBlank())) {
                findings += SecurityFinding(
                    SecurityFinding.Severity.LOW,
                    "Веб-админка без HTTPS",
                    "${d.displayName} (${d.ip}) — HTTP без шифрования"
                )
            }
        }

        // Evil twin: same SSID, different BSSID
        val evilTwins = if (currentSsid.isNotBlank() && currentSsid != "—") {
            nearby.filter {
                it.ssid.equals(currentSsid, ignoreCase = true) &&
                    it.bssid.isNotBlank() &&
                    !it.bssid.equals(currentBssid, ignoreCase = true)
            }
        } else emptyList()

        if (evilTwins.isNotEmpty()) {
            findings += SecurityFinding(
                SecurityFinding.Severity.HIGH,
                "Возможный Evil Twin",
                "SSID «$currentSsid» виден на ${evilTwins.size} других BSSID"
            )
        }

        // Weak neighbor networks with same name already covered; also flag Open/WEP nearby with high signal
        nearby.filter { it.security == "Open" || it.security == "WEP" }.take(3).forEach { n ->
            findings += SecurityFinding(
                SecurityFinding.Severity.LOW,
                "Слабая соседняя сеть",
                "${n.ssid.ifBlank { "скрытая" }} — ${n.security}, ${n.signalDbm} dBm"
            )
        }

        val high = findings.count { it.severity == SecurityFinding.Severity.HIGH }
        val med = findings.count { it.severity == SecurityFinding.Severity.MEDIUM }
        val low = findings.count { it.severity == SecurityFinding.Severity.LOW }
        val score = (100 - high * 25 - med * 12 - low * 4).coerceIn(0, 100)

        if (findings.isEmpty()) {
            findings += SecurityFinding(
                SecurityFinding.Severity.INFO,
                "Критических проблем не найдено",
                "Продолжайте периодически сканировать порты ключевых устройств"
            )
        }

        return SecurityReport(score, findings, evilTwins)
    }
}
