package com.example.wifiloggerapp.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.wifiloggerapp.R
import com.example.wifiloggerapp.data.AppDatabase
import com.example.wifiloggerapp.data.WifiEventEntity
import com.example.wifiloggerapp.service.WifiLoggerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val permissionRequestCode = 1001

    private lateinit var btnTest: Button
    private lateinit var btnShowServiceLogs: Button
    private lateinit var tvReport: TextView

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadEventsFromDb()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnTest = findViewById(R.id.btnTest)
        btnShowServiceLogs = findViewById(R.id.btnShowServiceLogs)
        tvReport = findViewById(R.id.tvReport)

        btnTest.setOnClickListener {
            val testSsid = "Test-WiFi-${System.currentTimeMillis() % 1000}"
            val testIntent = Intent(this, WifiLoggerService::class.java).apply {
                action = WifiLoggerService.ACTION_LOG_EVENT
                putExtra(WifiLoggerService.EXTRA_EVENT_TYPE, "CONNECTED")
                putExtra(WifiLoggerService.EXTRA_SSID, testSsid)
                putExtra(WifiLoggerService.EXTRA_BSSID, "00:11:22:33:44:55")
            }
            try {
                ContextCompat.startForegroundService(this, testIntent)
                Toast.makeText(this, "رویداد تستی ارسال شد ($testSsid)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                WifiLoggerService.logEventDirect(this, "CONNECTED", testSsid, "00:11:22:33:44:55")
                Toast.makeText(this, "رویداد تستی ثبت شد ($testSsid)", Toast.LENGTH_SHORT).show()
            }
        }

        btnShowServiceLogs.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val text = try {
                    applicationContext.openFileInput("wifi_debug.log").bufferedReader().use { it.readText() }
                } catch (t: Throwable) {
                    "(هنوز فایلی برای لاگ ثبت نشده است)"
                }
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Service Debug Log")
                        .setMessage(if (text.isBlank()) "(فایل لاگ خالی است)" else text)
                        .setPositiveButton("بستن", null)
                        .show()
                }
            }
        }

        observeEvents()
        checkPermissionsAndStartService()
    }

    override fun onStart() {
        super.onStart()
        try {
            val filter = IntentFilter("com.example.wifiloggerapp.EVENT_INSERTED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(refreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(refreshReceiver, filter)
            }
        } catch (_: Exception) {}
        loadEventsFromDb()
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(refreshReceiver)
        } catch (_: Exception) {}
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            try {
                AppDatabase.getInstance().wifiEventDao().getAll().collectLatest { events ->
                    renderEvents(events)
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to observe events", e)
            }
        }
    }

    private fun loadEventsFromDb() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val events = AppDatabase.getInstance().wifiEventDao().getAllList().reversed()
                withContext(Dispatchers.Main) {
                    renderEvents(events)
                }
            } catch (_: Exception) {}
        }
    }

    private fun renderEvents(events: List<WifiEventEntity>) {
        if (events.isEmpty()) {
            tvReport.text = "هیچ رویداد وای‌فایی ثبت نشده است.\nسرویس در پس‌زمینه فعال است و هر بار که به وای‌فای متصل شوید، رویداد در اینجا و فایل لاگ ثبت خواهد شد."
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("تعداد کل وقایع ثبت شده: ${events.size}\n")
        sb.append("────────────────────────\n\n")

        for (item in events.take(100)) {
            val dateStr = sdf.format(Date(item.timestamp))
            val isConnect = item.eventType.equals("CONNECTED", ignoreCase = true)
            val action = if (isConnect) "🟢 ورود (CONNECT)" else "🔴 خروج (DISCONNECT)"
            val networkName = item.ssid ?: "<نامشخص / بدون دسترسی لوکیشن>"
            val bssid = item.bssid ?: "<نامشخص>"

            sb.append("$action\n")
            sb.append("زمان: $dateStr\n")
            sb.append("شبکه: $networkName\n")
            sb.append("مک آدرس (BSSID): $bssid\n")
            sb.append("────────────────────────\n")
        }

        tvReport.text = sb.toString()
    }

    private fun checkPermissionsAndStartService() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startWifiService()
        } else {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                permissionRequestCode
            )
        }
    }

    private fun startWifiService() {
        try {
            val serviceIntent = Intent(this, WifiLoggerService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting service", e)
            Toast.makeText(this, "خطا در شروع سرویس: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "دسترسی‌ها تایید شدند. سرویس پایش فعال شد.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "توجه: بدون مجوز مکان (Location)، اندروید نام وای‌فای (SSID) را پنهان می‌کند.", Toast.LENGTH_LONG).show()
            }
            startWifiService()
        }
    }
}
