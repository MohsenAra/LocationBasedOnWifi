package com.example.wifiloggerapp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.wifiloggerapp.service.WifiLoggerService
import com.example.wifiloggerapp.util.AppErrorLogger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        try {
            if (i?.action == Intent.ACTION_BOOT_COMPLETED || i?.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
                val s = Intent(c, WifiLoggerService::class.java)
                ContextCompat.startForegroundService(c, s)
            }
        } catch (t: Throwable) {
            AppErrorLogger.logError(c, "BootReceiver", "خطا در شروع سرویس پس از راه‌اندازی دستگاه", t)
        }
    }
}
