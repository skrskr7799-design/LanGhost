package com.langhost.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.langhost.data.NetworkRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Performs widget-triggered discovery as persistent work instead of holding a
 * BroadcastReceiver's short goAsync() execution window during a network scan.
 */
class WidgetScanWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = NetworkRepository.getInstance(applicationContext)
            repository.scanDevices()
            val onlineCount = repository.devices.first().count { it.isOnline }
            val manager = AppWidgetManager.getInstance(applicationContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(applicationContext, ScanWidgetProvider::class.java)
            )
            ids.forEach {
                ScanWidgetProvider.updateWidget(applicationContext, manager, it, onlineCount)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
