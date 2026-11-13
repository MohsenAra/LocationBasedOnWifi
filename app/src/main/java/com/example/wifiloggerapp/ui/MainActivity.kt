package com.example.wifiloggerapp.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
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
import com.example.wifiloggerapp.util.JalaliDateHelper
import com.example.wifiloggerapp.util.NetworkAliasManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val permissionRequestCode = 1001

    private lateinit var btnTest: Button
    private lateinit var btnManageAliases: Button
    private lateinit var btnShowServiceLogs: Button
    private lateinit var etFilter: EditText
    private lateinit var btnClearFilter: Button
    private lateinit var tvFilterStatus: TextView
    private lateinit var tvReport: TextView

    private lateinit var aliasManager: NetworkAliasManager
    private var allEvents: List<WifiEventEntity> = emptyList()
    private var currentFilterQuery: String = ""

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadEventsFromDb()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        aliasManager = NetworkAliasManager(this)

        initViews()
        setupListeners()
        observeEvents()
        checkPermissionsAndStartService()
    }

    private fun initViews() {
        btnTest = findViewById(R.id.btnTest)
        btnManageAliases = findViewById(R.id.btnManageAliases)
        btnShowServiceLogs = findViewById(R.id.btnShowServiceLogs)
        etFilter = findViewById(R.id.etFilter)
        btnClearFilter = findViewById(R.id.btnClearFilter)
        tvFilterStatus = findViewById(R.id.tvFilterStatus)
        tvReport = findViewById(R.id.tvReport)
    }

    private fun setupListeners() {
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

        btnManageAliases.setOnClickListener {
            showManageAliasesDialog()
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
                        .setTitle("لاگ خام سرویس (Service Debug Log)")
                        .setMessage(if (text.isBlank()) "(فایل لاگ خالی است)" else text)
                        .setPositiveButton("بستن", null)
                        .show()
                }
            }
        }

        etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentFilterQuery = s?.toString()?.trim() ?: ""
                renderFilteredEvents()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearFilter.setOnClickListener {
            etFilter.setText("")
        }
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
                    allEvents = events
                    renderFilteredEvents()
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
                    allEvents = events
                    renderFilteredEvents()
                }
            } catch (_: Exception) {}
        }
    }

    private fun renderFilteredEvents() {
        val query = currentFilterQuery.lowercase(Locale.getDefault())
        val filtered = if (query.isEmpty()) {
            allEvents
        } else {
            allEvents.filter { event ->
                val ssid = event.ssid?.lowercase(Locale.getDefault()) ?: ""
                val bssid = event.bssid?.lowercase(Locale.getDefault()) ?: ""
                val alias = aliasManager.getAlias(event.ssid)?.lowercase(Locale.getDefault()) ?: ""
                val shamsi = JalaliDateHelper.formatShamsiDateTime(event.timestamp)

                ssid.contains(query) || alias.contains(query) || bssid.contains(query) || shamsi.contains(query)
            }
        }

        if (query.isNotEmpty()) {
            tvFilterStatus.text = "فیلتر فعال: نمایش ${filtered.size} رویداد از مجموع ${allEvents.size}"
        } else {
            tvFilterStatus.text = "تعداد کل رویدادها: ${allEvents.size}"
        }

        if (allEvents.isEmpty()) {
            tvReport.text = "هنوز هیچ رویدادی ثبت نشده است.\nسرویس در پس‌زمینه فعال است و به محض اتصال به وای‌فای لاگ ثبت می‌شود."
            return
        }

        if (filtered.isEmpty()) {
            tvReport.text = "هیچ رویدادی مطابق با عبارت «$currentFilterQuery» یافت نشد."
            return
        }

        val sb = StringBuilder()
        for (item in filtered.take(100)) {
            val shamsiDate = JalaliDateHelper.formatShamsiDateTime(item.timestamp)
            val isConnect = item.eventType.equals("CONNECTED", ignoreCase = true)
            val action = if (isConnect) "🟢 ورود (CONNECT)" else "🔴 خروج (DISCONNECT)"
            val rawSsid = item.ssid ?: "<نامشخص>"
            val customAlias = aliasManager.getAlias(item.ssid)
            val bssid = item.bssid ?: "<نامشخص>"

            val networkTitle = if (!customAlias.isNullOrBlank()) {
                "🏷️ $customAlias (SSID: $rawSsid)"
            } else {
                "📡 $rawSsid"
            }

            sb.append("$action\n")
            sb.append("تاریخ و ساعت شمسی: $shamsiDate\n")
            sb.append("شبکه: $networkTitle\n")
            sb.append("مک آدرس (BSSID): $bssid\n")
            sb.append("────────────────────────\n")
        }

        tvReport.text = sb.toString()
    }

    private fun showManageAliasesDialog() {
        // Collect known unique SSIDs from events
        val knownSsids = allEvents.mapNotNull { it.ssid }
            .filter { it.isNotBlank() && it != "<unknown ssid>" }
            .distinct()
            .toMutableList()

        // Also add any SSIDs currently stored in aliases
        val allStored = aliasManager.getAllAliases().keys
        for (s in allStored) {
            if (!knownSsids.contains(s)) {
                knownSsids.add(s)
            }
        }

        val items = mutableListOf<String>()
        items.add("➕ نام‌گذاری دستی یک شبکه جدید...")
        for (ssid in knownSsids) {
            val alias = aliasManager.getAlias(ssid)
            if (alias != null) {
                items.add("📶 $ssid  ➜  [ $alias ]")
            } else {
                items.add("📶 $ssid  ➜  (بدون نام دلخواه)")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("انتخاب شبکه برای نام‌گذاری")
            .setItems(items.toTypedArray()) { _, which ->
                if (which == 0) {
                    showEditAliasDialog(initialSsid = "", isNew = true)
                } else {
                    val selectedSsid = knownSsids[which - 1]
                    showEditAliasDialog(initialSsid = selectedSsid, isNew = false)
                }
            }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun showEditAliasDialog(initialSsid: String, isNew: Boolean) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val etSsid = EditText(this).apply {
            hint = "نام شبکه (SSID)"
            setText(initialSsid)
            isEnabled = isNew
        }

        val currentAlias = if (initialSsid.isNotEmpty()) aliasManager.getAlias(initialSsid) ?: "" else ""
        val etAlias = EditText(this).apply {
            hint = "نام دلخواه شما (مثلاً: خانه، محل کار، کافه)"
            setText(currentAlias)
        }

        if (isNew) {
            layout.addView(TextView(this).apply { text = "نام شبکه (SSID) را وارد کنید:" })
            layout.addView(etSsid)
        } else {
            layout.addView(TextView(this).apply { text = "شبکه: $initialSsid" })
        }

        layout.addView(TextView(this).apply {
            text = "نام دلخواه:"
            setPadding(0, 20, 0, 0)
        })
        layout.addView(etAlias)

        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle(if (isNew) "افزودن نام دلخواه جدید" else "ویرایش نام دلخواه")
            .setView(layout)
            .setPositiveButton("ذخیره") { _, _ ->
                val targetSsid = etSsid.text.toString().trim()
                val targetAlias = etAlias.text.toString().trim()
                if (targetSsid.isNotEmpty()) {
                    aliasManager.setAlias(targetSsid, targetAlias)
                    renderFilteredEvents()
                    Toast.makeText(this, "نام دلخواه ذخیره شد.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("انصراف", null)

        if (!isNew && currentAlias.isNotEmpty()) {
            dialogBuilder.setNeutralButton("حذف نام") { _, _ ->
                aliasManager.removeAlias(initialSsid)
                renderFilteredEvents()
                Toast.makeText(this, "نام دلخواه حذف شد.", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBuilder.show()
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
