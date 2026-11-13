package com.example.wifiloggerapp

import android.app.Application
import com.example.wifiloggerapp.data.AppDatabase
import com.example.wifiloggerapp.util.AppErrorLogger

class WifiLoggerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppErrorLogger.init(this)
        try {
            AppDatabase.init(this)
        } catch (t: Throwable) {
            AppErrorLogger.logError(this, "WifiLoggerApp", "خطا در مقداردهی پایگاه داده", t)
        }
    }
}
