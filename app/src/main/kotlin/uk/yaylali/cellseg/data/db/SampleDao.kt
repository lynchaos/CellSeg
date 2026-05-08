package uk.yaylali.cellseg.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import uk.yaylali.cellseg.data.db.entity.SampleEntity

@Dao
interface SampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: SampleEntity)

    @Query("SELECT * FROM samples WHERE id = :id")
    suspend fun getById(id: String): SampleEntity?

    @Query("SELECT * FROM samples ORDER BY created_at DESC")
    fun observeAll(): Flow<List<SampleEntity>>

    @Query("DELETE FROM samples WHERE id = :id")
    suspend fun delete(id: String)
}
