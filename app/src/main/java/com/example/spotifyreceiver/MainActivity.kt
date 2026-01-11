package com.example.spotifyreceiver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private var deviceName = "Android Speaker"

    //playing around with 14+ foreground service reqs
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startSpotifyService(deviceName)
            } else {
                //grah
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermissionsAndStartService()

        findViewById<Button>(R.id.btnUpdateDeviceName).setOnClickListener {
            val newName = findViewById<TextInputEditText>(R.id.etDeviceName).text.toString()
            if (newName.isNotEmpty()) {
                deviceName = newName
                findViewById<TextView>(R.id.tvDeviceName).text = "Device Name: $deviceName"
                restartSpotifyService()
            }
        }
    }

    private fun checkPermissionsAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    startSpotifyService(deviceName)
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            startSpotifyService(deviceName)
        }
    }

    private fun startSpotifyService(name: String) {
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        tvStatus.text = "Starting Receiver..."

        val intent = Intent(this, SpotifyService::class.java).apply {
            putExtra("DEVICE_NAME", name)
        }
        startForegroundService(intent)

        Handler(Looper.getMainLooper()).postDelayed({
            tvStatus.text = ""
            findViewById<TextView>(R.id.tvSongTitle).text = "Waiting For Connection"
        }, 5000)
    }

    private fun restartSpotifyService() {
        stopService(Intent(this, SpotifyService::class.java))
        startSpotifyService(deviceName)
    }
}