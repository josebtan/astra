package com.astra.core.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ObservationSessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: ObservationSessionEntity)

    @Update
    suspend fun update(session: ObservationSessionEntity)

    @Query("SELECT * FROM observation_sessions WHERE id = :id")
    suspend fun getById(id: String): ObservationSessionEntity?

    @Query("SELECT * FROM observation_sessions ORDER BY createdAtUtc DESC")
    suspend fun getAll(): List<ObservationSessionEntity>

    @Query("DELETE FROM observation_sessions WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ImageFrameDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(frame: ImageFrameEntity)

    @Query("SELECT * FROM image_frames WHERE sessionId = :sessionId ORDER BY id")
    suspend fun getForSession(sessionId: String): List<ImageFrameEntity>

    @Query("SELECT id FROM image_frames WHERE sessionId = :sessionId ORDER BY id")
    suspend fun getIdsForSession(sessionId: String): List<String>
}
