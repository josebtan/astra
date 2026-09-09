package com.astra.core.storage

import com.astra.core.model.ImageFrame
import com.astra.core.model.ObservationSession

/**
 * Persists [ObservationSession]s and their [ImageFrame]s.
 *
 * Kept free of any Room/Android import so callers (UI, capture pipeline)
 * depend only on this contract. [com.astra.core.storage.db.RoomSessionRepository]
 * is the real, disk-backed implementation.
 */
interface SessionRepository {

    suspend fun createSession(session: ObservationSession)

    suspend fun updateSession(session: ObservationSession)

    suspend fun getSession(id: String): ObservationSession?

    suspend fun getAllSessions(): List<ObservationSession>

    suspend fun addFrame(frame: ImageFrame)

    suspend fun getFramesForSession(sessionId: String): List<ImageFrame>
}
