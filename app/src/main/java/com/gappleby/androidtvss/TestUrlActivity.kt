package com.gappleby.androidtvss

import android.os.Bundle
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class TestUrlActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
    }

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_url)

        webView = findViewById(R.id.webview_test)
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        setupWebView(url)
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

    // Back button exits the test preview
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
