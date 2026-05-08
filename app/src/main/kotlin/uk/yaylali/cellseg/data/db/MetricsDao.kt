package uk.yaylali.cellseg.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.yaylali.cellseg.data.db.entity.MetricsEntity

@Dao
interface MetricsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(metrics: MetricsEntity)

    @Query("SELECT * FROM metrics WHERE run_id = :runId")
    suspend fun getForRun(runId: String): MetricsEntity?

    @Query("SELECT * FROM metrics WHERE run_id = :runId")
    fun observeForRun(runId: String): Flow<MetricsEntity?>

    @Query("DELETE FROM metrics WHERE run_id = :runId")
    suspend fun deleteForRun(runId: String)
}
