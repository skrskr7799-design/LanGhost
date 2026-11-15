package com.langhost.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Lightweight private receiver. WorkManager owns scan execution so Android
 * can safely stop/restart the process without losing an in-flight widget scan.
 */
class WidgetScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScanWidgetProvider.ACTION_SCAN) return

        val request = OneTimeWorkRequestBuilder<WidgetScanWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        const val WORK_NAME = "langhost_widget_scan"
    }
}
