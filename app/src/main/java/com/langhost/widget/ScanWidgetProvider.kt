package com.langhost.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.langhost.MainActivity
import com.langhost.R
import com.langhost.data.NetworkRepository

class ScanWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val repository = NetworkRepository.getInstance(context)
        val count = if (repository.lastScanTime.value > 0L) {
            repository.devices.value.count { it.isOnline }
        } else {
            null
        }
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id, count)
        }
    }

    companion object {
        const val ACTION_SCAN = "com.langhost.widget.ACTION_SCAN"

        fun updateWidget(context: Context, manager: AppWidgetManager, id: Int, onlineCount: Int?) {
            val views = RemoteViews(context.packageName, R.layout.widget_scan)
            views.setTextViewText(R.id.widget_count, onlineCount?.toString() ?: "—")
            views.setTextViewText(R.id.widget_label, if (onlineCount == null) "нет данных" else "онлайн")

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            val scanIntent = Intent(context, WidgetScanReceiver::class.java).apply { action = ACTION_SCAN }
            val scanPi = PendingIntent.getBroadcast(
                context, 1, scanIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_scan_btn, scanPi)

            manager.updateAppWidget(id, views)
        }
    }
}
