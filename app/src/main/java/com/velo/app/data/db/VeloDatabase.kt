package com.velo.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.velo.app.data.model.DownloadRecord

@Database(
    entities = [DownloadRecord::class],
    version = 3,
    exportSchema = false,
)
abstract class VeloDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
