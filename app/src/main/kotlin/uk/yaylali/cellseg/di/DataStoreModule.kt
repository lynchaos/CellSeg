package uk.yaylali.cellseg.di

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.yaylali.cellseg.data.datastore.AppSettingsSerializer
import uk.yaylali.cellseg.data.datastore.SettingsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    /**
     * Build [SettingsRepository] here rather than via @Inject constructor so that
     * Hilt's KSP processor never sees [AppSettingsProto] (a protobuf-generated
     * Java class) in any @Provides method signature — which avoids the
     * proto-generated-class / KSP ordering problem.
     */
    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
    ): SettingsRepository {
        val dataStore = DataStoreFactory.create(
            serializer = AppSettingsSerializer,
            produceFile = { context.dataStoreFile("app_settings.pb") },
        )
        return SettingsRepository(dataStore)
    }
}
