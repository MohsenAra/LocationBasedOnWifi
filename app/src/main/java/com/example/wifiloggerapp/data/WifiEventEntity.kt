package com.example.wifiloggerapp.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "wifi_events")
data class WifiEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ssid: String?, val bssid: String?, val eventType: String, val timestamp: Long
)
