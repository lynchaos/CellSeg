package uk.yaylali.cellseg.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import uk.yaylali.cellseg.data.datastore.proto.AppSettingsProto
import java.io.InputStream
import java.io.OutputStream

object AppSettingsSerializer : Serializer<AppSettingsProto> {

    override val defaultValue: AppSettingsProto = AppSettingsProto.newBuilder()
        .setLicenceAcknowledged(false)
        .setDefaultBackendTier(0) // LOCAL_CYTO3
        .setAutoFallbackToLocal(true)
        .setDefaultParams(
            uk.yaylali.cellseg.data.datastore.proto.SegmentationParamsProto.newBuilder()
                .setMaxResize(256)
                .setMaxIter(250)
                .setFlowThreshold(0.4f)
                .setCellprobThreshold(0.0f)
                .build()
        )
        .setOnboardingCompleted(false)
        .build()

    override suspend fun readFrom(input: InputStream): AppSettingsProto {
        try {
            return AppSettingsProto.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read AppSettings proto.", e)
        }
    }

    override suspend fun writeTo(t: AppSettingsProto, output: OutputStream) {
        t.writeTo(output)
    }
}
