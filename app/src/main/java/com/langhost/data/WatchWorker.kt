package com.langhost.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class WatchWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val appSettings = AppSettings(applicationContext)
            val settings = appSettings.settingsFlow.first()

            if (!settings.watchModeEnabled) {
                return Result.success()
            }

            val repository = NetworkRepository.getInstance(applicationContext)
            val knownBefore = repository.getKnownIds()

            repository.scanDevices()

            val after = repository.devices.value.filter { it.isOnline }
            val newDevices = after.filter {
                it.stableId !in knownBefore && !it.isLocalDevice && !it.isGateway
            }

            if (settings.notifyNewDevices && newDevices.isNotEmpty() && knownBefore.isNotEmpty()) {
                if (newDevices.size == 1) {
                    val d = newDevices.first()
                    NotificationHelper.notifyNewDevice(
                        applicationContext,
                        d.displayName,
                        d.ip
                    )
                } else {
                    NotificationHelper.notifyMultipleNew(applicationContext, newDevices.size)
                }
            }

            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "langhost_watch"

        fun start(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
