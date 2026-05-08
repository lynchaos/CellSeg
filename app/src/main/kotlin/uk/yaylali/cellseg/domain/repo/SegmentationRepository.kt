package uk.yaylali.cellseg.domain.repo

import kotlinx.coroutines.flow.Flow
import uk.yaylali.cellseg.domain.model.CellMetrics
import uk.yaylali.cellseg.domain.model.Sample
import uk.yaylali.cellseg.domain.model.SegmentationRun
import uk.yaylali.cellseg.domain.model.RunStatus

interface SegmentationRepository {

    // ── Samples ──────────────────────────────────────────────────────────────
    suspend fun insertSample(sample: Sample)
    suspend fun updateSample(sample: Sample)
    suspend fun getSample(id: String): Sample?
    fun observeSamples(): Flow<List<Sample>>

    // ── Runs ─────────────────────────────────────────────────────────────────
    suspend fun insertRun(run: SegmentationRun)
    suspend fun updateRunStatus(id: String, status: RunStatus, error: String? = null)
    suspend fun updateRunPaths(
        id: String,
        outlineOverlayPath: String?,
        flowsPath: String?,
        maskTiffPath: String?,
    )
    suspend fun completeRun(run: SegmentationRun)
    suspend fun getRun(id: String): SegmentationRun?
    fun observeRunsForSample(sampleId: String): Flow<List<SegmentationRun>>
    fun observeAllRuns(): Flow<List<SegmentationRun>>
    suspend fun deleteRun(id: String)

    // ── Metrics ──────────────────────────────────────────────────────────────
    suspend fun insertOrReplaceMetrics(metrics: CellMetrics)
    suspend fun getMetrics(runId: String): CellMetrics?
    fun observeMetrics(runId: String): Flow<CellMetrics?>
}
