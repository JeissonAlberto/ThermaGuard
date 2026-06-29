package com.jeissonalberto.thermaguard.data
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ThermalDao {
    @Insert
    suspend fun insert(snapshot: ThermalSnapshot)
    @Query("SELECT * FROM thermal_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLast(): ThermalSnapshot?
}

@Database(entities = [ThermalSnapshot::class], version = 1, exportSchema = false)
abstract class ThermalDatabase : RoomDatabase() {
    abstract fun thermalDao(): ThermalDao
}
