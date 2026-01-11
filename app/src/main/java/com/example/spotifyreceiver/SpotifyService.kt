package com.example.spotifyreceiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SpotifyService : Service() {

    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        NativeBridge.initLogger()
        acquireLocks()
        startForeground(1, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceName = intent?.getStringExtra("DEVICE_NAME") ?: "Android Speaker"

        // start the Rust engine
        Thread {
            NativeBridge.startDevice(deviceName)
        }.start()

        return START_STICKY
    }

    private fun acquireLocks() {
        //wifi multicast lock - mdns requirement for spot connect discovery
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("SpotifyDiscoveryLock").apply {
            setReferenceCounted(true)
            acquire()
        }

        //wake lock - run when screen off
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "SpotifyReceiver::WakeLock").apply {
            acquire()
        }
    }

    private fun createNotification(): Notification {
        val channelId = "spotify_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Spotify Receiver Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Spotify Receiver Active")
            .setContentText("Listening for connections...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        NativeBridge.stopDevice()
        multicastLock?.release()
        wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}