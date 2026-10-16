*** Begin Patch
*** Update File: app/src/main/java/com/example/wifiloggerapp/ui/MainActivity.kt
@@
     private lateinit var btnTest: Button
+    private lateinit var btnShowServiceLogs: Button
     private lateinit var tvReport: TextView
@@
         btnTest = findViewById(R.id.btnTest)
+        btnShowServiceLogs = findViewById(R.id.btnShowServiceLogs)
         tvReport = findViewById(R.id.tvReport)
@@
         btnTest.setOnClickListener {
@@
         }
+
+        btnShowServiceLogs.setOnClickListener {
+            // read internal file and show in dialog
+            lifecycleScope.launch(Dispatchers.IO) {
+                val text = try {
+                    applicationContext.openFileInput("wifi_debug.log").bufferedReader().use { it.readText() }
+                } catch (t: Throwable) {
+                    "(no debug log file yet)"
+                }
+                withContext(Dispatchers.Main) {
+                    AlertDialog.Builder(this@MainActivity)
+                        .setTitle("Service Debug Log")
+                        .setMessage(text)
+                        .setPositiveButton("بستن", null)
+                        .show()
+                }
+            }
+        }
*** End Patch