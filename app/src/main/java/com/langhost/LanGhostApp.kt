package com.langhost

import android.app.Application
import com.langhost.data.NotificationHelper

class LanGhostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}
