package com.example.wifiloggerapp.ui
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.wifiloggerapp.data.AppDatabase
import com.example.wifiloggerapp.databinding.ActivityMainBinding
import com.example.wifiloggerapp.service.WifiLoggerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*
class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private val uiScope = CoroutineScope(Dispatchers.Main + Job())
    private val permLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){startService()}
    override fun onCreate(s: Bundle?) {
        super.onCreate(s); b=ActivityMainBinding.inflate(layoutInflater); setContentView(b.root); reqPerms(); observeLogs()
    }
    private fun reqPerms(){
        val p= mutableListOf<String>()
        if(Build.VERSION.SDK_INT<33 && ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if(Build.VERSION.SDK_INT>=33){
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.POST_NOTIFICATIONS)
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED) p.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if(p.isNotEmpty()) permLauncher.launch(p.toTypedArray()) else startService()
    }
    private fun startService(){ ContextCompat.startForegroundService(this, Intent(this, WifiLoggerService::class.java)) }
    private fun observeLogs(){
        val dao=AppDatabase.getInstance().wifiEventDao()
        uiScope.launch{
            dao.getAll().collectLatest{list->
                val sdf=SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                b.logsText.text=list.joinToString("\n"){ "${it.eventType} | ${it.ssid?: "?"} | ${it.bssid?: "?"} | ${sdf.format(Date(it.timestamp))}" }
            }
        }
    }
}
