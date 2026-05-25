package com.gappleby.androidtvss

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.service.dreams.DreamService
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class ScreensaverDreamService : DreamService() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var sleepRunnable: Runnable? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        isInteractive = false   // exit dream on any D-pad / touch input
        isFullscreen = true

        setContentView(R.layout.dream_webview)
        webView = findViewById(R.id.webview)

        val prefs = AppPreferences(this)
        setupWebView(prefs.url)

        val durationMs = prefs.sleepDurationMinutes * 60_000L
        if (durationMs > 0) {
            sleepRunnable = Runnable { finish() }.also {
                handler.postDelayed(it, durationMs)
            }
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
            // Use desktop UA so sites render their full layout on the large TV screen
            userAgentString = userAgentString.replace("Mobile", "")
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.visibility = View.VISIBLE
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Block JS dialogs
            override fun onJsAlert(v: WebView, url: String, msg: String, r: JsResult) = r.cancel().let { true }
            override fun onJsConfirm(v: WebView, url: String, msg: String, r: JsResult) = r.cancel().let { true }
            override fun onJsPrompt(v: WebView, url: String, msg: String, d: String?, r: JsPromptResult) = r.cancel().let { true }
            override fun onJsBeforeUnload(v: WebView, url: String, msg: String, r: JsResult) = r.cancel().let { true }

            // Block popup windows
            override fun onCreateWindow(v: WebView, isDialog: Boolean, isGesture: Boolean, msg: Message?) = false

            // Deny all device-permission requests (camera, mic, etc.)
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()

            // Deny geolocation
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, false, false)
            }
        }

        webView.visibility = View.INVISIBLE
        webView.loadUrl(url)
    }

    override fun onDetachedFromWindow() {
        sleepRunnable?.let { handler.removeCallbacks(it) }
        webView.stopLoading()
        webView.destroy()
        super.onDetachedFromWindow()
    }
}
