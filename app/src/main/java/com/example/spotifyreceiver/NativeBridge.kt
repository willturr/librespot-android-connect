package com.example.spotifyreceiver

import android.util.Log

object NativeBridge {
    init {
        try {
            System.loadLibrary("spotify_receiver_core")
            Log.d("NativeBridge", "Library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NativeBridge", "Failed to load library: ${e.message}")
        }
    }

    //map functions in rust core lib
    external fun initLogger()
    external fun startDevice(deviceName: String)
    external fun stopDevice()
}