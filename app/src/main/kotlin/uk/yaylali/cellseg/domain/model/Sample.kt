package uk.yaylali.cellseg.domain.model

import java.time.Instant

data class Sample(
    val id: String,
    val sampleLabel: String?,
    val timepoint: String?,
    val passage: Int?,
    val wellId: String?,
    val channel: ImagingChannel,
    val magnification: Magnification,
    val cellLineLabel: String?,
    val createdAt: Instant,
)

enum class ImagingChannel {
    PHASE_CONTRAST,
    BRIGHTFIELD,
    FLUORESCENCE_GREEN,
    FLUORESCENCE_RED,
    OTHER;

    val displayName: String get() = when (this) {
        PHASE_CONTRAST     -> "Phase"
        BRIGHTFIELD        -> "Brightfield"
        FLUORESCENCE_GREEN -> "GFP"
        FLUORESCENCE_RED   -> "RFP"
        OTHER              -> "Other"
    }
}

enum class Magnification(val factor: Int) {
    X4(4),
    X10(10),
    X20(20),
    X40(40),
    OTHER(0);

    val label: String get() = if (this == OTHER) "Other" else "${factor}×"

    companion object {
        fun fromFactor(factor: Int): Magnification =
            entries.firstOrNull { it.factor == factor } ?: OTHER
    }
}
