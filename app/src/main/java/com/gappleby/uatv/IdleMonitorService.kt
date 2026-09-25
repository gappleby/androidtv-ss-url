package com.gappleby.uatv

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * Foreground service with two jobs:
 *
 * 1. Intercept DREAMING_STARTED — when any DreamService fires (including
 *    Amazon's own Backdrop screensaver on Fire TV), launch ScreensaverActivity
 *    on top so the user sees our WebView instead.
 *
 * 2. Periodically re-apply screensaver_components — Amazon occasionally resets
 *    this secure setting back to their own screensaver. Re-writing it every hour
 *    keeps our DreamService registered as the system screensaver while the
 *    DREAMING_STARTED intercept acts as the safety net if it hasn't been
 *    re-applied yet.
 */
class IdleMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val dreamReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_DREAMING_STARTED) {
                launchScreensaver()
            }
        }
    }

    private val reapplyRunnable = object : Runnable {
        override fun run() {
            reapplyScreensaverComponent()
            handler.postDelayed(this, REAPPLY_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())

        val filter = IntentFilter(Intent.ACTION_DREAMING_STARTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dreamReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dreamReceiver, filter)
        }

        // Re-apply immediately on start, then on the hourly schedule
        reapplyScreensaverComponent()
        handler.postDelayed(reapplyRunnable, REAPPLY_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY // restart automatically if the OS kills the service

    override fun onDestroy() {
        handler.removeCallbacks(reapplyRunnable)
        try { unregisterReceiver(dreamReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Screensaver intercept ────────────────────────────────────────────────

    private fun launchScreensaver() {
        startActivity(
            Intent(this, ScreensaverActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        )
    }

    // ── Secure settings re-apply ─────────────────────────────────────────────

    private fun reapplyScreensaverComponent() {
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
            != PackageManager.PERMISSION_GRANTED
        ) return

        try {
            val component = ComponentName(this, ScreensaverDreamService::class.java).flattenToString()
            Settings.Secure.putString(contentResolver, "screensaver_components", component)
            Settings.Secure.putInt(contentResolver, "screensaver_enabled", 1)
            Settings.Secure.putInt(contentResolver, "screensaver_activate_on_sleep", 1)
            Settings.Secure.putInt(contentResolver, "screensaver_activate_on_dock", 1)
        } catch (_: Exception) {}
    }

    // ── Foreground notification ──────────────────────────────────────────────

    private fun buildNotification() = run {
        createNotificationChannel()

        val settingsIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_monitoring))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(settingsIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "screensaver_monitor"
        private const val NOTIFICATION_ID = 1
        private const val REAPPLY_INTERVAL_MS = 60 * 60 * 1000L // 1 hour
    }
}
