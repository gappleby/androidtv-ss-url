package com.gappleby.androidtvss

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: AppPreferences
    private lateinit var editUrl: EditText
    private lateinit var editSleepDuration: EditText
    private lateinit var editScreenTimeout: EditText
    private lateinit var textStatus: TextView
    private lateinit var btnPermission: Button
    private lateinit var btnSetSystemScreensaver: Button
    private lateinit var textAdbHint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = AppPreferences(this)

        editUrl = findViewById(R.id.edit_url)
        editSleepDuration = findViewById(R.id.edit_sleep_duration)
        editScreenTimeout = findViewById(R.id.edit_screen_timeout)
        textStatus = findViewById(R.id.text_status)
        btnPermission = findViewById(R.id.btn_request_permission)
        btnSetSystemScreensaver = findViewById(R.id.btn_set_system_screensaver)
        textAdbHint = findViewById(R.id.text_adb_hint)

        editUrl.setText(prefs.url)
        editSleepDuration.setText(prefs.sleepDurationMinutes.toString())
        editScreenTimeout.setText(prefs.screenTimeoutMinutes.toString())

        // Ensure the monitor service is always running
        ContextCompat.startForegroundService(this, Intent(this, IdleMonitorService::class.java))

        findViewById<Button>(R.id.btn_save).setOnClickListener { saveSettings() }
        findViewById<Button>(R.id.btn_test).setOnClickListener { testUrl() }
        findViewById<Button>(R.id.btn_launch_screensaver).setOnClickListener { launchScreensaver() }
        findViewById<Button>(R.id.btn_system_settings).setOnClickListener { openScreensaverSettings() }
        btnSetSystemScreensaver.setOnClickListener { applySystemScreensaverComponent() }

        btnPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (_: Exception) {
                    showStatus(getString(R.string.error_permission_screen_unavailable), error = true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionButton()
        refreshSystemScreensaverButton()
    }

    private fun saveSettings() {
        val url = editUrl.text.toString().trim()
        val sleepStr = editSleepDuration.text.toString().trim()
        val timeoutStr = editScreenTimeout.text.toString().trim()

        if (url.isEmpty() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
            showStatus(getString(R.string.error_url_invalid), error = true)
            return
        }

        val sleepMinutes = sleepStr.toIntOrNull()
        if (sleepMinutes == null || sleepMinutes < 1) {
            showStatus(getString(R.string.error_sleep_invalid), error = true)
            return
        }

        val timeoutMinutes = timeoutStr.toIntOrNull()
        if (timeoutMinutes == null || timeoutMinutes < 1) {
            showStatus(getString(R.string.error_timeout_invalid), error = true)
            return
        }

        prefs.url = url
        prefs.sleepDurationMinutes = sleepMinutes
        prefs.screenTimeoutMinutes = timeoutMinutes

        applySystemScreenTimeout(timeoutMinutes)
    }

    private fun applySystemScreenTimeout(minutes: Int) {
        if (!Settings.System.canWrite(this)) {
            showStatus(getString(R.string.status_saved_no_permission))
            return
        }
        try {
            val timeoutMs = minutes * 60_000
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
            showStatus(getString(R.string.status_saved_with_timeout, minutes))
        } catch (e: Exception) {
            showStatus(getString(R.string.status_saved_timeout_failed))
        }
    }

    /**
     * Writes the four secure settings that make the system use our DreamService
     * as the screensaver — the same approach used by apps like Aerial Views.
     * Requires WRITE_SECURE_SETTINGS, which must be granted once via ADB:
     *   adb shell pm grant com.gappleby.androidtvss android.permission.WRITE_SECURE_SETTINGS
     */
    private fun applySystemScreensaverComponent() {
        if (!hasWriteSecureSettings()) {
            textAdbHint.visibility = View.VISIBLE
            showStatus(getString(R.string.error_secure_settings_permission), error = true)
            return
        }
        try {
            val component = "$packageName/.ScreensaverDreamService"
            Settings.Secure.putString(contentResolver, "screensaver_components", component)
            Settings.Secure.putInt(contentResolver, "screensaver_enabled", 1)
            Settings.Secure.putInt(contentResolver, "screensaver_activate_on_sleep", 1)
            Settings.Secure.putInt(contentResolver, "screensaver_activate_on_dock", 1)
            textAdbHint.visibility = View.GONE
            showStatus(getString(R.string.status_system_screensaver_set))
        } catch (e: Exception) {
            showStatus(getString(R.string.error_secure_settings_failed, e.message), error = true)
        }
    }

    private fun hasWriteSecureSettings(): Boolean {
        return checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun launchScreensaver() {
        startActivity(Intent(this, ScreensaverActivity::class.java))
    }

    private fun testUrl() {
        val url = editUrl.text.toString().trim()
        if (url.isEmpty()) {
            showStatus(getString(R.string.error_url_empty), error = true)
            return
        }
        startActivity(
            Intent(this, TestUrlActivity::class.java).putExtra(TestUrlActivity.EXTRA_URL, url)
        )
    }

    private fun openScreensaverSettings() {
        val tried = listOf(Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS)
        for (action in tried) {
            try { startActivity(Intent(action)); return } catch (_: Exception) {}
        }
        showStatus(getString(R.string.error_settings_unavailable), error = true)
    }

    private fun refreshPermissionButton() {
        btnPermission.visibility = if (Settings.System.canWrite(this)) View.GONE else View.VISIBLE
    }

    private fun refreshSystemScreensaverButton() {
        // Show ADB hint inline if permission not yet granted
        textAdbHint.visibility = if (hasWriteSecureSettings()) View.GONE else View.VISIBLE
    }

    private fun showStatus(message: String, error: Boolean = false) {
        textStatus.text = message
        textStatus.setTextColor(
            if (error) getColor(android.R.color.holo_red_light)
            else getColor(android.R.color.holo_green_light)
        )
        textStatus.visibility = View.VISIBLE
    }
}
