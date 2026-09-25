package com.gappleby.uatv

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class ScreensaverActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while screensaver is active
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_screensaver)
        hideSystemUi()

        webView = findViewById(R.id.webview_screensaver)
        val prefs = AppPreferences(this)
        setupWebView(prefs.url)
        scheduleSleep(prefs.sleepDurationMinutes)
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    private fun setupWebView(url: String) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = userAgentString.replace("Mobile", "")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.visibility = View.VISIBLE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(v: WebView, url: String, msg: String, r: JsResult) = r.cancel().let { true }
            override fun onJsConfirm(v: WebView, url: String, msg: String, r: JsResult) = r.cancel().let { true }
            override fun onJsPrompt(v: WebView, url: String, msg: String, d: String?, r: JsPromptResult) = r.cancel().let { true }
            override fun onJsBeforeUnload(v: WebView, url: String, msg: String, r: JsResult) = r.cancel().let { true }
            override fun onCreateWindow(v: WebView, isDialog: Boolean, isGesture: Boolean, msg: Message?) = false
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }
        }

        webView.visibility = View.INVISIBLE
        webView.loadUrl(url)
    }

    private fun scheduleSleep(minutes: Int) {
        if (minutes <= 0) return
        sleepRunnable = Runnable {
            // Drop keep-screen-on so the device can sleep normally, then exit
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            finish()
        }.also { handler.postDelayed(it, minutes * 60_000L) }
    }

    // Handled here rather than in onKeyDown so the focused WebView can't swallow them.
    // Back → return to the home screen (or whatever launched us); Menu → open settings.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action: (() -> Unit)? = when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> ::finish
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> ::openSettings
            else -> null
        }
        if (action == null) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) action()
        return true
    }

    private fun openSettings() {
        sleepRunnable?.let { handler.removeCallbacks(it) }
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        sleepRunnable?.let { handler.removeCallbacks(it) }
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }
}
