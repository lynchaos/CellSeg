package uk.yaylali.cellseg.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.yaylali.cellseg.data.ml.CellposePreprocessor
import uk.yaylali.cellseg.data.ml.LocalCellposeBackend
import uk.yaylali.cellseg.data.ml.ModelDownloader
import uk.yaylali.cellseg.data.ml.OnnxRuntimeWrapper
import uk.yaylali.cellseg.data.remote.gradio.GradioClient
import uk.yaylali.cellseg.data.remote.gradio.RemoteHfGradioBackend
import uk.yaylali.cellseg.data.datastore.SettingsRepository
import uk.yaylali.cellseg.data.filestore.FileStore
import uk.yaylali.cellseg.data.local.TokenStore
import uk.yaylali.cellseg.domain.backend.SegmentationBackend
import uk.yaylali.cellseg.domain.model.BackendTier
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MlModule {

    @Provides
    @Singleton
    @Named("backend_local")
    fun provideLocalBackend(
        modelDownloader: ModelDownloader,
        onnx: OnnxRuntimeWrapper,
        fileStore: FileStore,
    ): SegmentationBackend = LocalCellposeBackend(modelDownloader, onnx, fileStore)

    @Provides
    @Singleton
    @Named("backend_remote_public")
    fun provideRemotePublicBackend(
        gradioClient: GradioClient,
        fileStore: FileStore,
        tokenStore: TokenStore,
        settingsRepository: SettingsRepository,
    ): SegmentationBackend = RemoteHfGradioBackend(
        gradioClient = gradioClient,
        fileStore = fileStore,
        tokenStore = tokenStore,
        settingsRepository = settingsRepository,
        tier = BackendTier.REMOTE_PUBLIC_HF,
        spaceSlug = BackendTier.HF_PUBLIC_SPACE_SLUG,
    )
}
