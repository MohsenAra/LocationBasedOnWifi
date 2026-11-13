package com.example.wifiloggerapp.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object AppErrorLogger {

    private const val TAG = "AppErrorLogger"
    private const val ERROR_LOG_FILE = "app_errors.log"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                logError(
                    context,
                    "CRASH_${thread.name}",
                    "خطای مدیریت‌نشده در برنامه (Uncaught Crash): ${throwable.localizedMessage}",
                    throwable
                )
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    @Synchronized
    fun logError(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        try {
            val shamsiTime = JalaliDateHelper.formatShamsiDateTime(System.currentTimeMillis())
            val sb = StringBuilder()
            sb.append("────────────────────────────────────────\n")
            sb.append("زمان (شمسی): $shamsiTime\n")
            sb.append("بخش: $tag\n")
            sb.append("پیام: $message\n")
            if (throwable != null) {
                sb.append("نوع خطا: ${throwable.javaClass.name}: ${throwable.message}\n")
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                sb.append("ردیابی پشته (Stack Trace):\n")
                sb.append(sw.toString().trimEnd())
                sb.append("\n")
            }
            sb.append("────────────────────────────────────────\n\n")

            context.applicationContext.openFileOutput(ERROR_LOG_FILE, Context.MODE_APPEND).use {
                it.write(sb.toString().toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write error to log file", e)
        }
    }

    fun getErrorLogs(context: Context): String {
        return try {
            val file = File(context.applicationContext.filesDir, ERROR_LOG_FILE)
            if (!file.exists()) {
                "(تاکنون هیچ خطایی در سیستم ثبت نشده است)"
            } else {
                val text = file.readText(Charsets.UTF_8)
                if (text.isBlank()) "(تاکنون هیچ خطایی در سیستم ثبت نشده است)" else text
            }
        } catch (e: Exception) {
            "خطا در خواندن فایل لاگ خطاها: ${e.message}"
        }
    }

    fun clearErrorLogs(context: Context): Boolean {
        return try {
            val file = File(context.applicationContext.filesDir, ERROR_LOG_FILE)
            if (file.exists()) file.delete() else true
        } catch (_: Exception) {
            false
        }
    }
}
