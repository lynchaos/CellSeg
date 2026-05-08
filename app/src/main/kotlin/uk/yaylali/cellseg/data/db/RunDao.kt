package uk.yaylali.cellseg.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.yaylali.cellseg.data.db.entity.RunEntity

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: RunEntity)

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun getById(id: String): RunEntity?

    @Query("""
        SELECT * FROM runs
        WHERE sample_id = :sampleId
        ORDER BY started_at DESC
    """)
    fun observeForSample(sampleId: String): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs ORDER BY started_at DESC")
    fun observeAll(): Flow<List<RunEntity>>

    @Query("UPDATE runs SET status = :status, error = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String?)

    @Query("""
        UPDATE runs
        SET outline_overlay_path = :outlinePath,
            flows_path = :flowsPath,
            mask_tiff_path = :maskPath
        WHERE id = :id
    """)
    suspend fun updatePaths(id: String, outlinePath: String?, flowsPath: String?, maskPath: String?)

    @Query("""
        UPDATE runs
        SET status = :status,
            completed_at = :completedAt,
            outline_overlay_path = :outlinePath,
            flows_path = :flowsPath,
            mask_tiff_path = :maskPath,
            error = NULL
        WHERE id = :id
    """)
    suspend fun completeRun(
        id: String,
        status: String,
        completedAt: Long,
        outlinePath: String?,
        flowsPath: String?,
        maskPath: String?,
    )

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun delete(id: String)
}
