package com.example.wifiloggerapp.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.wifiloggerapp.R
import com.example.wifiloggerapp.data.AppDatabase
import com.example.wifiloggerapp.service.WifiLoggerService
import com.example.wifiloggerapp.util.ReportCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private val permissionRequestCode = 1001

    private lateinit var etFilterSsid: EditText
    private lateinit var btnAnnualReport: Button
    private lateinit var tvReport: TextView

    private val reportCalculator = ReportCalculator()
    private val dao by lazy { AppDatabase.getInstance().wifiEventDao() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        checkPermissionsAndStartService()
        loadCurrentReportPreview()
    }

    private fun initViews() {
        etFilterSsid = findViewById(R.id.etFilterSsid)
        btnAnnualReport = findViewById(R.id.btnAnnualReport)
        tvReport = findViewById(R.id.tvReport)

        btnAnnualReport.setOnClickListener {
            showYearPickerDialog()
        }
    }

    private fun loadCurrentReportPreview() {
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now(zone).withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = LocalDate.now(zone).withDayOfYear(LocalDate.now(zone).lengthOfYear()).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

            val events = withContext(Dispatchers.IO) {
                dao.getEventsBetween(start, minOf(now, end))
            }

            if (events.isEmpty()) {
                tvReport.text = "هیچ رویدادی برای سال جاری ثبت نشده است."
                return@launch
            }

            val sessions = reportCalculator.computeSessions(events, start, minOf(now, end))
            val reports = reportCalculator.buildSsidReports(sessions, zone)

            val summary = StringBuilder()
            reports.forEach { report ->
                summary.append("SSID: ${report.ssid ?: "<UNKNOWN>"}\n")
                summary.append("کل زمان: ${reportCalculator.formatDurationHumanReadable(report.totalDurationMillis)}\n")
                report.daily.forEach { day ->
                    summary.append("  ${day.date}: ${reportCalculator.formatDurationHumanReadable(day.totalDurationMillis)}\n")
                }
                summary.append("\n")
            }
            tvReport.text = summary.toString().ifEmpty { "هیچ داده‌ای برای نمایش وجود ندارد." }
        }
    }

    private fun showYearPickerDialog() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear downTo currentYear - 5).map { it.toString() }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("انتخاب سال")
            .setItems(years) { _, which ->
                val selectedYear = years[which].toInt()
                generateAnnualReport(selectedYear)
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun generateAnnualReport(year: Int) {
        val filter = etFilterSsid.text.toString().trim()
        lifecycleScope.launch {
            tvReport.text = "در حال ساخت گزارش سال ${year}..."

            val zone = ZoneId.systemDefault()
            val start = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = LocalDate.of(year, 12, 31).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

            val events = withContext(Dispatchers.IO) {
                if (filter.isNotEmpty()) {
                    dao.getEventsForSsidBetween(filter, start, end)
                } else {
                    dao.getEventsBetween(start, end)
                }
            }

            if (events.isEmpty()) {
                tvReport.text = "هیچ رویدادی برای سال ${year} پیدا نشد."
                return@launch
            }

            val sessions = reportCalculator.computeSessions(events, start, end)
            val reports = reportCalculator.buildSsidReports(sessions, zone)

            val summary = StringBuilder()
            reports.forEach { report ->
                summary.append("SSID: ${report.ssid ?: "<UNKNOWN>"}\n")
                summary.append("کل زمان: ${reportCalculator.formatDurationHumanReadable(report.totalDurationMillis)}\n")
                report.daily.forEach { day ->
                    summary.append("  ${day.date}: ${reportCalculator.formatDurationHumanReadable(day.totalDurationMillis)}\n")
                }
                summary.append("\n")
            }

            tvReport.text = summary.toString()

            AlertDialog.Builder(this@MainActivity)
                .setTitle("گزارش سال ${year}")
                .setMessage(summary.toString())
                .setPositiveButton("خروجی CSV") { _, _ -> exportCsv(year, reports) }
                .setNegativeButton("بستن", null)
                .show()
        }
    }

    private fun exportCsv(year: Int, reports: List<com.example.wifiloggerapp.util.SsidReport>) {
        lifecycleScope.launch {
            val csv = withContext(Dispatchers.Default) {
                reportCalculator.exportToCsv(reports, ZoneId.systemDefault())
            }

            withContext(Dispatchers.IO) {
                var targetFile: File? = null

                try {
                    val docDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    } else {
                        getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    }
                    if (docDir != null && (docDir.exists() || docDir.mkdirs())) {
                        targetFile = File(docDir, "wifi_report_${year}.csv")
                        targetFile.writeText(csv)
                    }
                } catch (_: Exception) {
                }

                if (targetFile == null) {
                    val fallbackDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    if (fallbackDir != null && (fallbackDir.exists() || fallbackDir.mkdirs())) {
                        targetFile = File(fallbackDir, "wifi_report_${year}.csv")
                        targetFile.writeText(csv)
                    }
                }

                val msg = if (targetFile != null && targetFile.exists()) {
                    "گزارش صادر شد: ${targetFile.absolutePath}"
                } else {
                    "خطا در نوشتن فایل گزارش."
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkPermissionsAndStartService() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startWifiService()
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), permissionRequestCode)
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
        } catch (e: Exception) {
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
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startWifiService()
            } else {
                Toast.makeText(this, "بدون مجوزهای مناسب، دسترسی به SSID فعال نخواهد شد.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
