package com.yang.player.online

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.media3.common.util.UnstableApi

@UnstableApi
class YouTubeDownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloads = YouTubeDownloads.get(this)
        startForeground(YouTubeDownloads.NOTIFICATION_ID, downloads.notification())
        val started = downloads.runPending {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelfResult(startId)
        }
        if (!started) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        YouTubeDownloads.get(this).cancel()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }
}
