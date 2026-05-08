package uk.yaylali.cellseg.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation route objects for Navigation 2.8 + kotlinx.serialization. */

@Serializable object Onboarding
@Serializable object LicenceAck
@Serializable object Home
@Serializable object Capture
@Serializable object Gallery

@Serializable data class SampleContext(val sampleId: String? = null, val imageUri: String = "")
@Serializable data class Analyze(val sampleId: String, val imageUri: String)
@Serializable data class RunProgress(val runId: String)
@Serializable data class RunDetail(val runId: String)

@Serializable object History
@Serializable object BatchQueue
@Serializable object Settings
@Serializable object ModelManagement
@Serializable object Attribution
