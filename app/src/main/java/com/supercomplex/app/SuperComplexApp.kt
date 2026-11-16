package com.supercomplex.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.supercomplex.app.data.AppDatabase

class SuperComplexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        AppDatabase.init(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Active download progress" }
            )
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_STATUS,
                    "Status",
                    NotificationManager.IMPORTANCE_MIN
                ).apply { description = "Background status updates" }
            )
        }
    }

    companion object {
        lateinit var instance: SuperComplexApp
            private set
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_STATUS = "status"
    }
}
