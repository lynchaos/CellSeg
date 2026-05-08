package uk.yaylali.cellseg.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MaskDecoderTest {

    @Test
    fun `cellCount returns 0 for empty label image`() {
        assertEquals(0, MaskDecoder.cellCount(IntArray(16) { 0 }))
    }

    @Test
    fun `cellCount returns max label value`() {
        val labels = intArrayOf(0, 1, 2, 2, 3, 1, 0, 3)
        assertEquals(3, MaskDecoder.cellCount(labels))
    }

    @Test
    fun `cellAreas excludes background`() {
        val labels = intArrayOf(0, 1, 1, 2, 0, 2, 2, 0)
        val areas = MaskDecoder.cellAreas(labels)
        assertFalse(areas.containsKey(0), "Background should not appear in areas")
        assertEquals(2, areas[1])
        assertEquals(3, areas[2])
    }

    @Test
    fun `confluencePercent is 0 for all-background`() {
        val labels = IntArray(100) { 0 }
        assertEquals(0f, MaskDecoder.confluencePercent(labels), 0.001f)
    }

    @Test
    fun `confluencePercent is 100 for all-foreground`() {
        val labels = IntArray(100) { 1 }
        assertEquals(100f, MaskDecoder.confluencePercent(labels), 0.001f)
    }

    @Test
    fun `confluencePercent is 50 for half foreground`() {
        val labels = IntArray(100) { if (it < 50) 1 else 0 }
        assertEquals(50f, MaskDecoder.confluencePercent(labels), 0.001f)
    }
}
