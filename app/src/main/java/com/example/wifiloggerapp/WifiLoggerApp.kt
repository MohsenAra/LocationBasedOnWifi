package com.example.wifiloggerapp
import android.app.Application
import com.example.wifiloggerapp.data.AppDatabase
class WifiLoggerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDatabase.init(this)
    }
}
