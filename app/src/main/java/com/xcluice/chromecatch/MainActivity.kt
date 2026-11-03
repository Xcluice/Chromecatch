package com.xcluice.chromecatch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * One-time setup screen. Three taps, once each:
 *  1. Camera permission (standard Android permission dialog)
 *  2. Accessibility service (Android requires this be enabled manually in Settings —
 *     no app, including this one, can flip it on silently)
 *  3. Screen-capture consent (MediaProjection) — also a one-time system dialog.
 *     The token is cached in ProjectionHolder and reused for every capture after
 *     this, as long as the background service stays alive.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshStatus() }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            ProjectionHolder.resultCode = result.resultCode
            ProjectionHolder.data = result.data
            CaptureService.ensureRunning(this)
        }
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.btnCamera).setOnClickListener {
            cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnProjection).setOnClickListener {
            val mgr = getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(mgr.createScreenCaptureIntent())
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val accessibility = isAccessibilityServiceEnabled()
        val projection = ProjectionHolder.data != null

        statusText.text = buildString {
            append(if (cam) "✓ Camera\n" else "✗ Camera\n")
            append(if (accessibility) "✓ Accessibility\n" else "✗ Accessibility (tap 2, find ChromeCatch, enable)\n")
            append(if (projection) "✓ Screen capture\n" else "✗ Screen capture\n")
            if (cam && accessibility && projection) append("\nAll set. Open Chrome to test it.")
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${ChromeWatchService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(":").any { it.equals(expected, ignoreCase = true) }
    }
}
