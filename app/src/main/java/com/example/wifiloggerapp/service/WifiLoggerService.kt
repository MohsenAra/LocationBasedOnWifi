package com.example.wifiloggerapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.wifiloggerapp.data.AppDatabase
import com.example.wifiloggerapp.data.WifiEventEntity
import com.example.wifiloggerapp.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WifiLoggerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager

    private var currentSsid: String? = null
    private var currentBssid: String? = null
    private var isConnected: Boolean = false

    private var lastLoggedEventType: String? = null
    private var lastLoggedSsid: String? = null
    private var lastLoggedTimestamp: Long = 0L

    companion object {
        private const val TAG = "WifiLoggerService"
        private const val CHANNEL_ID = "wifi_logger_channel"
        private const val NOTIFICATION_ID = 101

        const val ACTION_LOG_EVENT = "com.example.wifiloggerapp.ACTION_LOG_EVENT"
        const val EXTRA_EVENT_TYPE = "extra_event_type"
        const val EXTRA_SSID = "extra_ssid"
        const val EXTRA_BSSID = "extra_bssid"

        const val BROADCAST_EVENT_INSERTED = "com.example.wifiloggerapp.EVENT_INSERTED"

        fun logEventDirect(context: Context, eventType: String, ssid: String?, bssid: String?) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppDatabase.init(context)
                    val dao = AppDatabase.getInstance().wifiEventDao()
                    val event = WifiEventEntity(
                        ssid = ssid,
                        bssid = bssid,
                        eventType = eventType,
                        timestamp = System.currentTimeMillis()
                    )
                    dao.insert(event)
                    appendDebugLog(context, eventType, ssid, bssid)
                    sendBroadcastIntent(context, eventType, ssid)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed direct logEvent", e)
                }
            }
        }

        fun appendDebugLog(context: Context, eventType: String, ssid: String?, bssid: String?) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val timeStr = sdf.format(Date())
                val debugLine = "$timeStr | $eventType | ${ssid ?: "<null>"} | ${bssid ?: "<null>"}\n"
                context.openFileOutput("wifi_debug.log", Context.MODE_APPEND).use {
                    it.write(debugLine.toByteArray(Charsets.UTF_8))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write debug log", e)
            }
        }

        fun sendBroadcastIntent(context: Context, eventType: String, ssid: String?) {
            try {
                val intent = Intent(BROADCAST_EVENT_INSERTED).apply {
                    putExtra("eventType", eventType)
                    putExtra("ssid", ssid)
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
            } catch (_: Exception) {}
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "NetworkCallback onAvailable")
            handleWifiConnected(network)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            Log.i(TAG, "NetworkCallback onCapabilitiesChanged")
            handleWifiConnected(network, networkCapabilities)
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "NetworkCallback onLost")
            handleWifiDisconnected()
        }
    }

    private val wifiStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            Log.i(TAG, "BroadcastReceiver onReceive: $action")
            if (action == WifiManager.NETWORK_STATE_CHANGED_ACTION) {
                checkCurrentWifiState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        registerWifiCallbacks()
        checkCurrentWifiState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LOG_EVENT) {
            val eventType = intent.getStringExtra(EXTRA_EVENT_TYPE) ?: "TEST"
            val ssid = intent.getStringExtra(EXTRA_SSID)
            val bssid = intent.getStringExtra(EXTRA_BSSID)
            logEventInternal(eventType, ssid, bssid)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service onDestroy")
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(wifiStateReceiver)
        } catch (_: Exception) {}
        serviceScope.cancel()
    }

    fun insertEvent(eventType: String, ssid: String?, bssid: String?) {
        logEventInternal(eventType, ssid, bssid)
    }

    private fun registerWifiCallbacks() {
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
        }

        try {
            val filter = IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            registerReceiver(wifiStateReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register broadcast receiver", e)
        }
    }

    private fun checkCurrentWifiState() {
        serviceScope.launch {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = connectivityManager.activeNetwork
                val caps = if (activeNetwork != null) connectivityManager.getNetworkCapabilities(activeNetwork) else null
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    handleWifiConnected(activeNetwork, caps)
                    return@launch
                }
            }
            // Fallback: check WifiManager
            val (ssid, bssid) = extractWifiInfo()
            if (ssid != null || bssid != null) {
                handleWifiConnected(null, null)
            }
        }
    }

    private fun handleWifiConnected(network: Network?, capabilities: NetworkCapabilities? = null) {
        serviceScope.launch {
            var (ssid, bssid) = extractWifiInfo(network, capabilities)

            if (ssid == null && bssid == null) {
                // Wait briefly in case Wi-Fi info resolution takes a moment
                delay(800)
                val retry = extractWifiInfo(network, capabilities)
                ssid = retry.first
                bssid = retry.second
            }

            val validSsid = ssid ?: "<unknown ssid>"

            if (currentSsid != null && currentSsid != validSsid && isConnected && currentSsid != "<unknown ssid>") {
                logEventInternal("DISCONNECTED", currentSsid, currentBssid)
            }

            val shouldLog = !isConnected || currentSsid != validSsid || (currentSsid == "<unknown ssid>" && validSsid != "<unknown ssid>")
            currentSsid = validSsid
            currentBssid = bssid ?: currentBssid
            isConnected = true

            if (shouldLog) {
                logEventInternal("CONNECTED", currentSsid, currentBssid)
            }
        }
    }

    private fun handleWifiDisconnected() {
        serviceScope.launch {
            if (isConnected) {
                val oldSsid = currentSsid ?: "<unknown ssid>"
                val oldBssid = currentBssid
                isConnected = false
                currentSsid = null
                currentBssid = null
                logEventInternal("DISCONNECTED", oldSsid, oldBssid)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractWifiInfo(
        network: Network? = null,
        capabilities: NetworkCapabilities? = null
    ): Pair<String?, String?> {
        var detectedSsid: String? = null
        var detectedBssid: String? = null

        // Method 1: NetworkCapabilities (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val caps = capabilities ?: (if (network != null) connectivityManager.getNetworkCapabilities(network) else null)
            val transportInfo = caps?.transportInfo
            if (transportInfo is WifiInfo) {
                val s = sanitizeSsid(transportInfo.ssid)
                if (s != null) {
                    detectedSsid = s
                    detectedBssid = transportInfo.bssid
                }
            }
        }

        // Method 2: WifiManager.connectionInfo
        if (detectedSsid == null) {
            try {
                val info = wifiManager.connectionInfo
                if (info != null && info.networkId != -1) {
                    val s = sanitizeSsid(info.ssid)
                    if (s != null) {
                        detectedSsid = s
                        detectedBssid = info.bssid
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error querying connectionInfo", e)
            }
        }

        return detectedSsid to detectedBssid
    }

    private fun sanitizeSsid(rawSsid: String?): String? {
        if (rawSsid == null) return null
        var s = rawSsid.trim()
        if (s.startsWith("\"") && s.endsWith("\"") && s.length >= 2) {
            s = s.substring(1, s.length - 1)
        }
        if (s.isEmpty() || s == "<unknown ssid>" || s == "0x") {
            return null
        }
        return s
    }

    @Synchronized
    private fun logEventInternal(eventType: String, ssid: String?, bssid: String?) {
        val now = System.currentTimeMillis()
        val cleanSsid = ssid ?: "<unknown ssid>"

        // Deduplication: suppress identical event within 3 seconds
        if (eventType == lastLoggedEventType && cleanSsid == lastLoggedSsid && (now - lastLoggedTimestamp) < 3000) {
            Log.d(TAG, "Suppressed duplicate event: $eventType $cleanSsid")
            return
        }
        lastLoggedEventType = eventType
        lastLoggedSsid = cleanSsid
        lastLoggedTimestamp = now

        Log.i(TAG, "Logging Wi-Fi event: $eventType | SSID=$cleanSsid | BSSID=$bssid")

        serviceScope.launch {
            try {
                val dao = AppDatabase.getInstance().wifiEventDao()
                val entity = WifiEventEntity(
                    ssid = cleanSsid,
                    bssid = bssid,
                    eventType = eventType,
                    timestamp = now
                )
                dao.insert(entity)

                appendDebugLog(this@WifiLoggerService, eventType, cleanSsid, bssid)
                sendBroadcastIntent(this@WifiLoggerService, eventType, cleanSsid)
                updateNotification(eventType, cleanSsid)
            } catch (e: Exception) {
                Log.w(TAG, "Error saving wifi event", e)
            }
        }
    }

    private fun updateNotification(eventType: String, ssid: String) {
        try {
            val statusText = if (eventType == "CONNECTED") {
                "متصل به: $ssid"
            } else {
                "قطع اتصال از: $ssid"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(statusText))
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                "پایشگر وای‌فای (Wi-Fi Logger)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "ثبت خودکار ورود و خروج به شبکه‌های وای‌فای"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(chan)
        }
    }

    private fun buildNotification(statusText: String = "در حال پایش اتصالات وای‌فای..."): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("پایشگر وای‌فای فعال است")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
