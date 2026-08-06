package com.jeissonalberto.thermaguard.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ThermalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: ThermalSnapshot)

    @Query("SELECT * FROM thermal_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ThermalSnapshot>>

    @Query("DELETE FROM thermal_history WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Database(entities = [ThermalSnapshot::class], version = 1, exportSchema = false)
abstract class ThermalDatabase : RoomDatabase() {
    abstract fun thermalDao(): ThermalDao

    companion object {
        @Volatile
        private var instance: ThermalDatabase? = null

        fun getInstance(context: Context): ThermalDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ThermalDatabase::class.java,
                    "thermal_history.db"
                ).build().also { instance = it }
            }
    }
}
