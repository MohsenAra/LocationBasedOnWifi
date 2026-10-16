package com.example.wifiloggerapp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.wifiloggerapp.R
import com.example.wifiloggerapp.WifiLoggerApp
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

    private val PERMISSION_REQUEST_CODE = 1001

    private lateinit var tvStatus: TextView
    private lateinit var tvLogCount: TextView
    private lateinit var tvLogs: TextView
    private lateinit var etFilterSSID: EditText
    private lateinit var btnFilter: Button
    private lateinit var btnClear: Button

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
        observeDatabase()
        checkPermissionsAndStartService()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvLogCount = findViewById(R.id.tvLogCount)
        tvLogs = findViewById(R.id.tvLogs)
        etFilterSSID = findViewById(R.id.etFilterSSID)
        // رفع باگ کرش: تبدیل به Button به جای TextView
        btnFilter = findViewById(R.id.btnFilter)
        btnClear = findViewById(R.id.btnClear)
    }

    private fun setupListeners() {
        btnFilter.setOnClickListener {
            val query = etFilterSSID.text.toString().trim()
            if (query.isNotEmpty()) {
                filterLogs(query)
            } else {
                observeDatabase()
            }
        }

        btnClear.setOnClickListener {
            etFilterSSID.setText("")
            lifecycleScope.launch(Dispatchers.IO) {
                WifiLoggerApp.database.wifiEventDao().deleteAll()
            }
        }
    }

    private fun observeDatabase() {
        lifecycleScope.launch {
            WifiLoggerApp.database.wifiEventDao().getAllEventsFlow().collectLatest { events ->
                renderEvents(events)
            }
        }
    }

    private fun filterLogs(ssid: String) {
        lifecycleScope.launch {
            val filtered = withContext(Dispatchers.IO) {
                WifiLoggerApp.database.wifiEventDao().getEventsBySSID(ssid)
            }
            renderEvents(filtered)
        }
    }

    private fun renderEvents(events: List<WifiEventEntity>) {
        tvLogCount.text = "تعداد لاگ‌ها: ${events.size}"
        if (events.isEmpty()) {
            tvLogs.text = "هیچ اتصالی ثبت نشده است."
            return
        }

        val sb = StringBuilder()
        for (item in events) {
            val formattedDate = dateFormat.format(Date(item.timestamp))
            val action = if (item.eventType == "CONNECTED") "✅ ورود" else "❌ خروج"
            sb.append("$formattedDate | $action\n")
            sb.append("شبکه: ${item.ssid} (${item.bssid ?: "نامشخص"})\n")
            sb.append("------------------------------------\n")
        }
        tvLogs.text = sb.toString()
    }

    private fun checkPermissionsAndStartService() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // افزودن دسترسی نوتیفیکیشن برای اندروید 13 و بالاتر
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
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startWifiService() {
        try {
            val intent = Intent(this, WifiLoggerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            tvStatus.text = "وضعیت پایش: فعال در پس‌زمینه"
        } catch (e: Exception) {
            tvStatus.text = "خطا در شروع سرویس: ${e.message}"
            Toast.makeText(this, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startWifiService()
            } else {
                tvStatus.text = "نیاز به اعطای دسترسی لوکیشن و نوتیفیکیشن"
                Toast.makeText(this, "بدون مجوز، نام وای‌فای ثبت نخواهد شد.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
