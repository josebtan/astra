package com.astra.core.storage.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Flattened, embeddable copy of [com.astra.core.model.ImageMetadata] for
 * storage. Kept as a plain data class (not the domain model itself) for the
 * same reason as [ObservationSessionEntity]: the on-disk shape should be
 * free to diverge from the domain model over time.
 */
data class ImageMetadataEntity(
    val timestampUtc: String,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Double?,
    val cameraModel: String,
    val sensorModel: String?,
    val lensModel: String?,
    val focalLengthMm: Double?,
    val apertureFNumber: Double?,
    val exposureTimeSeconds: Double,
    val gain: Double?,
    val iso: Int?,
    val temperatureCelsius: Double?,
    val imageWidthPx: Int,
    val imageHeightPx: Int,
    val pixelSizeMicrons: Double?,
    val orientationDegrees: Double?,
    val rightAscensionDeg: Double?,
    val declinationDeg: Double?,
    val azimuthDeg: Double?,
    val altitudeAngleDeg: Double?
)

/**
 * Room persistence model for [com.astra.core.model.ImageFrame].
 *
 * Cascades on delete: removing a session removes its frames too, so we
 * never end up with orphaned RAW-frame rows pointing at a deleted session —
 * consistent with the "los datos son sagrados" principle applying to the
 * *database* record, not just the files on disk (roadmap section 4).
 */
@Entity(
    tableName = "image_frames",
    foreignKeys = [
        ForeignKey(
            entity = ObservationSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ImageFrameEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val frameType: String,
    val filePath: String,
    val derivedFromFrameId: String?,
    val qualityScore: Double?,
    @Embedded(prefix = "metadata_") val metadata: ImageMetadataEntity
)
