package uk.yaylali.cellseg.data.datastore

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import uk.yaylali.cellseg.data.datastore.proto.AppSettingsProto
import uk.yaylali.cellseg.data.datastore.proto.CalibrationEntryProto
import uk.yaylali.cellseg.data.datastore.proto.NamedPresetProto
import uk.yaylali.cellseg.data.datastore.proto.SegmentationParamsProto
import uk.yaylali.cellseg.domain.model.BackendTier
import uk.yaylali.cellseg.domain.model.CalibrationEntry
import uk.yaylali.cellseg.domain.model.ModelMetadata
import uk.yaylali.cellseg.domain.model.NamedPreset
import uk.yaylali.cellseg.domain.model.SegmentationParams
import java.io.IOException
import java.time.Instant
import javax.inject.Singleton

@Singleton
class SettingsRepository(
    private val dataStore: DataStore<AppSettingsProto>,
) {

    val settings: Flow<AppSettingsProto> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(AppSettingsSerializer.defaultValue)
            else throw exception
        }

    val licenceAcknowledged: Flow<Boolean> = settings.map { it.licenceAcknowledged }
    val onboardingCompleted: Flow<Boolean> = settings.map { it.onboardingCompleted }
    val defaultBackendTier: Flow<BackendTier> = settings.map {
        BackendTier.entries.getOrElse(it.defaultBackendTier) { BackendTier.LOCAL_CYTO3 }
    }
    val autoFallbackToLocal: Flow<Boolean> = settings.map { it.autoFallbackToLocal }
    val customSpaceSlug: Flow<String> = settings.map { it.customSpaceSlug }
    val defaultParams: Flow<SegmentationParams> = settings.map { it.defaultParams.toDomain() }
    val cyto3ModelMetadata: Flow<ModelMetadata?> = settings.map {
        if (it.hasCyto3ModelMetadata()) it.cyto3ModelMetadata.toDomain() else null
    }
    val calibrations: Flow<List<CalibrationEntry>> = settings.map { proto ->
        proto.calibrationsList.map { it.toDomain() }
    }
    val presets: Flow<List<NamedPreset>> = settings.map { proto ->
        proto.presetsList.map { it.toDomain() }
    }

    suspend fun acknowledgeLicence() = dataStore.updateData { it.toBuilder().setLicenceAcknowledged(true).build() }
    suspend fun completeOnboarding() = dataStore.updateData { it.toBuilder().setOnboardingCompleted(true).build() }

    suspend fun setDefaultBackendTier(tier: BackendTier) =
        dataStore.updateData { it.toBuilder().setDefaultBackendTier(tier.ordinal).build() }

    suspend fun setAutoFallbackToLocal(enabled: Boolean) =
        dataStore.updateData { it.toBuilder().setAutoFallbackToLocal(enabled).build() }

    suspend fun setCustomSpaceSlug(slug: String) =
        dataStore.updateData { it.toBuilder().setCustomSpaceSlug(slug).build() }

    suspend fun setDefaultParams(params: SegmentationParams) =
        dataStore.updateData { it.toBuilder().setDefaultParams(params.toProto()).build() }

    suspend fun setCyto3ModelMetadata(metadata: ModelMetadata) =
        dataStore.updateData { it.toBuilder().setCyto3ModelMetadata(metadata.toProto()).build() }

    suspend fun clearCyto3ModelMetadata() =
        dataStore.updateData { it.toBuilder().clearCyto3ModelMetadata().build() }

    suspend fun upsertCalibration(entry: CalibrationEntry) {
        dataStore.updateData { proto ->
            val existing = proto.calibrationsList.indexOfFirst {
                it.magnificationLabel == entry.magnificationLabel
            }
            val updated = proto.toBuilder().clearCalibrations()
            proto.calibrationsList.forEachIndexed { i, e ->
                if (i == existing) updated.addCalibrations(entry.toProto())
                else updated.addCalibrations(e)
            }
            if (existing < 0) updated.addCalibrations(entry.toProto())
            updated.build()
        }
    }

    suspend fun setCalibrations(entries: List<CalibrationEntry>) {
        dataStore.updateData { proto ->
            proto.toBuilder()
                .clearCalibrations()
                .addAllCalibrations(entries.map { it.toProto() })
                .build()
        }
    }

    suspend fun upsertPreset(preset: NamedPreset) {
        dataStore.updateData { proto ->
            val existing = proto.presetsList.indexOfFirst { it.name == preset.name }
            val updated = proto.toBuilder().clearPresets()
            proto.presetsList.forEachIndexed { i, p ->
                if (i == existing) updated.addPresets(preset.toProto())
                else updated.addPresets(p)
            }
            if (existing < 0) updated.addPresets(preset.toProto())
            updated.build()
        }
    }

    suspend fun deletePreset(name: String) {
        dataStore.updateData { proto ->
            proto.toBuilder()
                .clearPresets()
                .addAllPresets(proto.presetsList.filter { it.name != name })
                .build()
        }
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    private fun SegmentationParamsProto.toDomain() = SegmentationParams(
        maxResize = maxResize.takeIf { it > 0 } ?: 1000,
        maxIter = maxIter.takeIf { it > 0 } ?: 250,
        flowThreshold = flowThreshold.takeIf { it > 0f } ?: 0.4f,
        cellProbThreshold = cellprobThreshold,
        diameter = diameter.takeIf { it > 0.0 } ?: 30.0,
    )

    private fun SegmentationParams.toProto(): SegmentationParamsProto =
        SegmentationParamsProto.newBuilder()
            .setMaxResize(maxResize)
            .setMaxIter(maxIter)
            .setFlowThreshold(flowThreshold)
            .setCellprobThreshold(cellProbThreshold)
            .setDiameter(diameter)
            .build()

    private fun uk.yaylali.cellseg.data.datastore.proto.ModelMetadataProto.toDomain() = ModelMetadata(
        modelId = modelId,
        version = version,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        downloadedAt = Instant.ofEpochMilli(downloadedAtEpochMs),
        sourceUrl = sourceUrl,
        localPath = localPath,
    )

    private fun ModelMetadata.toProto() =
        uk.yaylali.cellseg.data.datastore.proto.ModelMetadataProto.newBuilder()
            .setModelId(modelId)
            .setVersion(version)
            .setSizeBytes(sizeBytes)
            .setSha256(sha256)
            .setDownloadedAtEpochMs(downloadedAt.toEpochMilli())
            .setSourceUrl(sourceUrl)
            .setLocalPath(localPath)
            .build()

    private fun CalibrationEntryProto.toDomain() = CalibrationEntry(
        magnificationLabel = magnificationLabel,
        pxPerMicron = pxPerMicron,
        fovWidthMm = fovWidthMm,
        fovHeightMm = fovHeightMm,
    )

    private fun CalibrationEntry.toProto(): CalibrationEntryProto =
        CalibrationEntryProto.newBuilder()
            .setMagnificationLabel(magnificationLabel)
            .setPxPerMicron(pxPerMicron)
            .setFovWidthMm(fovWidthMm)
            .setFovHeightMm(fovHeightMm)
            .build()

    private fun NamedPresetProto.toDomain() = NamedPreset(name = name, params = params.toDomain())

    private fun NamedPreset.toProto(): NamedPresetProto =
        NamedPresetProto.newBuilder().setName(name).setParams(params.toProto()).build()
}
