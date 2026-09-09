package com.astra.core.storage.db

import com.astra.core.model.ImageFrame
import com.astra.core.model.ObservationSession
import com.astra.core.storage.SessionRepository

/**
 * Real, disk-backed [SessionRepository], on top of [AstraDatabase].
 *
 * A session's [ObservationSession.frameIds] is not stored directly on the
 * session row — it's derived by querying [ImageFrameDao] for frames whose
 * `sessionId` matches, which is why [getSession] and [getAllSessions] do an
 * extra query per session.
 */
class RoomSessionRepository(
    private val sessionDao: ObservationSessionDao,
    private val frameDao: ImageFrameDao
) : SessionRepository {

    override suspend fun createSession(session: ObservationSession) {
        sessionDao.insert(session.toEntity())
    }

    override suspend fun updateSession(session: ObservationSession) {
        sessionDao.update(session.toEntity())
    }

    override suspend fun getSession(id: String): ObservationSession? {
        val entity = sessionDao.getById(id) ?: return null
        return entity.toDomain(frameDao.getIdsForSession(id))
    }

    override suspend fun getAllSessions(): List<ObservationSession> =
        sessionDao.getAll().map { entity ->
            entity.toDomain(frameDao.getIdsForSession(entity.id))
        }

    override suspend fun addFrame(frame: ImageFrame) {
        frameDao.insert(frame.toEntity())
    }

    override suspend fun getFramesForSession(sessionId: String): List<ImageFrame> =
        frameDao.getForSession(sessionId).map { it.toDomain() }
}
