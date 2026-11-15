package com.langhost.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import com.langhost.data.NetworkRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Private receiver for the widget's explicit scan PendingIntent.
 * Kept separate from the exported AppWidgetProvider so other apps cannot trigger scans.
 */
class WidgetScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScanWidgetProvider.ACTION_SCAN) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = NetworkRepository.getInstance(appContext)
                repository.scanDevices()
                val onlineCount = repository.devices.first().count { it.isOnline }
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(
                    ComponentName(appContext, ScanWidgetProvider::class.java)
                )
                ids.forEach { ScanWidgetProvider.updateWidget(appContext, manager, it, onlineCount) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Widget refresh is best-effort; the app remains usable if scanning fails.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
