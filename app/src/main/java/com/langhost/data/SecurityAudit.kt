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
    /** Null means there is not enough fresh, completed scan evidence to calculate a score. */
    val score: Int?,
    val findings: List<SecurityFinding>,
    /** Same-SSID access points with a clearly different, known security family. Not proof of an evil twin. */
    val securityMismatches: List<WifiNetwork>,
    val coveragePercent: Int,
    val deviceScanCompleted: Boolean,
    val serviceScanCompleted: Boolean
)

object SecurityAudit {
    private const val EVIDENCE_FRESHNESS_MS = 5 * 60 * 1000L

    fun analyze(
        devices: List<Device>,
        currentSsid: String,
        currentBssid: String,
        nearby: List<WifiNetwork>,
        currentSecurity: String = "—",
        scanCoverage: ScanCoverage = ScanCoverage(),
        nearbyScanCompleted: Boolean = false,
        serviceScanCompleted: Boolean = false,
        currentNetworkScope: String? = null,
        nearbyScanTime: Long = 0L,
        serviceScanTime: Long = 0L,
        now: Long = System.currentTimeMillis()
    ): SecurityReport {
        // Legacy/test callers may omit currentNetworkScope. The production SecurityScreen
        // always supplies it, which enables strict scope and freshness checks.
        val requiresFreshness = currentNetworkScope != null
        val scopeMatches = currentNetworkScope == null ||
            (currentNetworkScope.isNotBlank() && scanCoverage.networkScope == currentNetworkScope)
        val deviceScanFresh = !requiresFreshness || isFresh(scanCoverage.scannedAt, now)
        val deviceScanCompleted = scanCoverage.checkedAddresses > 0 && scopeMatches && deviceScanFresh
        val effectiveServiceScanCompleted = deviceScanCompleted && serviceScanCompleted &&
            (!requiresFreshness || isFresh(serviceScanTime, now))
        val effectiveNearbyScanCompleted = nearbyScanCompleted &&
            (!requiresFreshness || isFresh(nearbyScanTime, now))

        val findings = mutableListOf<SecurityFinding>()
        val currentSecurityFamilies = securityFamilies(currentSecurity)
        when {
            "OPEN" in currentSecurityFamilies -> findings += SecurityFinding(
                SecurityFinding.Severity.HIGH,
                "Текущая Wi-Fi сеть не использует шифрование",
                "Трафик в этой сети может быть доступен другим участникам. Используйте защищённую сеть или доверенный VPN."
            )
            "WEP" in currentSecurityFamilies -> findings += SecurityFinding(
                SecurityFinding.Severity.HIGH,
                "Используется устаревший WEP",
                "WEP считается небезопасным. Переключите точку доступа на WPA2-AES или WPA3."
            )
            "WPA" in currentSecurityFamilies -> findings += SecurityFinding(
                SecurityFinding.Severity.MEDIUM,
                "Используется устаревший WPA",
                "Предпочтительны WPA2-AES или WPA3. Android может не раскрывать точный режим для некоторых PSK-сетей."
            )
        }

        // Port findings are shown only when a fresh service scan belongs to this network.
        if (effectiveServiceScanCompleted) {
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
        }

        // A second BSSID with the same SSID is normal in mesh and multi-AP networks.
        // Only report a difference when the nearby scan is fresh and both security modes are known.
        val currentFamilies = securityFamilies(currentSecurity)
        val securityMismatches = if (
            effectiveNearbyScanCompleted &&
            currentSsid.isNotBlank() && currentSsid != "—" && currentFamilies.isNotEmpty()
        ) {
            nearby.filter { accessPoint ->
                val candidateFamilies = securityFamilies(accessPoint.security)
                accessPoint.ssid.equals(currentSsid, ignoreCase = true) &&
                    accessPoint.bssid.isNotBlank() &&
                    !accessPoint.bssid.equals(currentBssid, ignoreCase = true) &&
                    candidateFamilies.isNotEmpty() &&
                    candidateFamilies.intersect(currentFamilies).isEmpty()
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

        if (!deviceScanCompleted) {
            findings += SecurityFinding(
                SecurityFinding.Severity.INFO,
                "Недостаточно данных для общей оценки",
                "Запустите сканирование локальной сети. Пока проверены только доступные сведения о текущем Wi-Fi подключении."
            )
        } else if (
            effectiveServiceScanCompleted && effectiveNearbyScanCompleted &&
            findings.none { it.severity != SecurityFinding.Severity.INFO }
        ) {
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
        if (deviceScanCompleted && !effectiveServiceScanCompleted) {
            findings += SecurityFinding(
                SecurityFinding.Severity.INFO,
                "Проверка сервисов не выполнена или устарела",
                "Сканирование подсети обнаруживает устройства, но не проверяет полный набор рискованных TCP-сервисов. Запустите отдельную проверку устройств и портов."
            )
        }
        if (!effectiveNearbyScanCompleted) {
            findings += SecurityFinding(
                SecurityFinding.Severity.INFO,
                "Окружение Wi-Fi не проверено или результаты устарели",
                "Сравнение соседних точек доступа доступно только после свежего сканирования Wi-Fi."
            )
        }

        val high = findings.count { it.severity == SecurityFinding.Severity.HIGH }
        val medium = findings.count { it.severity == SecurityFinding.Severity.MEDIUM }
        val low = findings.count { it.severity == SecurityFinding.Severity.LOW }
        val score = if (deviceScanCompleted && effectiveServiceScanCompleted && effectiveNearbyScanCompleted) {
            (100 - high * 25 - medium * 12 - low * 4).coerceIn(0, 100)
        } else {
            null
        }

        return SecurityReport(
            score = score,
            findings = findings,
            securityMismatches = securityMismatches,
            coveragePercent = scanCoverage.coveragePercent,
            deviceScanCompleted = deviceScanCompleted,
            serviceScanCompleted = effectiveServiceScanCompleted
        )
    }

    private fun isFresh(timestamp: Long, now: Long): Boolean =
        timestamp > 0L && now >= timestamp && now - timestamp <= EVIDENCE_FRESHNESS_MS

    private fun securityFamilies(value: String): Set<String> {
        val normalized = value.trim().uppercase()
        return when {
            normalized == "OPEN" -> setOf("OPEN")
            normalized == "WEP" -> setOf("WEP")
            normalized == "OWE" -> setOf("OWE")
            normalized.contains("WPA2/WPA3") -> setOf("WPA2", "WPA3")
            normalized.contains("WPA3") || normalized.contains("SAE") -> setOf("WPA3")
            normalized == "WPA2" || normalized.startsWith("WPA2-") -> setOf("WPA2")
            normalized == "WPA" -> setOf("WPA")
            else -> emptySet()
        }
    }
}
