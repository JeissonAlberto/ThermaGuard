package com.jeissonalberto.thermaguard.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ThermalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: ThermalSnapshot)

    @Query("SELECT * FROM thermal_history ORDER BY timestamp DESC LIMIT 1")
    fun getLatest(): Flow<ThermalSnapshot?>

    @Query("SELECT * FROM thermal_history ORDER BY timestamp DESC LIMIT :limit")
    fun getHistory(limit: Int = 100): Flow<List<ThermalSnapshot>>

    @Query("SELECT * FROM thermal_history WHERE timestamp > :since ORDER BY timestamp ASC")
    fun getHistorySince(since: Long): Flow<List<ThermalSnapshot>>

    @Query("SELECT AVG(batteryTemp) FROM thermal_history WHERE timestamp > :since")
    suspend fun avgBatteryTempSince(since: Long): Float?

    @Query("SELECT MAX(batteryTemp) FROM thermal_history WHERE timestamp > :since")
    suspend fun maxBatteryTempSince(since: Long): Float?

    @Query("SELECT topApp, COUNT(*) as count FROM thermal_history WHERE batteryTemp > 40 GROUP BY topApp ORDER BY count DESC LIMIT 5")
    suspend fun topHotApps(): List<AppHeatStat>

    @Query("DELETE FROM thermal_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT COUNT(*) FROM thermal_history")
    suspend fun count(): Int
}

data class AppHeatStat(
    val topApp: String,
    val count: Int
)
