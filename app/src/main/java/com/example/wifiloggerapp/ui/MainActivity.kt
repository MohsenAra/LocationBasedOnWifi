package com.example.wifiloggerapp.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wifiloggerapp.R
import com.example.wifiloggerapp.data.AppDatabase
import com.example.wifiloggerapp.data.WifiEventEntity
import com.example.wifiloggerapp.service.WifiLoggerService
import com.example.wifiloggerapp.util.AppErrorLogger
import com.example.wifiloggerapp.util.JalaliDateHelper
import com.example.wifiloggerapp.util.NetworkAliasManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val permissionRequestCode = 1001
    private val backgroundLocationRequestCode = 1002

    private lateinit var btnManageAliases: Button
    private lateinit var btnErrorLogs: Button
    private lateinit var etFilter: EditText
    private lateinit var btnClearFilter: ImageButton
    private lateinit var tvFilterStatus: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var rvEvents: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyDesc: TextView

    private lateinit var adapter: WifiEventAdapter
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
        try {
            setContentView(R.layout.activity_main)

            aliasManager = NetworkAliasManager(this)

            initViews()
            setupRecyclerView()
            setupListeners()
            observeEvents()
            checkPermissionsAndStartService()
        } catch (e: Throwable) {
            AppErrorLogger.logError(this, "MainActivity", "خطای بحرانی در شروع برنامه", e)
            val stackTrace = e.stackTraceToString()
            try {
                AlertDialog.Builder(this)
                    .setTitle("خطا در اجرای برنامه")
                    .setMessage(stackTrace)
                    .setPositiveButton("بستن", null)
                    .show()
            } catch (_: Throwable) {
                val tv = TextView(this).apply {
                    text = stackTrace
                    setPadding(32, 32, 32, 32)
                    setTextIsSelectable(true)
                }
                setContentView(tv)
            }
        }
    }

    private fun initViews() {
        btnManageAliases = findViewById(R.id.btnManageAliases)
        btnErrorLogs = findViewById(R.id.btnErrorLogs)
        etFilter = findViewById(R.id.etFilter)
        btnClearFilter = findViewById(R.id.btnClearFilter)
        tvFilterStatus = findViewById(R.id.tvFilterStatus)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        rvEvents = findViewById(R.id.rvEvents)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptyDesc = findViewById(R.id.tvEmptyDesc)
    }

    private fun setupRecyclerView() {
        adapter = WifiEventAdapter(aliasManager) { event ->
            val ssid = event.ssid
            if (!ssid.isNullOrBlank() && ssid != "<unknown ssid>" && ssid != WifiLoggerService.UNKNOWN_SSID_READABLE) {
                showEditAliasDialog(initialSsid = ssid, isNew = false)
            } else {
                Toast.makeText(this, "امکان نام‌گذاری برای شبکه ناشناخته وجود ندارد", Toast.LENGTH_SHORT).show()
            }
        }
        rvEvents.layoutManager = LinearLayoutManager(this)
        rvEvents.adapter = adapter
    }

    private fun setupListeners() {
        btnManageAliases.setOnClickListener {
            showManageAliasesDialog()
        }

        btnErrorLogs.setOnClickListener {
            showErrorLogsDialog()
        }

        etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentFilterQuery = s?.toString()?.trim() ?: ""
                btnClearFilter.visibility = if (currentFilterQuery.isNotEmpty()) View.VISIBLE else View.GONE
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
        } catch (e: Exception) {
            AppErrorLogger.logError(this, "MainActivity", "خطا در ثبت گیرنده پیام", e)
        }
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
                AppErrorLogger.logError(this@MainActivity, "MainActivity", "خطا در دریافت وقایع از پایگاه داده", e)
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
            } catch (e: Exception) {
                AppErrorLogger.logError(this@MainActivity, "MainActivity", "خطا در بازخوانی وقایع", e)
            }
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
            tvFilterStatus.text = "فیلتر فعال: نمایش ${filtered.size} رویداد از ${allEvents.size}"
        } else {
            tvFilterStatus.text = "تعداد کل رویدادها: ${allEvents.size}"
        }

        if (allEvents.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvEvents.visibility = View.GONE
            tvEmptyTitle.text = "هنوز اتصالی ثبت نشده است"
            tvEmptyDesc.text = "سرویس در پس‌زمینه فعال است و به محض اتصال یا قطع از هر وای‌فای، رویداد در اینجا ثبت خواهد شد."
        } else if (filtered.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvEvents.visibility = View.GONE
            tvEmptyTitle.text = "رویدادی یافت نشد"
            tvEmptyDesc.text = "هیچ نتیجه‌ای مطابق با عبارت «$currentFilterQuery» پیدا نشد."
        } else {
            layoutEmpty.visibility = View.GONE
            rvEvents.visibility = View.VISIBLE
            adapter.submitList(filtered)
        }
    }

    private fun showManageAliasesDialog() {
        val knownSsids = allEvents.mapNotNull { it.ssid }
            .filter { it.isNotBlank() && it != "<unknown ssid>" && it != WifiLoggerService.UNKNOWN_SSID_READABLE }
            .distinct()
            .toMutableList()

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
            .setTitle("مدیریت نام‌های دلخواه شبکه‌ها")
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
            hint = "نام دلخواه (مثلاً: خانه، شرکت، دانشگاه)"
            setText(currentAlias)
        }

        if (isNew) {
            layout.addView(TextView(this).apply { text = "نام شبکه (SSID) را وارد کنید:" })
            layout.addView(etSsid)
        } else {
            layout.addView(TextView(this).apply { text = "شبکه انتخابی: $initialSsid" })
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

    private fun showErrorLogsDialog() {
        val errorText = AppErrorLogger.getErrorLogs(this)
        AlertDialog.Builder(this)
            .setTitle("لاگ خطاهای برنامه (Error Log)")
            .setMessage(errorText)
            .setPositiveButton("بستن", null)
            .setNeutralButton("پاکسازی لاگ") { _, _ ->
                AppErrorLogger.clearErrorLogs(this)
                Toast.makeText(this, "فایل لاگ خطاها پاکسازی شد", Toast.LENGTH_SHORT).show()
            }
            .show()
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
            checkBackgroundLocationPermission()
        } else {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                permissionRequestCode
            )
        }
    }

    private fun checkBackgroundLocationPermission() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasFineLocation) {
            val hasBackgroundLocation = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasBackgroundLocation) {
                showBackgroundLocationDialog()
                return
            }
        }
        startWifiService()
    }

    private fun showBackgroundLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("نیاز به مجوز مکان دائمی")
            .setMessage(
                "برای اینکه سرویس بتواند در پس‌زمینه نیز نام شبکه وای‌فای (SSID) را بدون خطای <unknown ssid> بخواند و ثبت کند، لطفاً در تنظیمات برنامه (Permissions -> Location) گزینه «Allow all the time» (اجازه در همه حال) را انتخاب کنید."
            )
            .setPositiveButton("باز کردن تنظیمات") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    AppErrorLogger.logError(this, "MainActivity", "خطا در باز کردن تنظیمات برنامه", e)
                    Toast.makeText(this, "لطفاً به صورت دستی از تنظیمات گوشی گزینه Allow all the time را فعال کنید.", Toast.LENGTH_LONG).show()
                }
                startWifiService()
            }
            .setNegativeButton("بعداً") { _, _ ->
                startWifiService()
            }
            .setCancelable(false)
            .show()
    }

    private fun startWifiService() {
        try {
            val serviceIntent = Intent(this, WifiLoggerService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            tvServiceStatus.text = "● سرویس فعال و در حال ثبت"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected_text))
        } catch (e: Exception) {
            AppErrorLogger.logError(this, "MainActivity", "خطا در شروع سرویس پس‌زمینه", e)
            tvServiceStatus.text = "⚠️ خطا در شروع سرویس: ${e.message}"
            tvServiceStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected_text))
            Toast.makeText(this, "خطا در شروع سرویس: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            permissionRequestCode -> {
                val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    Toast.makeText(this, "دسترسی‌های اولیه تایید شدند.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "توجه: بدون مجوز مکان (Location)، اندروید نام وای‌فای (SSID) را پنهان می‌کند.", Toast.LENGTH_LONG).show()
                }
                checkBackgroundLocationPermission()
            }
            backgroundLocationRequestCode -> {
                val bgGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (bgGranted) {
                    Toast.makeText(this, "مجوز مکان دائمی تایید شد. سرویس پایش فعال است.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "بدون گزینه Allow all the time، در پس‌زمینه ممکن است نام وای‌فای ناشناس ثبت شود.", Toast.LENGTH_LONG).show()
                }
                startWifiService()
            }
        }
    }
}
