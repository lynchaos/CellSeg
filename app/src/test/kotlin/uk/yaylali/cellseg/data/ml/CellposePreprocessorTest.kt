package uk.yaylali.cellseg.data.ml

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

/**
 * Tests for internal normalisation logic of [CellposePreprocessor].
 * Uses reflection to access private helpers because the public API requires [android.graphics.Bitmap].
 */
class CellposePreprocessorTest {

    /** Invoke the private `percentiles(data)` method via reflection. */
    private fun percentiles(data: FloatArray): Pair<Float, Float> {
        val m: Method = CellposePreprocessor::class.java
            .getDeclaredMethod("percentiles", FloatArray::class.java)
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return m.invoke(CellposePreprocessor, data) as Pair<Float, Float>
    }

    @Test
    fun `percentiles of ascending range have lo less than hi`() {
        val data = FloatArray(1000) { it.toFloat() }
        val (lo, hi) = percentiles(data)
        assertTrue(lo < hi, "lo percentile should be less than hi percentile")
    }

    @Test
    fun `percentiles of constant array are equal`() {
        val data = FloatArray(100) { 42f }
        val (lo, hi) = percentiles(data)
        assertEquals(lo, hi, 0.01f, "percentiles of constant input should be equal")
    }

    @Test
    fun `percentiles respect order for random data`() {
        val rng = java.util.Random(42)
        val data = FloatArray(500) { rng.nextFloat() * 255f }
        val (lo, hi) = percentiles(data)
        assertTrue(lo <= hi, "lo <= hi must hold")
    }
}
