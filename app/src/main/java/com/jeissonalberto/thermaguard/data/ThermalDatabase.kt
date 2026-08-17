package com.jeissonalberto.thermaguard.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ThermalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: ThermalSnapshot)

    @Query("SELECT * FROM thermal_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ThermalSnapshot>>

    @Query("DELETE FROM thermal_history WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM thermal_history")
    suspend fun deleteAll()
}

@Database(entities = [ThermalSnapshot::class], version = 2, exportSchema = false)
abstract class ThermalDatabase : RoomDatabase() {
    abstract fun thermalDao(): ThermalDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE thermal_history ADD COLUMN batteryLevel INTEGER")
                database.execSQL("ALTER TABLE thermal_history ADD COLUMN isCharging INTEGER")
                database.execSQL("ALTER TABLE thermal_history ADD COLUMN batteryVoltageMv INTEGER")
                database.execSQL("ALTER TABLE thermal_history ADD COLUMN batteryCurrentMicroamps INTEGER")
            }
        }

        @Volatile
        private var instance: ThermalDatabase? = null

        fun getInstance(context: Context): ThermalDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ThermalDatabase::class.java,
                    "thermal_history.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
