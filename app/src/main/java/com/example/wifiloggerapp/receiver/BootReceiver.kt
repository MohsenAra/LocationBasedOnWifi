package com.example.wifiloggerapp.receiver
import android.content.*
import androidx.core.content.ContextCompat
import com.example.wifiloggerapp.service.WifiLoggerService
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        if (i?.action==Intent.ACTION_BOOT_COMPLETED || i?.action==Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val s=Intent(c,WifiLoggerService::class.java); ContextCompat.startForegroundService(c,s)
        }
    }
}
