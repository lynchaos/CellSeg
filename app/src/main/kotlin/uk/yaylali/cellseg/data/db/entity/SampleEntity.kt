package uk.yaylali.cellseg.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import uk.yaylali.cellseg.domain.model.ImagingChannel
import uk.yaylali.cellseg.domain.model.Magnification
import uk.yaylali.cellseg.domain.model.Sample
import java.time.Instant

@Entity(
    tableName = "samples",
    indices = [Index(value = ["sample_label"])]
)
data class SampleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "sample_label") val sampleLabel: String?,
    @ColumnInfo(name = "timepoint") val timepoint: String?,
    @ColumnInfo(name = "passage") val passage: Int?,
    @ColumnInfo(name = "well_id") val wellId: String?,
    @ColumnInfo(name = "channel") val channel: String,
    @ColumnInfo(name = "magnification") val magnification: Int,
    @ColumnInfo(name = "cell_line_label") val cellLineLabel: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,   // epoch ms
) {
    fun toDomain() = Sample(
        id = id,
        sampleLabel = sampleLabel,
        timepoint = timepoint,
        passage = passage,
        wellId = wellId,
        channel = ImagingChannel.valueOf(channel),
        magnification = Magnification.fromFactor(magnification),
        cellLineLabel = cellLineLabel,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

    companion object {
        fun fromDomain(s: Sample) = SampleEntity(
            id = s.id,
            sampleLabel = s.sampleLabel,
            timepoint = s.timepoint,
            passage = s.passage,
            wellId = s.wellId,
            channel = s.channel.name,
            magnification = s.magnification.factor,
            cellLineLabel = s.cellLineLabel,
            createdAt = s.createdAt.toEpochMilli(),
        )
    }
}
