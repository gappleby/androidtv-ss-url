package com.gappleby.androidtvss

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** URL to display in the screensaver WebView. */
    var url: String
        get() = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) = prefs.edit().putString(KEY_URL, value).apply()

    /**
     * How many minutes the screensaver runs before calling finish() (which lets
     * the device sleep).  0 = run indefinitely until the system decides to stop it.
     */
    var sleepDurationMinutes: Int
        get() = prefs.getInt(KEY_SLEEP_DURATION, DEFAULT_SLEEP_DURATION)
        set(value) = prefs.edit().putInt(KEY_SLEEP_DURATION, value).apply()

    /**
     * Desired system SCREEN_OFF_TIMEOUT in minutes.  The app will attempt to
     * write this to Settings.System.SCREEN_OFF_TIMEOUT when settings are saved
     * (requires WRITE_SETTINGS permission).
     */
    var screenTimeoutMinutes: Int
        get() = prefs.getInt(KEY_SCREEN_TIMEOUT, DEFAULT_SCREEN_TIMEOUT)
        set(value) = prefs.edit().putInt(KEY_SCREEN_TIMEOUT, value).apply()

    companion object {
        const val PREFS_NAME = "screensaver_prefs"
        const val KEY_URL = "url"
        const val KEY_SLEEP_DURATION = "sleep_duration_minutes"
        const val KEY_SCREEN_TIMEOUT = "screen_timeout_minutes"
        const val DEFAULT_URL = "https://example.com"
        const val DEFAULT_SLEEP_DURATION = 30
        const val DEFAULT_SCREEN_TIMEOUT = 5
    }
}
