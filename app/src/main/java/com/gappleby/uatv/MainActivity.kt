package com.gappleby.uatv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * Home screen shown from the TV launcher tile: Back (focused by default),
 * Show (plays the configured URL) and Config (opens SettingsActivity).
 * Deliberately shows no configuration details.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ensure the monitor service is always running
        ContextCompat.startForegroundService(this, Intent(this, IdleMonitorService::class.java))

        val btnBack = findViewById<Button>(R.id.btn_back)
        val btnShow = findViewById<Button>(R.id.btn_show)
        val btnConfig = findViewById<Button>(R.id.btn_config)

        btnBack.setOnClickListener { finish() }
        btnShow.setOnClickListener { startActivity(Intent(this, ScreensaverActivity::class.java)) }
        btnConfig.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        listOf(btnBack, btnShow, btnConfig).forEach { it.onFocusChangeListener = focusScaler }
        btnBack.requestFocus()
    }

    private val focusScaler = View.OnFocusChangeListener { view, hasFocus ->
        val scale = if (hasFocus) FOCUSED_SCALE else 1f
        view.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
    }

    companion object {
        private const val FOCUSED_SCALE = 1.1f
    }
}
