package com.example.wifiloggerapp.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.wifiloggerapp.data.AppDatabase
import com.example.wifiloggerapp.data.WifiEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WifiLoggerService(private val context: Context) {

    private val TAG = "WifiLoggerService"
    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    fun insertEvent(eventType: String, ssid: String?, bssid: String?) {
        Log.i(TAG, "insertEvent: type=$eventType ssid=$ssid bssid=$bssid")
        val dao = AppDatabase.getInstance().wifiEventDao()
        serviceScope.launch {
            try {
                val event = WifiEventEntity(
                    ssid = ssid,
                    bssid = bssid,
                    eventType = eventType,
                    timestamp = System.currentTimeMillis()
                )
                dao.insert(event)
                Log.i(TAG, "Event inserted into DB: $eventType / $ssid")

                // append debug line to internal file for offline debugging
                try {
                    val debugLine = "${System.currentTimeMillis()} | $eventType | ${ssid ?: "<null>"} | ${bssid ?: "<null>"}\n"
                    context.openFileOutput("wifi_debug.log", Context.MODE_APPEND).use { it.write(debugLine.toByteArray(Charsets.UTF_8)) }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to write debug log file", t)
                }

                // broadcast that an event was inserted so UI can refresh
                try {
                    val b = Intent("com.example.wifiloggerapp.EVENT_INSERTED")
                    b.putExtra("eventType", eventType)
                    b.putExtra("ssid", ssid)
                    context.sendBroadcast(b)
                } catch (_: Exception) {
                }

            } catch (t: Throwable) {
                Log.w(TAG, "Failed to insert event", t)
            }
        }
    }
}
