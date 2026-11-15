package com.langhost.data

object WifiSecurity {
    fun classify(capabilities: String): String {
        val value = capabilities.uppercase()
        if (value.isBlank()) return "Unknown"

        val isWep = value.contains("WEP")
        val isSae = value.contains("SAE") || value.contains("WPA3")
        val isPsk = value.contains("PSK")
        val isEap = value.contains("EAP")
        val isWpa3 = isSae
        val isWpa2 = value.contains("WPA2") ||
            (value.contains("RSN") && (isPsk || isEap))
        val isWpa = value.contains("WPA")

        return when {
            isWep -> "WEP"
            value.contains("OWE") -> "OWE"
            isWpa3 && isWpa2 -> "WPA2/WPA3"
            isWpa3 -> if (isEap) "WPA3-Enterprise" else "WPA3"
            isWpa2 -> if (isEap) "WPA2-Enterprise" else "WPA2"
            isWpa -> "WPA"
            value.contains("ESS") || value.contains("IBSS") -> "Open"
            else -> "Unknown"
        }
    }
}
