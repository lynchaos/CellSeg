package uk.yaylali.cellseg.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.yaylali.cellseg.data.db.MetricsDao
import uk.yaylali.cellseg.data.db.RunDao
import uk.yaylali.cellseg.data.db.SampleDao
import uk.yaylali.cellseg.data.db.entity.MetricsEntity
import uk.yaylali.cellseg.data.db.entity.RunEntity
import uk.yaylali.cellseg.data.db.entity.SampleEntity
import uk.yaylali.cellseg.domain.model.CellMetrics
import uk.yaylali.cellseg.domain.model.RunStatus
import uk.yaylali.cellseg.domain.model.Sample
import uk.yaylali.cellseg.domain.model.SegmentationRun
import uk.yaylali.cellseg.domain.repo.SegmentationRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SegmentationRepositoryImpl @Inject constructor(
    private val sampleDao: SampleDao,
    private val runDao: RunDao,
    private val metricsDao: MetricsDao,
) : SegmentationRepository {

    override suspend fun insertSample(sample: Sample) =
        sampleDao.insert(SampleEntity.fromDomain(sample))

    override suspend fun updateSample(sample: Sample) =
        sampleDao.insert(SampleEntity.fromDomain(sample)) // Room replace-on-conflict

    override suspend fun getSample(id: String): Sample? =
        sampleDao.getById(id)?.toDomain()

    override fun observeSamples(): Flow<List<Sample>> =
        sampleDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun insertRun(run: SegmentationRun) =
        runDao.insert(RunEntity.fromDomain(run))

    override suspend fun updateRunStatus(id: String, status: RunStatus, error: String?) =
        runDao.updateStatus(id, status.name, error)

    override suspend fun updateRunPaths(
        id: String,
        outlineOverlayPath: String?,
        flowsPath: String?,
        maskTiffPath: String?,
    ) = runDao.updatePaths(id, outlineOverlayPath, flowsPath, maskTiffPath)

    override suspend fun completeRun(run: SegmentationRun) {
        runDao.completeRun(
            id = run.id,
            status = run.status.name,
            completedAt = (run.completedAt ?: Instant.now()).toEpochMilli(),
            outlinePath = run.outlineOverlayPath,
            flowsPath = run.flowsPath,
            maskPath = run.maskTiffPath,
        )
    }

    override suspend fun getRun(id: String): SegmentationRun? =
        runDao.getById(id)?.toDomain()

    override fun observeRunsForSample(sampleId: String): Flow<List<SegmentationRun>> =
        runDao.observeForSample(sampleId).map { list -> list.map { it.toDomain() } }

    override fun observeAllRuns(): Flow<List<SegmentationRun>> =
        runDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun deleteRun(id: String) = runDao.delete(id)

    override suspend fun insertOrReplaceMetrics(metrics: CellMetrics) =
        metricsDao.insertOrReplace(MetricsEntity.fromDomain(metrics))

    override suspend fun getMetrics(runId: String): CellMetrics? =
        metricsDao.getForRun(runId)?.toDomain()

    override fun observeMetrics(runId: String): Flow<CellMetrics?> =
        metricsDao.observeForRun(runId).map { it?.toDomain() }
}
