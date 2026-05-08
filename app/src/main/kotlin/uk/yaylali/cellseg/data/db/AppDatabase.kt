package uk.yaylali.cellseg.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import uk.yaylali.cellseg.data.db.entity.MetricsEntity
import uk.yaylali.cellseg.data.db.entity.RunEntity
import uk.yaylali.cellseg.data.db.entity.SampleEntity

@Database(
    entities = [SampleEntity::class, RunEntity::class, MetricsEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
    abstract fun runDao(): RunDao
    abstract fun metricsDao(): MetricsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE runs ADD COLUMN diameter REAL NOT NULL DEFAULT 30.0")
            }
        }
    }
}
