package com.astra.quality

/** Immutable scientific-quality measurements for one calibrated linear frame. */
data class FrameQualityReport(
    val backgroundMean: Double,
    val backgroundNoise: Double,
    val snr: Double,
    val starCount: Int,
    val medianFwhm: Double,
    val medianEllipticity: Double,
    val medianTrailing: Double,
    val saturatedPixelCount: Int,
    val saturatedPixelRatio: Double,
    val qualityScore: Double,
    val accepted: Boolean,
    val rejectionReasons: List<String>
) {
    fun toUserMessage(): String = buildString {
        appendLine("Frame quality: ${"%.1f".format(qualityScore)}/100")
        appendLine("Accepted: $accepted")
        appendLine("Stars: $starCount")
        appendLine("SNR: ${format(snr)}")
        appendLine("Background: ${format(backgroundMean)} ± ${format(backgroundNoise)}")
        appendLine("FWHM: ${format(medianFwhm)} px")
        appendLine("Ellipticity: ${format(medianEllipticity)}")
        appendLine("Trailing: ${format(medianTrailing)}")
        appendLine("Saturated pixels: $saturatedPixelCount (${"%.4f".format(saturatedPixelRatio * 100.0)}%)")
        if (rejectionReasons.isNotEmpty()) {
            appendLine("Reasons:")
            rejectionReasons.forEach { appendLine("- $it") }
        }
    }

    private fun format(value: Double): String = if (value.isFinite()) "%.4f".format(value) else "N/A"
}
