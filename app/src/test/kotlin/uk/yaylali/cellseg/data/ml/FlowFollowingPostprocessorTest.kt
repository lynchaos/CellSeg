package uk.yaylali.cellseg.data.ml

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FlowFollowingPostprocessorTest {

    /**
     * output layout is (3, H, W): dY, dX, cellprob.
     */
    private fun makeOutput(h: Int, w: Int, dy: Float = 0f, dx: Float = 0f, prob: Float = 0f): FloatArray {
        val hw = h * w
        return FloatArray(3 * hw) { i ->
            when {
                i < hw      -> dy
                i < 2 * hw  -> dx
                else        -> prob
            }
        }
    }

    @Test
    fun `returns label array of correct size`() {
        val h = 4; val w = 4
        val output = makeOutput(h, w)
        val labels = FlowFollowingPostprocessor.postprocess(output, width = w, height = h)
        assertEquals(h * w, labels.size)
    }

    @Test
    fun `label indices are non-negative`() {
        val h = 8; val w = 8
        val output = makeOutput(h, w, dy = 0.1f, dx = 0.1f, prob = 1f)
        val labels = FlowFollowingPostprocessor.postprocess(output, width = w, height = h)
        assertTrue(labels.all { it >= 0 }, "All labels must be >= 0")
    }

    @Test
    fun `zero cell probability produces all-background labels`() {
        val h = 4; val w = 4
        val output = makeOutput(h, w, prob = -10f)
        val labels = FlowFollowingPostprocessor.postprocess(output, width = w, height = h,
            cellprobThreshold = 0f)
        assertTrue(labels.all { it == 0 }, "All background pixels must be label 0")
    }
}
