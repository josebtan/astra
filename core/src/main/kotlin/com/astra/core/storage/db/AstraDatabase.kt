package com.astra.core.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ObservationSessionEntity::class, ImageFrameEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AstraDatabase : RoomDatabase() {
    abstract fun observationSessionDao(): ObservationSessionDao
    abstract fun imageFrameDao(): ImageFrameDao

    companion object {
        const val DATABASE_NAME = "astra.db"
    }
}
