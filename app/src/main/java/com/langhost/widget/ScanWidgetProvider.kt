package com.langhost.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.langhost.MainActivity
import com.langhost.R
import com.langhost.data.NetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScanWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val count = try {
            NetworkRepository.getInstance(context).devices.value.count { it.isOnline }
        } catch (_: Exception) {
            0
        }
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id, count)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_SCAN) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch {
                try {
                    val repo = NetworkRepository.getInstance(context)
                    repo.scanDevices()
                    val count = repo.devices.first().count { it.isOnline }
                    val mgr = AppWidgetManager.getInstance(context)
                    val ids = mgr.getAppWidgetIds(ComponentName(context, ScanWidgetProvider::class.java))
                    ids.forEach { updateWidget(context, mgr, it, count) }
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        const val ACTION_SCAN = "com.langhost.widget.ACTION_SCAN"

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int, onlineCount: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_scan)
            views.setTextViewText(R.id.widget_count, onlineCount.toString())
            views.setTextViewText(R.id.widget_label, "онлайн")

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            val scanIntent = Intent(context, ScanWidgetProvider::class.java).apply { action = ACTION_SCAN }
            val scanPi = PendingIntent.getBroadcast(
                context, 1, scanIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_scan_btn, scanPi)

            manager.updateAppWidget(id, views)
        }
    }
}
