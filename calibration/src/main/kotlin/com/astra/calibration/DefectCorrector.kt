package com.astra.calibration

import com.astra.core.model.LinearImage

/**
 * Replaces each defect pixel's value with the median of its non-defective
 * 3x3 neighbors — a standard, parameter-free hot/dead pixel removal
 * technique. Falls back to the image's global median only if every
 * neighbor is itself defective (e.g. a small cluster of adjacent defects).
 */
object DefectCorrector {

    fun correct(image: LinearImage, defects: List<DefectPixel>): LinearImage {
        if (defects.isEmpty()) return image

        val defectIndices = defects.map { it.y * image.width + it.x }.toHashSet()
        val corrected = image.data.copyOf()
        val globalMedian by lazy { median(image.data) }

        for (defect in defects) {
            val neighborValues = neighborsOf(defect.x, defect.y, image.width, image.height)
                .map { (nx, ny) -> ny * image.width + nx }
                .filterNot { it in defectIndices }
                .map { image.data[it] }

            val index = defect.y * image.width + defect.x
            corrected[index] = if (neighborValues.isNotEmpty()) {
                median(neighborValues.toFloatArray())
            } else {
                globalMedian
            }
        }

        return LinearImage(image.width, image.height, image.channels, image.bitDepth, corrected)
    }

    private fun neighborsOf(x: Int, y: Int, width: Int, height: Int): List<Pair<Int, Int>> =
        buildList {
            for (dy in -1..1) {
                for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until height) add(nx to ny)
                }
            }
        }

    private fun median(values: FloatArray): Float {
        val sorted = values.copyOf().also { it.sort() }
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2f
    }
}
