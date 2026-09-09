package com.astra.core.storage.db

import com.astra.core.model.FrameType
import com.astra.core.model.ImageFrame
import com.astra.core.model.ImageMetadata
import com.astra.core.model.ObservationSession
import com.astra.core.model.SessionStatus

fun ObservationSession.toEntity(): ObservationSessionEntity = ObservationSessionEntity(
    id = id,
    name = name,
    createdAtUtc = createdAtUtc,
    cameraModel = cameraModel,
    target = target,
    status = status.name,
    calibrationDescription = calibrationDescription,
    processingPipelineId = processingPipelineId,
    totalIntegrationSeconds = totalIntegrationSeconds
)

/**
 * [frameIds] isn't a column on the entity (frames reference their session,
 * not the other way around), so it's supplied by the repository after a
 * separate query — see [RoomSessionRepository].
 */
fun ObservationSessionEntity.toDomain(frameIds: List<String>): ObservationSession = ObservationSession(
    id = id,
    name = name,
    createdAtUtc = createdAtUtc,
    cameraModel = cameraModel,
    target = target,
    status = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.CREATED),
    frameIds = frameIds,
    calibrationDescription = calibrationDescription,
    processingPipelineId = processingPipelineId,
    totalIntegrationSeconds = totalIntegrationSeconds
)

fun ImageMetadata.toEntity(): ImageMetadataEntity = ImageMetadataEntity(
    timestampUtc = timestampUtc,
    latitude = latitude,
    longitude = longitude,
    altitudeMeters = altitudeMeters,
    cameraModel = cameraModel,
    sensorModel = sensorModel,
    lensModel = lensModel,
    focalLengthMm = focalLengthMm,
    apertureFNumber = apertureFNumber,
    exposureTimeSeconds = exposureTimeSeconds,
    gain = gain,
    iso = iso,
    temperatureCelsius = temperatureCelsius,
    imageWidthPx = imageWidthPx,
    imageHeightPx = imageHeightPx,
    pixelSizeMicrons = pixelSizeMicrons,
    orientationDegrees = orientationDegrees,
    rightAscensionDeg = rightAscensionDeg,
    declinationDeg = declinationDeg,
    azimuthDeg = azimuthDeg,
    altitudeAngleDeg = altitudeAngleDeg
)

fun ImageMetadataEntity.toDomain(): ImageMetadata = ImageMetadata(
    timestampUtc = timestampUtc,
    latitude = latitude,
    longitude = longitude,
    altitudeMeters = altitudeMeters,
    cameraModel = cameraModel,
    sensorModel = sensorModel,
    lensModel = lensModel,
    focalLengthMm = focalLengthMm,
    apertureFNumber = apertureFNumber,
    exposureTimeSeconds = exposureTimeSeconds,
    gain = gain,
    iso = iso,
    temperatureCelsius = temperatureCelsius,
    imageWidthPx = imageWidthPx,
    imageHeightPx = imageHeightPx,
    pixelSizeMicrons = pixelSizeMicrons,
    orientationDegrees = orientationDegrees,
    rightAscensionDeg = rightAscensionDeg,
    declinationDeg = declinationDeg,
    azimuthDeg = azimuthDeg,
    altitudeAngleDeg = altitudeAngleDeg
)

fun ImageFrame.toEntity(): ImageFrameEntity = ImageFrameEntity(
    id = id,
    sessionId = sessionId,
    frameType = frameType.name,
    filePath = filePath,
    derivedFromFrameId = derivedFromFrameId,
    qualityScore = qualityScore,
    metadata = metadata.toEntity()
)

fun ImageFrameEntity.toDomain(): ImageFrame = ImageFrame(
    id = id,
    sessionId = sessionId,
    frameType = runCatching { FrameType.valueOf(frameType) }.getOrDefault(FrameType.LIGHT),
    filePath = filePath,
    metadata = metadata.toDomain(),
    derivedFromFrameId = derivedFromFrameId,
    qualityScore = qualityScore
)
