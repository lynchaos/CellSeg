package uk.yaylali.cellseg.domain.model

import java.time.Instant

/**
 * Derived metrics computed from the integer label image.
 * µm² and density fields are null when calibration is absent.
 *
 * IMPORTANT: This app does NOT compute viability, aspect-ratio, circularity,
 * or any 3D/Z-stack metric. Those are explicitly out of scope for v1.
 */
data class CellMetrics(
    val runId: String,
    val cellCount: Int,
    val confluencePercent: Float,
    val meanCellAreaPx: Float,
    val medianCellAreaPx: Float,
    val cellAreaPxStdev: Float,
    val meanCellAreaUm2: Float?,
    val estimatedDensityCellsPerCm2: Float?,
    /** 20 equal-width bins from 0 to p99(areas). */
    val areaHistogramBins: List<Int>,
    val computedAt: Instant,
)
