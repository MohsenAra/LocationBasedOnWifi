package com.example.wifiloggerapp.service
import android.app.*
import android.content.*
import android.net.*
import android.net.wifi.WifiManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.example.wifiloggerapp.R
import com.example.wifiloggerapp.data.AppDatabase
import com.example.wifiloggerapp.data.WifiEventEntity
import com.example.wifiloggerapp.ui.MainActivity
import kotlinx.coroutines.*
class WifiLoggerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var cm: ConnectivityManager
    private val channelId = "wifi_logger_channel"
    private val notifId = 101
    private var lastSsid: String? = null
    private var lastBssid: String? = null
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val (s,b)=getWifiInfo(); lastSsid=s; lastBssid=b; insert("CONNECT",s,b)
        }
        override fun onLost(network: Network) { insert("DISCONNECT",lastSsid, lastBssid) }
    }
    override fun onCreate() {
        super.onCreate(); cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        createChannel(); startForeground(notifId, buildNotif()); registerCallback()
    }
    override fun onBind(i: Intent?) = null
    override fun onDestroy() { super.onDestroy(); cm.unregisterNetworkCallback(callback); scope.cancel() }
    override fun onStartCommand(i: Intent?, f: Int, s: Int) = START_STICKY
    private fun registerCallback() {
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        cm.registerNetworkCallback(req, callback)
    }
    private fun insert(type:String,s:String?,b:String?){ val dao=AppDatabase.getInstance().wifiEventDao(); scope.launch{ dao.insert(WifiEventEntity(ssid=s,bssid=b,eventType=type,timestamp=System.currentTimeMillis())) }}
    private fun getWifiInfo():Pair<String?,String?>{
        val wm=getSystemService(Context.WIFI_SERVICE) as WifiManager; val i=wm.connectionInfo
        if(i!=null && i.networkId!=-1){ var ssid=i.ssid; if(ssid?.startsWith(""")==true) ssid=ssid.substring(1,ssid.length-1); return ssid to i.bssid }
        return null to null
    }
    private fun createChannel(){ if(Build.VERSION.SDK_INT>=26){ val ch=NotificationChannel(channelId,"Wi-Fi Logger",NotificationManager.IMPORTANCE_LOW); getSystemService(NotificationManager::class.java).createNotificationChannel(ch) } }
    private fun buildNotif():Notification{
        val intent=Intent(this,MainActivity::class.java)
        val pi=PendingIntent.getActivity(this,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,channelId).setContentTitle("Wi-Fi Logger Running").setContentText("Listening for Wi-Fi events").setSmallIcon(R.drawable.ic_launcher_foreground).setContentIntent(pi).setOngoing(true).build()
    }
}
