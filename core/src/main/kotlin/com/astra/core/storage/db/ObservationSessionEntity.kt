package com.astra.core.storage.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room persistence model for [com.astra.core.model.ObservationSession].
 *
 * Deliberately a separate class from the domain model: the domain model
 * (roadmap section 9) has no Room/Android dependency and stays testable in
 * plain Kotlin; this entity is the on-disk shape and can evolve
 * independently (e.g. schema migrations) without touching domain code.
 * See [toDomain]/[toEntity] in Mappers.kt for the conversion.
 */
@Entity(tableName = "observation_sessions")
data class ObservationSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtUtc: String,
    val cameraModel: String,
    val target: String?,
    val status: String,
    val calibrationDescription: String?,
    val processingPipelineId: String?,
    val totalIntegrationSeconds: Double
)
