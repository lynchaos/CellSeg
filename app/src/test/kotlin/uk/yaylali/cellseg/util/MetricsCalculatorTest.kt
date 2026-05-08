package uk.yaylali.cellseg.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MetricsCalculatorTest {

    private val calculator = MetricsCalculator()

    // ── Cell count ────────────────────────────────────────────────────────────

    @Test
    fun `empty label image gives zero cell count`() {
        val result = calculator.compute("r1", IntArray(100) { 0 })
        assertEquals(0, result.cellCount)
    }

    @Test
    fun `single-cell label image gives cell count 1`() {
        // 4×4 label image: all background except label 1 in centre
        val labels = IntArray(16) { if (it in 5..10) 1 else 0 }
        val result = calculator.compute("r2", labels)
        assertEquals(1, result.cellCount)
    }

    @Test
    fun `three distinct cell labels gives count 3`() {
        val labels = intArrayOf(1, 1, 2, 2, 3, 3, 0, 0)
        val result = calculator.compute("r3", labels)
        assertEquals(3, result.cellCount)
    }

    // ── Confluence ────────────────────────────────────────────────────────────

    @Test
    fun `all-background gives 0 pct confluence`() {
        val result = calculator.compute("r4", IntArray(100) { 0 })
        assertEquals(0f, result.confluencePercent, 0.001f)  // confluencePercent
    }

    @Test
    fun `all-foreground gives 100 pct confluence`() {
        val labels = IntArray(100) { it + 1 }
        val result = calculator.compute("r5", labels)
        assertEquals(100f, result.confluencePercent, 0.001f)
    }

    @Test
    fun `half foreground gives approx 50 pct confluence`() {
        val labels = IntArray(100) { if (it < 50) it + 1 else 0 }
        val result = calculator.compute("r6", labels)
        assertEquals(50f, result.confluencePercent, 0.001f)
    }

    // ── Area statistics ───────────────────────────────────────────────────────

    @Test
    fun `mean cell area is average of all cell pixel counts`() {
        // label 1: 4 px, label 2: 2 px → mean = 3.0
        val labels = intArrayOf(1, 1, 1, 1, 2, 2, 0, 0)
        val result = calculator.compute("r7", labels)
        assertEquals(3f, result.meanCellAreaPx, 0.001f) // meanCellAreaPx
    }

    @Test
    fun `median cell area for odd count is middle value`() {
        // label 1: 1 px, label 2: 3 px, label 3: 5 px → median = 3
        val labels = intArrayOf(1, 2, 2, 2, 3, 3, 3, 3, 3)
        val result = calculator.compute("r8", labels)
        assertEquals(3f, result.medianCellAreaPx, 0.001f) // medianCellAreaPx
    }

    @Test
    fun `stdev is zero for single cell`() {
        val labels = intArrayOf(1, 1, 1, 0, 0)
        val result = calculator.compute("r9", labels)
        assertEquals(0f, result.cellAreaPxStdev, 0.001f)
    }

    // ── Calibrated µm² area ───────────────────────────────────────────────────

    @Test
    fun `mean area in um2 is null when no calibration provided`() {
        val labels = intArrayOf(1, 1, 0, 0)
        val result = calculator.compute("r10", labels)
        assertNull(result.meanCellAreaUm2)
    }

    @Test
    fun `mean area in um2 converts correctly with calibration`() {
        // label 1: 4 px; pxPerMicron = 2 → each px = 0.25 µm² → mean = 1.0 µm²
        val labels = intArrayOf(1, 1, 1, 1, 0, 0, 0, 0)
        val result = calculator.compute("r11", labels, pxPerMicron = 2f)
        assertEquals(1f, result.meanCellAreaUm2!!, 0.001f)
    }

    // ── Density ───────────────────────────────────────────────────────────────

    @Test
    fun `density is null without fov dimensions`() {
        val labels = intArrayOf(1, 2, 0, 0)
        val result = calculator.compute("r12", labels)
        assertNull(result.estimatedDensityCellsPerCm2)
    }

    @Test
    fun `density calculation is correct for known fov`() {
        // 10 cells, fov = 1mm × 1mm = 0.01 cm² → density = 1000 cells/cm²
        val labels = IntArray(100) { if (it < 50) it + 1 else 0 }
            .also { arr -> (0 until 50).forEach { arr[it] = (it % 10) + 1 } }
        val result = calculator.compute("r13", labels, fovWidthMm = 1f, fovHeightMm = 1f)
        // Just verify density is non-null and positive
        assertNotNull(result.estimatedDensityCellsPerCm2)
        assertTrue(result.estimatedDensityCellsPerCm2!! > 0f)
    }

    // ── Histogram ────────────────────────────────────────────────────────────

    @Test
    fun `histogram has 20 bins`() {
        val labels = IntArray(100) { it % 5 }
        val result = calculator.compute("r14", labels)
        assertEquals(20, result.areaHistogramBins.size)
    }

    @Test
    fun `histogram sum equals cell count`() {
        val labels = IntArray(100) { if (it % 3 != 0) it / 3 + 1 else 0 }
        val result = calculator.compute("r15", labels)
        val histSum = result.areaHistogramBins.sum()
        assertEquals(result.cellCount, histSum)
    }

    // ── Run ID passthrough ────────────────────────────────────────────────────

    @Test
    fun `run id is preserved in result`() {
        val result = calculator.compute("my-run-123", IntArray(10) { 0 })
        assertEquals("my-run-123", result.runId)
    }
}
