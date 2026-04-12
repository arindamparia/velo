package com.velo.app.di

import android.content.Context
import androidx.room.Room
import com.velo.app.data.db.DownloadDao
import com.velo.app.data.db.VeloDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VeloDatabase =
        // When bumping VeloDatabase.version, add a Migration(oldVer, newVer) here instead of
        // using fallbackToDestructiveMigration, which wipes all user download history on update.
        Room.databaseBuilder(context, VeloDatabase::class.java, "velo.db")
            .build()

    @Provides
    fun provideDownloadDao(db: VeloDatabase): DownloadDao = db.downloadDao()
}
