package uk.yaylali.cellseg.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import uk.yaylali.cellseg.domain.model.CellMetrics
import java.time.Instant

@Entity(
    tableName = "metrics",
    foreignKeys = [ForeignKey(
        entity = RunEntity::class,
        parentColumns = ["id"],
        childColumns = ["run_id"],
        onDelete = ForeignKey.CASCADE,
    )]
)
data class MetricsEntity(
    @PrimaryKey
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "cell_count") val cellCount: Int,
    @ColumnInfo(name = "confluence_pct") val confluencePercent: Float,
    @ColumnInfo(name = "mean_area_px") val meanCellAreaPx: Float,
    @ColumnInfo(name = "median_area_px") val medianCellAreaPx: Float,
    @ColumnInfo(name = "stdev_area_px") val cellAreaPxStdev: Float,
    @ColumnInfo(name = "mean_area_um2") val meanCellAreaUm2: Float?,
    @ColumnInfo(name = "density_cells_per_cm2") val estimatedDensityCellsPerCm2: Float?,
    /** Comma-separated 20-bin histogram counts. */
    @ColumnInfo(name = "area_histogram_csv") val areaHistogramCsv: String,
    @ColumnInfo(name = "computed_at") val computedAt: Long,   // epoch ms
) {
    fun toDomain() = CellMetrics(
        runId = runId,
        cellCount = cellCount,
        confluencePercent = confluencePercent,
        meanCellAreaPx = meanCellAreaPx,
        medianCellAreaPx = medianCellAreaPx,
        cellAreaPxStdev = cellAreaPxStdev,
        meanCellAreaUm2 = meanCellAreaUm2,
        estimatedDensityCellsPerCm2 = estimatedDensityCellsPerCm2,
        areaHistogramBins = areaHistogramCsv.split(",").mapNotNull { it.trim().toIntOrNull() },
        computedAt = Instant.ofEpochMilli(computedAt),
    )

    companion object {
        fun fromDomain(m: CellMetrics) = MetricsEntity(
            runId = m.runId,
            cellCount = m.cellCount,
            confluencePercent = m.confluencePercent,
            meanCellAreaPx = m.meanCellAreaPx,
            medianCellAreaPx = m.medianCellAreaPx,
            cellAreaPxStdev = m.cellAreaPxStdev,
            meanCellAreaUm2 = m.meanCellAreaUm2,
            estimatedDensityCellsPerCm2 = m.estimatedDensityCellsPerCm2,
            areaHistogramCsv = m.areaHistogramBins.joinToString(","),
            computedAt = m.computedAt.toEpochMilli(),
        )
    }
}
