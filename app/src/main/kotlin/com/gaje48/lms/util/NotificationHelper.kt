package com.gaje48.lms.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class NotificationHelper(private val context: Context) {
    private companion object {
        const val CHANNEL_ID = "lms"
        const val CHANNEL_NAME = "ELemES"
    }

    private val notificationManager = context.getSystemService(NotificationManager::class.java)
        .apply {
            createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
    private val lastNotifUpdate = mutableMapOf<Int, Long>()

    private fun notifBuilder() = Notification.Builder(context, CHANNEL_ID)

    fun showDownloadStarted(notifId: Int) {
        notifBuilder()
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Menyiapkan Download...")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showDownloadProgress(notifId: Int, fileName: String, progress: Float) {
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val now = System.currentTimeMillis()

        if (now - (lastNotifUpdate[notifId] ?: 0) < 500) return
        lastNotifUpdate[notifId] = now

        notifBuilder()
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Mengunduh File...")
            .setContentText("$fileName - $percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showDownloadSuccess(notifId: Int, fileName: String) {
        lastNotifUpdate.remove(notifId)

        notifBuilder()
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Selesai!")
            .setContentText(fileName)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showDownloadFailure(notifId: Int, message: String) {
        lastNotifUpdate.remove(notifId)

        notifBuilder()
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Gagal")
            .setContentText(message)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showUploadStarted(notifId: Int) {
        notifBuilder()
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Menyiapkan Upload...")
            .setProgress(0, 0, true)
            .setOngoing(true)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showUploadProgress(notifId: Int, fileName: String, progress: Float) {
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val now = System.currentTimeMillis()

        if (now - (lastNotifUpdate[notifId] ?: 0) < 500) return
        lastNotifUpdate[notifId] = now

        notifBuilder()
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Mengunggah Tugas...")
            .setContentText("$fileName - $percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showUploadSuccess(notifId: Int, fileName: String) {
        lastNotifUpdate.remove(notifId)

        notifBuilder()
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Upload Selesai!")
            .setContentText(fileName)
            .also { notificationManager.notify(notifId, it.build()) }
    }

    fun showUploadFailure(notifId: Int, message: String) {
        lastNotifUpdate.remove(notifId)

        notifBuilder()
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Upload Gagal")
            .setContentText(message)
            .also { notificationManager.notify(notifId, it.build()) }
    }
}