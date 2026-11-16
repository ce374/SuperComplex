package com.supercomplex.app.download

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.supercomplex.app.MainActivity
import com.supercomplex.app.R
import com.supercomplex.app.SuperComplexApp
import com.supercomplex.app.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that processes the download queue one item at a time.
 * Driven by intents from the UI: ACTION_QUEUE (drain queue) or
 * ACTION_DOWNLOAD_ONE with an extra rowId.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var drainJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                drainJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_QUEUE -> startDrain()
            ACTION_DOWNLOAD_ONE -> {
                val rowId = intent.getLongExtra(EXTRA_ROW_ID, -1L)
                if (rowId > 0) {
                    startForeground(NOTIF_ID, baseNotification("Downloading…"))
                    scope.launch {
                        DownloadManager.get(this@DownloadService).runDownload(rowId)
                        // after one finishes, also drain the rest of the queue
                        startDrain()
                    }
                }
            }
            else -> startDrain()
        }
        return START_STICKY
    }

    private fun startDrain() {
        startForeground(NOTIF_ID, baseNotification("Processing queue…"))
        if (drainJob?.isActive == true) return
        drainJob = scope.launch {
            while (true) {
                val next = AppDatabase.get().downloads().queue().firstOrNull() ?: break
                if (next.status == "DONE") continue
                DownloadManager.get(this@DownloadService).runDownload(next.rowId)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun baseNotification(text: String): Notification {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        else PendingIntent.FLAG_UPDATE_CURRENT
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            flags
        )
        return NotificationCompat.Builder(this, SuperComplexApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .build()
    }

    companion object {
        const val ACTION_QUEUE = "com.supercomplex.app.QUEUE"
        const val ACTION_DOWNLOAD_ONE = "com.supercomplex.app.DOWNLOAD_ONE"
        const val ACTION_STOP = "com.supercomplex.app.STOP"
        const val EXTRA_ROW_ID = "row_id"
        private const val NOTIF_ID = 4242

        fun startQueue(context: Context) {
            val i = Intent(context, DownloadService::class.java).setAction(ACTION_QUEUE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun runOne(context: Context, rowId: Long) {
            val i = Intent(context, DownloadService::class.java)
                .setAction(ACTION_DOWNLOAD_ONE)
                .putExtra(EXTRA_ROW_ID, rowId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, DownloadService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }
}
