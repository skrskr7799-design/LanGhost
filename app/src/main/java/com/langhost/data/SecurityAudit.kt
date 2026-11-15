package com.langhost.data

import com.langhost.data.model.Device
import com.langhost.data.model.ScanCoverage
import com.langhost.data.model.WifiNetwork

data class SecurityFinding(
    val severity: Severity,
    val title: String,
    val detail: String
) {
    enum class Severity { HIGH, MEDIUM, LOW, INFO }
}

data class SecurityReport(
    /** Null means there is not enough completed scan evidence to calculate a score. */
    val score: Int?,
    val findings: List<SecurityFinding>,
    /** Same-SSID access points with a clearly different, known security family. Not proof of an evil twin. */
    val securityMismatches: List<WifiNetwork>,
    val coveragePercent: Int,
    val deviceScanCompleted: Boolean
)

object SecurityAudit {

    fun analyze(
        devices: List<Device>,
        currentSsid: String,
        currentBssid: String,
        nearby: List<WifiNetwork>,
        currentSecurity: String = "—",
        scanCoverage: ScanCoverage = ScanCoverage(),
        nearbyScanCompleted: Boolean = false
    ): SecurityReport {
        val findings = mutableListOf<SecurityFinding>()

        when (securityFamily(currentSecurity)) {
            "OPEN" -> findings += SecurityFinding(
                SecurityFinding.Severity.HIGH,
                "Текущая Wi-Fi сеть не использует шифрование",
                "Трафик в этой сети может быть доступен другим участникам. Используйте защищённую сеть или доверенный VPN."
            )
            "WEP" -> findings += SecurityFinding(
                SecurityFinding.Severity.HIGH,
                "Используется устаревший WEP",
                "WEP считается небезопасным. Переключите точку доступа на WPA2-AES или WPA3."
            )
            "WPA" -> findings += SecurityFinding(
                SecurityFinding.Severity.MEDIUM,
                "Используется устаревший WPA",
                "Предпочтительны WPA2-AES или WPA3. Android может не раскрывать точный режим для некоторых PSK-сетей."
            )
        }

        // These checks describe reachable services, not proof that a device is exploitable.
        devices.filter { it.isOnline }.forEach { device ->
            val ports = device.openPorts.toSet()
            if (23 in ports) {
                findings += SecurityFinding(
                    SecurityFinding.Severity.HIGH,
                    "Доступен Telnet",
                    "${device.displayName} (${device.ip}) принимает TCP-соединения на порту 23. Telnet не шифрует сеанс."
                )
            }
            if (445 in ports || 139 in ports) {
                findings += SecurityFinding(
                    SecurityFinding.Severity.MEDIUM,
                    "Доступен SMB",
                    "${device.displayName} (${device.ip}) предоставляет SMB в проверяемой сети. Проверьте правила общего доступа и firewall."
                )
            }
            if (3389 in ports) {
                findings += SecurityFinding(
                    SecurityFinding.Severity.MEDIUM,
                    "Доступен RDP",
                    "${device.displayName} (${device.ip}) принимает RDP-соединения. Ограничьте доступ доверенными устройствами."
                )
            }
            if (80 in ports && device.isGateway && 443 !in ports) {
                findings += SecurityFinding(
                    SecurityFinding.Severity.LOW,
                    "HTTP доступен на шлюзе",
                    "${device.displayName} (${device.ip}) отвечает на HTTP, а порт 443 не подтвердился. Это не доказывает, что интерфейс администратора передаёт данные без защиты."
                )
            }
        }

        // A second BSSID with the same SSID is normal in mesh and multi-AP networks.
        // Only report a difference when both security modes are known and clearly distinct.
        val currentFamily = securityFamily(currentSecurity)
        val securityMismatches = if (
            currentSsid.isNotBlank() && currentSsid != "—" && currentFamily != "UNKNOWN"
        ) {
            nearby.filter { accessPoint ->
                val candidateFamily = securityFamily(accessPoint.security)
                accessPoint.ssid.equals(currentSsid, ignoreCase = true) &&
                    accessPoint.bssid.isNotBlank() &&
                    !accessPoint.bssid.equals(currentBssid, ignoreCase = true) &&
                    candidateFamily != "UNKNOWN" &&
                    candidateFamily != currentFamily
            }
        } else {
            emptyList()
        }

        if (securityMismatches.isNotEmpty()) {
            findings += SecurityFinding(
                SecurityFinding.Severity.LOW,
                "Различаются режимы защиты точек с одинаковым SSID",
                "Обнаружено точек: ${securityMismatches.size}. Это может быть особенностью гостевой сети или переходной конфигурации mesh; само по себе не является доказательством Evil Twin."
            )
        }

        val deviceScanCompleted = scanCoverage.checkedAddresses > 0
        if (!deviceScanCompleted) {
            findings += SecurityFinding(
                SecurityFinding.Severity.INFO,
                "Недостаточно данных для общей оценки",
                "Запустите сканирование локальной сети. Пока проверены только доступные сведения о текущем Wi-Fi подключении."
            )
        } else if (findings.none { it.severity != SecurityFinding.Severity.INFO }) {
            findings += SecurityFinding(
                SecurityFinding.Severity.INFO,
                "Подтверждённых проблем не обнаружено",
                "Это относится только к проверенным адресам и признакам; результат не является гарантией безопасности."
            )
        }

        if (deviceScanCompleted && scanCoverage.truncated) {
            findings += SecurityFinding(
                SecurityFinding.Severity.INFO,
                "Проверена не вся подсеть",
                "Проверено ${scanCoverage.checkedAddresses} из ${scanCoverage.totalUsableAddresses} доступных адресов. Часть устройств могла остаться вне проверки."
            )
        }
        if (!nearbyScanCompleted) {
            findings += SecurityFinding(
                SecurityFinding.Severity.INFO,
                "Окружение Wi-Fi не проверено",
                "Сравнение соседних точек доступа недоступно, пока не выполнено свежее сканирование Wi-Fi."
            )
        }

        val high = findings.count { it.severity == SecurityFinding.Severity.HIGH }
        val medium = findings.count { it.severity == SecurityFinding.Severity.MEDIUM }
        val low = findings.count { it.severity == SecurityFinding.Severity.LOW }
        val score = if (deviceScanCompleted) {
            (100 - high * 25 - medium * 12 - low * 4).coerceIn(0, 100)
        } else {
            null
        }

        return SecurityReport(
            score = score,
            findings = findings,
            securityMismatches = securityMismatches,
            coveragePercent = scanCoverage.coveragePercent,
            deviceScanCompleted = deviceScanCompleted
        )
    }

    private fun securityFamily(value: String): String {
        val normalized = value.trim().uppercase()
        return when {
            normalized == "OPEN" -> "OPEN"
            normalized == "WEP" -> "WEP"
            normalized.contains("WPA3") || normalized.contains("SAE") -> "WPA3"
            normalized == "WPA" -> "WPA"
            normalized == "WPA2" || normalized == "WPA2-PSK" -> "WPA2"
            // Android's generic PSK/EAP values do not reliably distinguish WPA from WPA2.
            normalized.contains("PSK") || normalized.contains("ENTERPRISE") ||
                normalized == "OWE" || normalized == "—" || normalized.isBlank() -> "UNKNOWN"
            else -> "UNKNOWN"
        }
    }
}
