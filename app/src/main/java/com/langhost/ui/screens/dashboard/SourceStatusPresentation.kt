package com.langhost.ui.screens.dashboard

import com.langhost.data.model.DiscoverySourceStatus

internal fun discoverySourceStatusLabel(status: DiscoverySourceStatus): String = when (status) {
    DiscoverySourceStatus.NOT_RUN -> "Не запускался"
    DiscoverySourceStatus.RESPONDED -> "Есть ответы"
    DiscoverySourceStatus.PARTIAL -> "Частично доступен"
    DiscoverySourceStatus.NO_RESPONSES -> "Ответов нет"
    DiscoverySourceStatus.UNAVAILABLE -> "Недоступен"
    DiscoverySourceStatus.NOT_REPORTED -> "Нет данных"
}
