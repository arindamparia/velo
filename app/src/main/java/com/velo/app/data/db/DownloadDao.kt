package com.velo.app.data.db

import androidx.room.*
import com.velo.app.data.model.DownloadRecord
import com.velo.app.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    // Limit to 500 rows to prevent loading thousands of records into memory.
    // Active downloads are always recent so they fall within this window.
    @Query("SELECT * FROM downloads ORDER BY timestampMs DESC LIMIT 500")
    fun getAllDownloads(): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY timestampMs DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM downloads WHERE url = :url AND formatLabel = :formatLabel")
    suspend fun getRecords(url: String, formatLabel: String): List<DownloadRecord>

    @Query("DELETE FROM downloads WHERE url = :url AND formatLabel = :formatLabel")
    suspend fun deleteRecords(url: String, formatLabel: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DownloadRecord)

    @Update
    suspend fun update(record: DownloadRecord)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    // Clears both completed and failed records so stale entries don't accumulate.
    @Query("DELETE FROM downloads WHERE status = 'DONE' OR status = 'FAILED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM downloads")
    suspend fun deleteAllRecords()

    // Used for accurate storage display regardless of the display limit on getAllDownloads().
    @Query("SELECT COALESCE(SUM(fileSizeBytes), 0) FROM downloads WHERE status = 'DONE'")
    fun getTotalStorageBytes(): Flow<Long>
}
