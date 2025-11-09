package com.example.wifiloggerapp.data
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface WifiEventDao {
    @Insert suspend fun insert(event: WifiEventEntity)
    @Query("SELECT * FROM wifi_events ORDER BY timestamp DESC") fun getAll(): Flow<List<WifiEventEntity>>
}
