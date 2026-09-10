package com.astra.calibration

import com.astra.core.model.LinearImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationEngineTest {

    @Test
    fun `applies bias, dark and flat when all are available, uniform-flat case`() {
        val light = uniform(0.6f)
        val bias = listOf(uniform(0.1f), uniform(0.1f))
        val dark = listOf(uniform(0.15f), uniform(0.15f)) // includes the 0.1 bias component
        val flat = listOf(uniform(0.8f), uniform(0.8f))   // uniform flat -> normalizes to exactly 1.0

        val result = CalibrationEngine.calibrate(light, bias, dark, flat)

        // 0.6 - bias(0.1) - darkCurrentOnly(0.15-0.1=0.05) = 0.45, then /1.0 unchanged
        result.calibratedImage.data.forEach { assertEquals(0.45f, it, 1e-5f) }

        val steps = result.report.steps.associateBy { it.type }
        assertTrue(steps.getValue(CalibrationStepType.BIAS_SUBTRACTION).applied)
        assertTrue(steps.getValue(CalibrationStepType.DARK_SUBTRACTION).applied)
        assertTrue(steps.getValue(CalibrationStepType.FLAT_DIVISION).applied)
        // uniform dark -> stddev 0 -> no defects to report
        assertFalse(steps.getValue(CalibrationStepType.DEFECT_CORRECTION).applied)
        assertEquals(0, result.report.defectsDetected)
    }

    @Test
    fun `skips every step and reports why when no calibration frames are available`() {
        val light = uniform(0.6f)

        val result = CalibrationEngine.calibrate(light)

        result.report.steps.forEach { step ->
            assertFalse(step.applied)
            assertTrue(step.message.isNotBlank())
        }
        assertEquals(light.data.average(), result.calibratedImage.data.average(), 1e-6)
        assertEquals(result.report.meanSignalBefore, result.report.meanSignalAfter, 1e-6)
    }

    @Test
    fun `detects and corrects a defect introduced by the dark frames`() {
        // 3x3 grid so the defect pixel has real neighbors to be corrected from.
        // Background has tiny jitter (not all-identical) so the robust
        // median/MAD statistic isn't degenerate - same pattern as
        // DefectMapBuilderTest's multi-outlier case.
        val lightValues = FloatArray(9) { 0.5f }
        val light = LinearImage(3, 3, 1, 16, lightValues)

        val darkBase = floatArrayOf(0.099f, 0.100f, 0.101f, 0.0995f, 0.95f, 0.1005f, 0.099f, 0.101f, 0.100f)
        val dark = listOf(LinearImage(3, 3, 1, 16, darkBase)) // center pixel (index 4) is a clear hot pixel

        val result = CalibrationEngine.calibrate(light, darkFrames = dark)

        assertEquals(1, result.report.defectsDetected)
        assertEquals(1, result.report.defectsCorrected)
        val defectStep = result.report.steps.first { it.type == CalibrationStepType.DEFECT_CORRECTION }
        assertTrue(defectStep.applied)

        // the center pixel should have been corrected using neighbor light values,
        // not left equal to (light - the bogus 0.95 dark) which would be negative/clamped to 0
        assertTrue(result.calibratedImage.data[4] > 0.3f)
    }

    @Test
    fun `toUserMessage produces a non-empty human-readable summary`() {
        val result = CalibrationEngine.calibrate(uniform(0.5f))

        val message = result.report.toUserMessage()

        assertTrue(message.contains("skipped"))
        assertTrue(message.isNotBlank())
    }

    private fun uniform(value: Float) = LinearImage(width = 2, height = 2, channels = 1, bitDepth = 16, data = FloatArray(4) { value })
}
