package com.example.wifiloggerapp.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var btnTest: Button
    private lateinit var btnShowServiceLogs: Button
    private lateinit var tvReport: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.example.wifiloggerapp.R.layout.activity_main)

        btnTest = findViewById(com.example.wifiloggerapp.R.id.btnTest)
        btnShowServiceLogs = findViewById(com.example.wifiloggerapp.R.id.btnShowServiceLogs)
        tvReport = findViewById(com.example.wifiloggerapp.R.id.tvReport)

        btnTest.setOnClickListener {
            // original test action: show a simple message in the report TextView
            tvReport.text = "Test event logged"
        }

        btnShowServiceLogs.setOnClickListener {
            // read internal file and show in dialog
            lifecycleScope.launch(Dispatchers.IO) {
                val text = try {
                    applicationContext.openFileInput("wifi_debug.log").bufferedReader().use { it.readText() }
                } catch (t: Throwable) {
                    "(no debug log file yet)"
                }
                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Service Debug Log")
                        .setMessage(text)
                        .setPositiveButton("بستن", null)
                        .show()
                }
            }
        }
    }
}
