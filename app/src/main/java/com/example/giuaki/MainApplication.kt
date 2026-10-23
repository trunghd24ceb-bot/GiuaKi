package com.example.giuaki

import android.app.Application
import com.cloudinary.android.MediaManager

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val config = mapOf("cloud_name" to "px9ptx27")
            MediaManager.init(this, config)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
