package com.astra.quality

import com.astra.core.model.LinearImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Scientifically-oriented first-pass quality analysis for a single linear frame.
 *
 * Design decisions:
 * - Operates on LinearImage, so it is independent from Android and from UI.
 * - Does not modify the input image.
 * - Estimates background from the lower half of sorted pixel values. This is
 *   deliberately conservative for astronomical frames where stars are sparse.
 * - Estimates noise with the median absolute deviation (MAD), avoiding the
 *   influence of bright stars and cosmic-ray-like outliers.
 * - Detects bright star candidates above background + threshold*noise.
 * - Measures FWHM and ellipticity from local thresholded connected components.
 * - Estimates trailing from the second moments of each detected source.
 * - Saturation is reported from the normalized [0,1] linear representation.
 *
 * This is intentionally a V0.6 quality analyzer, not the final V0.9 object
 * detector. The future detection module can replace the source detector while
 * retaining the quality-report contract.
 */
object FrameQualityAnalyzer {
    data class Config(
        val detectionSigma: Double = 5.0,
        val minSourcePixels: Int = 3,
        val maxSourcePixels: Int = 10_000,
        val saturationThreshold: Double = 0.999,
        val maxSources: Int = 500
    )

    fun analyze(image: LinearImage, config: Config = Config()): FrameQualityReport {
        require(config.detectionSigma > 0.0) { "detectionSigma must be positive" }
        require(config.minSourcePixels > 0) { "minSourcePixels must be positive" }
        require(config.maxSourcePixels >= config.minSourcePixels) { "maxSourcePixels must be >= minSourcePixels" }
        require(config.saturationThreshold in 0.0..1.0) { "saturationThreshold must be in [0,1]" }
        require(config.maxSources > 0) { "maxSources must be positive" }

        val values = image.data
        val background = estimateBackground(values)
        val noise = estimateNoise(values, background)
        val threshold = background + config.detectionSigma * noise
        val saturationCount = values.count { it.toDouble() >= config.saturationThreshold }
        val saturationRatio = saturationCount.toDouble() / values.size

        val sources = detectSources(image, threshold, config)
        val starCount = sources.size
        val medianFwhm = median(sources.map { it.fwhm }.filter { it.isFinite() })
        val medianEllipticity = median(sources.map { it.ellipticity }.filter { it.isFinite() })
        val medianTrailing = median(sources.map { it.trailing }.filter { it.isFinite() })

        val signal = if (sources.isEmpty()) background else sources.map { it.peak }.average()
        val snr = if (noise > 0.0) (signal - background) / noise else if (signal > background) Double.POSITIVE_INFINITY else 0.0

        val reasons = mutableListOf<String>()
        if (starCount == 0) reasons += "No se detectaron fuentes estelares con el umbral actual"
        if (saturationRatio > 0.01) reasons += "Más del 1% de los píxeles están saturados"
        if (medianTrailing > 0.35) reasons += "Las fuentes presentan elongación compatible con trailing"
        if (snr < 3.0) reasons += "SNR global de las fuentes inferior a 3"

        val score = calculateScore(starCount, snr, saturationRatio, medianEllipticity, medianTrailing)
        val accepted = reasons.isEmpty()

        return FrameQualityReport(
            backgroundMean = background,
            backgroundNoise = noise,
            snr = snr,
            starCount = starCount,
            medianFwhm = medianFwhm,
            medianEllipticity = medianEllipticity,
            medianTrailing = medianTrailing,
            saturatedPixelCount = saturationCount,
            saturatedPixelRatio = saturationRatio,
            qualityScore = score,
            accepted = accepted,
            rejectionReasons = reasons
        )
    }

    private data class SourceMeasurement(
        val peak: Double,
        val fwhm: Double,
        val ellipticity: Double,
        val trailing: Double
    )

    private fun detectSources(image: LinearImage, threshold: Double, config: Config): List<SourceMeasurement> {
        if (image.channels != 1) return emptyList()
        val width = image.width
        val height = image.height
        val visited = BooleanArray(width * height)
        val result = ArrayList<SourceMeasurement>()
        val data = image.data

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (visited[index] || data[index].toDouble() <= threshold) continue

                val pixels = ArrayList<Int>()
                val queue = ArrayDeque<Int>()
                queue.addLast(index)
                visited[index] = true

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    pixels += current
                    val cx = current % width
                    val cy = current / width
                    for (dy in -1..1) for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = cx + dx
                        val ny = cy + dy
                        if (nx !in 0 until width || ny !in 0 until height) continue
                        val ni = ny * width + nx
                        if (!visited[ni] && data[ni].toDouble() > threshold) {
                            visited[ni] = true
                            queue.addLast(ni)
                        }
                    }
                }

                if (pixels.size !in config.minSourcePixels..config.maxSourcePixels) continue
                result += measureSource(data, width, pixels, threshold)
                if (result.size >= config.maxSources) return result
            }
        }
        return result
    }

    private fun measureSource(data: FloatArray, width: Int, pixels: List<Int>, background: Double): SourceMeasurement {
        var sum = 0.0
        var peak = Double.NEGATIVE_INFINITY
        var peakIndex = pixels.first()
        var sx = 0.0
        var sy = 0.0

        for (index in pixels) {
            val weight = max(0.0, data[index].toDouble() - background)
            val x = index % width
            val y = index / width
            sum += weight
            sx += x * weight
            sy += y * weight
            if (data[index] > peak) {
                peak = data[index].toDouble()
                peakIndex = index
            }
        }

        if (sum <= 0.0) return SourceMeasurement(peak, Double.NaN, Double.NaN, Double.NaN)
        val cx = sx / sum
        val cy = sy / sum
        var xx = 0.0
        var yy = 0.0
        var xy = 0.0
        for (index in pixels) {
            val weight = max(0.0, data[index].toDouble() - background)
            val dx = index % width - cx
            val dy = index / width - cy
            xx += weight * dx * dx
            yy += weight * dy * dy
            xy += weight * dx * dy
        }
        xx /= sum
        yy /= sum
        xy /= sum

        val trace = xx + yy
        val determinant = max(0.0, xx * yy - xy * xy)
        val discriminant = sqrt(max(0.0, trace * trace - 4.0 * determinant))
        val majorVariance = max(0.0, (trace + discriminant) / 2.0)
        val minorVariance = max(0.0, (trace - discriminant) / 2.0)
        val majorSigma = sqrt(majorVariance)
        val minorSigma = sqrt(minorVariance)
        val fwhm = 2.354820045 * sqrt((majorVariance + minorVariance) / 2.0)
        val ellipticity = if (majorSigma > 0.0) 1.0 - minorSigma / majorSigma else 0.0
        val trailing = ellipticity

        // peakIndex is deliberately retained above: it makes the peak
        // definition explicit and keeps this measurement independent from
        // future source-detection implementations.
        @Suppress("UNUSED_VARIABLE")
        val ignoredPeakLocation = peakIndex

        return SourceMeasurement(peak, fwhm, ellipticity, trailing)
    }

    private fun estimateBackground(values: FloatArray): Double {
        val sorted = values.map { it.toDouble() }.sorted()
        val count = max(1, sorted.size / 2)
        return sorted.take(count).average()
    }

    private fun estimateNoise(values: FloatArray, center: Double): Double {
        val deviations = values.map { abs(it.toDouble() - center) }.sorted()
        val mad = median(deviations)
        return max(1e-9, mad * 1.4826)
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) sorted[sorted.size / 2]
        else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    }

    private fun calculateScore(stars: Int, snr: Double, saturation: Double, ellipticity: Double, trailing: Double): Double {
        if (stars == 0) return 0.0
        val snrScore = (snr / 20.0).coerceIn(0.0, 1.0)
        val saturationScore = (1.0 - saturation * 10.0).coerceIn(0.0, 1.0)
        val shape = (1.0 - max(ellipticity, trailing)).coerceIn(0.0, 1.0)
        return (0.5 * snrScore + 0.2 * saturationScore + 0.3 * shape) * 100.0
    }
}
