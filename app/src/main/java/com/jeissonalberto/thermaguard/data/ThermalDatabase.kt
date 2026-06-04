package com.jeissonalberto.thermaguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ThermalSnapshot::class],
    version = 1,
    exportSchema = false
)
abstract class ThermalDatabase : RoomDatabase() {

    abstract fun thermalDao(): ThermalDao

    companion object {
        @Volatile
        private var INSTANCE: ThermalDatabase? = null

        fun getInstance(context: Context): ThermalDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    ThermalDatabase::class.java,
                    "thermaguard.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
