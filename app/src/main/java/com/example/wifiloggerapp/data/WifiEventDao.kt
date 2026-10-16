package com.example.wifiloggerapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiEventDao {
    @Insert
    suspend fun insert(event: WifiEventEntity)

    @Query("SELECT * FROM wifi_events ORDER BY timestamp DESC")
    fun getAll(): Flow<List<WifiEventEntity>>

    @Query("SELECT * FROM wifi_events ORDER BY timestamp ASC")
    suspend fun getAllList(): List<WifiEventEntity>

    @Query("SELECT * FROM wifi_events WHERE timestamp BETWEEN :startTimestamp AND :endTimestamp ORDER BY timestamp ASC")
    suspend fun getEventsBetween(startTimestamp: Long, endTimestamp: Long): List<WifiEventEntity>

    @Query("SELECT * FROM wifi_events WHERE ssid = :ssid AND timestamp BETWEEN :startTimestamp AND :endTimestamp ORDER BY timestamp ASC")
    suspend fun getEventsForSsidBetween(ssid: String, startTimestamp: Long, endTimestamp: Long): List<WifiEventEntity>
}
