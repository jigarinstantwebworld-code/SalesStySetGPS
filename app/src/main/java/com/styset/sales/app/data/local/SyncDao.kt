package com.styset.sales.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.styset.sales.app.models.SyncDataEntity

@Dao
interface SyncDao {
    @Insert
    suspend fun insertSyncRecord(record: SyncDataEntity)

    @Query("SELECT * FROM sync_data WHERE apiName = :apiName AND status = 1 ORDER BY lastSyncedTime DESC LIMIT 1")
    suspend fun getLastSuccessfulSync(apiName: String): SyncDataEntity?

    @Query("SELECT * FROM sync_data WHERE apiName = :apiName ORDER BY lastSyncedTime DESC LIMIT 1")
    suspend fun getLastSyncAttempt(apiName: String): SyncDataEntity?

    @Query("SELECT COUNT(*) FROM stops WHERE startWallTimeMillis > :fromTime AND startWallTimeMillis <= :toTime")
    suspend fun getStopsCountInRange(fromTime: Long, toTime: Long): Int


    @Query("SELECT * FROM sync_data ORDER BY lastSyncedTime DESC")
    suspend fun getAllSyncRecords(): List<SyncDataEntity>

    @Query("DELETE FROM sync_data")
    suspend fun deleteAllSyncRecords()

    // ✅ Add this new method to get last successful sync time for STOPS
    @Query("SELECT * FROM sync_data WHERE status = 1 ORDER BY lastSyncedTime DESC")
    suspend fun getAllSuccessfulSyncRecords(): List<SyncDataEntity>

    @Query("SELECT * FROM sync_data WHERE status = 1 ORDER BY lastSyncedTime DESC LIMIT 1")
    suspend fun getLatestSuccessfulSyncRecord(): SyncDataEntity?

    @Query("DELETE FROM sync_data WHERE lastSyncedTime < :olderThan")
    suspend fun deleteRecordsOlderThan(olderThan: Long): Int

    @Query("SELECT COUNT(*) FROM sync_data WHERE lastSyncedTime < :cutoffTime")
    suspend fun getOldRecordsCount(cutoffTime: Long): Int


    @Query("""
        SELECT * FROM sync_data 
        WHERE status = 1 
        AND lastSyncedTime >= :lastWeekStart
        ORDER BY lastSyncedTime DESC 
        LIMIT 1
    """)
    suspend fun getLatestSyncFromLastWeek(lastWeekStart: Long): SyncDataEntity?

    @Query("""
        SELECT * FROM sync_data 
        WHERE lastSyncedTime >= :lastWeekStart
        ORDER BY lastSyncedTime DESC
    """)
    suspend fun getLastWeekSyncRecords(lastWeekStart: Long): List<SyncDataEntity>
}