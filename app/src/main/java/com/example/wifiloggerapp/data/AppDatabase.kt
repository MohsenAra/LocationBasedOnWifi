package com.example.wifiloggerapp.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
@Database(entities = [WifiEventEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wifiEventDao(): WifiEventDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "wifi_logger_db"
                        ).build()
                    }
                }
            }
        }
        fun getInstance(): AppDatabase =
            INSTANCE ?: throw IllegalStateException("AppDatabase not initialized")
    }
}
