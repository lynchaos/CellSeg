package uk.yaylali.cellseg.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.yaylali.cellseg.data.db.AppDatabase
import uk.yaylali.cellseg.data.db.MetricsDao
import uk.yaylali.cellseg.data.db.RunDao
import uk.yaylali.cellseg.data.db.SampleDao
import uk.yaylali.cellseg.data.repo.SegmentationRepositoryImpl
import uk.yaylali.cellseg.domain.repo.SegmentationRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "cellseg.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides fun provideSampleDao(db: AppDatabase): SampleDao = db.sampleDao()
    @Provides fun provideRunDao(db: AppDatabase): RunDao = db.runDao()
    @Provides fun provideMetricsDao(db: AppDatabase): MetricsDao = db.metricsDao()

    @Provides
    @Singleton
    fun provideSegmentationRepository(impl: SegmentationRepositoryImpl): SegmentationRepository = impl
}
