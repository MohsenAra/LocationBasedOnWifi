package com.example.wifiloggerapp.util

import android.content.Context
import android.content.SharedPreferences

class NetworkAliasManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("wifi_network_aliases", Context.MODE_PRIVATE)

    fun getAlias(ssid: String?): String? {
        if (ssid.isNullOrBlank()) return null
        val clean = ssid.trim()
        val alias = prefs.getString(clean, null)
        return if (!alias.isNullOrBlank()) alias else null
    }

    fun setAlias(ssid: String, alias: String) {
        val cleanSsid = ssid.trim()
        val cleanAlias = alias.trim()
        if (cleanAlias.isEmpty()) {
            prefs.edit().remove(cleanSsid).apply()
        } else {
            prefs.edit().putString(cleanSsid, cleanAlias).apply()
        }
    }

    fun removeAlias(ssid: String) {
        prefs.edit().remove(ssid.trim()).apply()
    }

    fun getAllAliases(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        prefs.all.forEach { (k, v) ->
            if (v is String && v.isNotBlank()) {
                result[k] = v
            }
        }
        return result
    }

    fun getDisplayName(ssid: String?): String {
        if (ssid.isNullOrBlank()) return "<نامشخص>"
        val alias = getAlias(ssid)
        return if (alias != null) "$alias ($ssid)" else ssid
    }
}
