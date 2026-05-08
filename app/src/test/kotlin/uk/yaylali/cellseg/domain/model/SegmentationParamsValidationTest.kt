package uk.yaylali.cellseg.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SegmentationParamsValidationTest {

    // ── Valid defaults ────────────────────────────────────────────────────────

    @Test
    fun `default params are valid`() {
        assertNull(SegmentationParams.DEFAULT.validate())
    }

    @Test
    fun `preset 4x is valid`() {
        assertNull(SegmentationParams.PRESET_4X.validate())
    }

    @Test
    fun `preset 10x is valid`() {
        assertNull(SegmentationParams.PRESET_10X.validate())
    }

    @Test
    fun `preset 20x is valid`() {
        assertNull(SegmentationParams.PRESET_20X.validate())
    }

    // ── maxResize bounds ──────────────────────────────────────────────────────

    @Test
    fun `maxResize at lower bound 256 is valid`() {
        assertNull(SegmentationParams(maxResize = 256).validate())
    }

    @Test
    fun `maxResize at upper bound 2000 is valid`() {
        assertNull(SegmentationParams(maxResize = 2000).validate())
    }

    @Test
    fun `maxResize below 256 is invalid`() {
        assertNotNull(SegmentationParams(maxResize = 255).validate())
    }

    @Test
    fun `maxResize above 2000 is invalid`() {
        assertNotNull(SegmentationParams(maxResize = 2001).validate())
    }

    @Test
    fun `maxResize 0 is invalid`() {
        assertNotNull(SegmentationParams(maxResize = 0).validate())
    }

    // ── maxIter bounds ────────────────────────────────────────────────────────

    @Test
    fun `maxIter at lower bound 100 is valid`() {
        assertNull(SegmentationParams(maxIter = 100).validate())
    }

    @Test
    fun `maxIter at upper bound 500 is valid`() {
        assertNull(SegmentationParams(maxIter = 500).validate())
    }

    @Test
    fun `maxIter below 100 is invalid`() {
        assertNotNull(SegmentationParams(maxIter = 99).validate())
    }

    @Test
    fun `maxIter above 500 is invalid`() {
        assertNotNull(SegmentationParams(maxIter = 501).validate())
    }

    // ── diameter bounds ───────────────────────────────────────────────────────

    @Test
    fun `diameter at lower bound 5 is valid`() {
        assertNull(SegmentationParams(diameter = 5.0).validate())
    }

    @Test
    fun `diameter at upper bound 500 is valid`() {
        assertNull(SegmentationParams(diameter = 500.0).validate())
    }

    @Test
    fun `diameter below 5 is invalid`() {
        assertNotNull(SegmentationParams(diameter = 4.9).validate())
    }

    @Test
    fun `diameter above 500 is invalid`() {
        assertNotNull(SegmentationParams(diameter = 500.1).validate())
    }

    // ── flowThreshold bounds ──────────────────────────────────────────────────

    @Test
    fun `flowThreshold 0 is valid`() {
        assertNull(SegmentationParams(flowThreshold = 0.0f).validate())
    }

    @Test
    fun `flowThreshold 3 is valid`() {
        assertNull(SegmentationParams(flowThreshold = 3.0f).validate())
    }

    @Test
    fun `flowThreshold negative is invalid`() {
        assertNotNull(SegmentationParams(flowThreshold = -0.1f).validate())
    }

    @Test
    fun `flowThreshold above 3 is invalid`() {
        assertNotNull(SegmentationParams(flowThreshold = 3.1f).validate())
    }

    // ── cellProbThreshold bounds ──────────────────────────────────────────────

    @Test
    fun `cellProbThreshold at -6 is valid`() {
        assertNull(SegmentationParams(cellProbThreshold = -6.0f).validate())
    }

    @Test
    fun `cellProbThreshold at 6 is valid`() {
        assertNull(SegmentationParams(cellProbThreshold = 6.0f).validate())
    }

    @Test
    fun `cellProbThreshold below -6 is invalid`() {
        assertNotNull(SegmentationParams(cellProbThreshold = -6.1f).validate())
    }

    @Test
    fun `cellProbThreshold above 6 is invalid`() {
        assertNotNull(SegmentationParams(cellProbThreshold = 6.1f).validate())
    }

    // ── Error message content ─────────────────────────────────────────────────

    @Test
    fun `invalid maxResize returns message containing 256`() {
        val msg = SegmentationParams(maxResize = 100).validate()
        assertNotNull(msg)
        assertTrue(msg!!.contains("256"))
    }

    @Test
    fun `invalid diameter returns message containing diameter`() {
        val msg = SegmentationParams(diameter = 1.0).validate()
        assertNotNull(msg)
        assertTrue(msg!!.lowercase().contains("diameter"))
    }
}
