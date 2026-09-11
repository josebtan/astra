package com.astra.quality

import com.astra.core.model.LinearImage

data class RankedFrame(val index: Int, val image: LinearImage, val report: FrameQualityReport)

data class FrameRankingReport(val ranked: List<RankedFrame>) {
    val accepted: List<RankedFrame> get() = ranked.filter { it.report.accepted }
    val rejected: List<RankedFrame> get() = ranked.filter { !it.report.accepted }

    fun toUserMessage(): String = buildString {
        appendLine("Frame ranking: ${ranked.size} frame(s), ${accepted.size} accepted, ${rejected.size} rejected.")
        ranked.sortedByDescending { it.report.qualityScore }.forEach { rf ->
            val status = if (rf.report.accepted) "OK" else "REJECTED"
            appendLine("- Frame ${rf.index}: score=${"%.0f".format(rf.report.qualityScore)} -> $status")
        }
    }
}

/**
 * Ranks a batch of light frames by [FrameQualityAnalyzer] score and
 * automatically separates accepted from rejected ones (roadmap section
 * 13: "the system decides which exposures are good"), instead of
 * stacking everything blindly.
 */
object FrameRanker {

    fun rank(images: List<LinearImage>): FrameRankingReport {
        val ranked = images.mapIndexed { index, image ->
            RankedFrame(index, image, FrameQualityAnalyzer.analyze(image))
        }
        return FrameRankingReport(ranked)
    }
}
