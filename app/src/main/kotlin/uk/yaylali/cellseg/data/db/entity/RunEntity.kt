package uk.yaylali.cellseg.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import uk.yaylali.cellseg.domain.model.BackendTier
import uk.yaylali.cellseg.domain.model.RunStatus
import uk.yaylali.cellseg.domain.model.SegmentationParams
import uk.yaylali.cellseg.domain.model.SegmentationRun
import java.time.Instant

@Entity(
    tableName = "runs",
    foreignKeys = [ForeignKey(
        entity = SampleEntity::class,
        parentColumns = ["id"],
        childColumns = ["sample_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["sample_id"]),
        Index(value = ["started_at"]),
    ]
)
data class RunEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "sample_id") val sampleId: String,
    @ColumnInfo(name = "max_resize") val maxResize: Int,
    @ColumnInfo(name = "max_iter") val maxIter: Int,
    @ColumnInfo(name = "flow_threshold") val flowThreshold: Float,
    @ColumnInfo(name = "cellprob_threshold") val cellprobThreshold: Float,
    @ColumnInfo(name = "diameter", defaultValue = "30.0") val diameter: Double = 30.0,
    @ColumnInfo(name = "backend_tier") val backendTier: String,
    @ColumnInfo(name = "backend_space_slug") val backendSpaceSlug: String,
    @ColumnInfo(name = "backend_version_tag") val backendVersionTag: String,
    @ColumnInfo(name = "original_image_path") val originalImagePath: String,
    @ColumnInfo(name = "outline_overlay_path") val outlineOverlayPath: String?,
    @ColumnInfo(name = "flows_path") val flowsPath: String?,
    @ColumnInfo(name = "mask_tiff_path") val maskTiffPath: String?,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,   // epoch ms
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    @ColumnInfo(name = "error") val error: String?,
) {
    fun toDomain() = SegmentationRun(
        id = id,
        sampleId = sampleId,
        params = SegmentationParams(
            maxResize = maxResize,
            maxIter = maxIter,
            flowThreshold = flowThreshold,
            cellProbThreshold = cellprobThreshold,
            diameter = diameter,
        ),
        backendTier = BackendTier.valueOf(backendTier),
        backendSpaceSlug = backendSpaceSlug,
        backendVersionTag = backendVersionTag,
        originalImagePath = originalImagePath,
        outlineOverlayPath = outlineOverlayPath,
        flowsPath = flowsPath,
        maskTiffPath = maskTiffPath,
        status = RunStatus.valueOf(status),
        startedAt = Instant.ofEpochMilli(startedAt),
        completedAt = completedAt?.let { Instant.ofEpochMilli(it) },
        error = error,
    )

    companion object {
        fun fromDomain(r: SegmentationRun) = RunEntity(
            id = r.id,
            sampleId = r.sampleId,
            maxResize = r.params.maxResize,
            maxIter = r.params.maxIter,
            flowThreshold = r.params.flowThreshold,
            cellprobThreshold = r.params.cellProbThreshold,
            diameter = r.params.diameter,
            backendTier = r.backendTier.name,
            backendSpaceSlug = r.backendSpaceSlug,
            backendVersionTag = r.backendVersionTag,
            originalImagePath = r.originalImagePath,
            outlineOverlayPath = r.outlineOverlayPath,
            flowsPath = r.flowsPath,
            maskTiffPath = r.maskTiffPath,
            status = r.status.name,
            startedAt = r.startedAt.toEpochMilli(),
            completedAt = r.completedAt?.toEpochMilli(),
            error = r.error,
        )
    }
}
