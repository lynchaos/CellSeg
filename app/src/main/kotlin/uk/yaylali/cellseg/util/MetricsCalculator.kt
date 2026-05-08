package uk.yaylali.cellseg.util

import uk.yaylali.cellseg.domain.model.CellMetrics
import java.time.Instant
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Computes [CellMetrics] from a label image.
 *
 * Scope:
 * - Cell count
 * - Confluence %
 * - Area statistics (mean, median, stdev) in pixels
 * - Area in µm² when a calibration (px/µm) is provided
 * - Estimated density (cells / cm²) when field-of-view dimensions are provided
 * - 20-bin area histogram (by pixel area)
 *
 * Not computed: viability, circularity, 3D volume — out of scope per spec.
 */
@Singleton
class MetricsCalculator @Inject constructor() {

    private companion object {
        const val HISTOGRAM_BINS = 20
    }

    /**
     * @param runId          Run this metrics record belongs to.
     * @param labels         Row-major label image (0 = background).
     * @param pxPerMicron    Calibration factor; null if unknown.
     * @param fovWidthMm     Full field-of-view width in mm; null if unknown.
     * @param fovHeightMm    Full field-of-view height in mm; null if unknown.
     */
    fun compute(
        runId: String,
        labels: IntArray,
        pxPerMicron: Float? = null,
        fovWidthMm: Float? = null,
        fovHeightMm: Float? = null,
    ): CellMetrics {
        val areas = MaskDecoder.cellAreas(labels)
        val sortedAreas = areas.values.sorted()
        val n = sortedAreas.size

        val confluencePct = MaskDecoder.confluencePercent(labels)

        val meanPx = if (n == 0) 0f else sortedAreas.sum().toFloat() / n
        val medianPx = if (n == 0) 0f else {
            if (n % 2 == 1) sortedAreas[n / 2].toFloat()
            else (sortedAreas[n / 2 - 1] + sortedAreas[n / 2]) / 2f
        }
        val stdevPx = if (n < 2) 0f else {
            val variance = sortedAreas.sumOf { a ->
                val d = a.toFloat() - meanPx
                (d * d).toDouble()
            } / (n - 1)
            sqrt(variance).toFloat()
        }

        val meanUm2 = pxPerMicron?.let { ppm ->
            val pxPerUm2 = ppm * ppm
            meanPx / pxPerUm2
        }

        val densityCellsPerCm2: Float? =
            if (fovWidthMm != null && fovHeightMm != null && n > 0) {
                val fovAreaCm2 = (fovWidthMm / 10f) * (fovHeightMm / 10f)
                if (fovAreaCm2 > 0f) n.toFloat() / fovAreaCm2 else null
            } else null

        val histogram = computeHistogram(sortedAreas, HISTOGRAM_BINS)

        return CellMetrics(
            runId = runId,
            cellCount = n,
            confluencePercent = confluencePct,
            meanCellAreaPx = meanPx,
            medianCellAreaPx = medianPx,
            cellAreaPxStdev = stdevPx,
            meanCellAreaUm2 = meanUm2,
            estimatedDensityCellsPerCm2 = densityCellsPerCm2,
            areaHistogramBins = histogram,
            computedAt = Instant.now(),
        )
    }

    private fun computeHistogram(sorted: List<Int>, bins: Int): List<Int> {
        if (sorted.isEmpty()) return List(bins) { 0 }
        val min = sorted.first().toFloat()
        val max = sorted.last().toFloat()
        val range = (max - min).coerceAtLeast(1f)
        val counts = IntArray(bins)
        for (area in sorted) {
            val binIdx = (((area - min) / range) * bins).toInt().coerceIn(0, bins - 1)
            counts[binIdx]++
        }
        return counts.toList()
    }
}
