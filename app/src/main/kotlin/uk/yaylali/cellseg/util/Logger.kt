package uk.yaylali.cellseg.util

import timber.log.Timber

/**
 * App-wide logging initialisation and helpers.
 *
 * In DEBUG builds, a [Timber.DebugTree] is planted.
 * In RELEASE builds, no tree is planted and Timber calls are no-ops
 * (Proguard removes Timber.* call sites via `proguard-rules.pro`).
 *
 * SECURITY: never pass HF tokens, image bytes, or full auth URLs to Timber.
 */
object Logger {

    fun init(isDebug: Boolean) {
        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        }
        // No Crashlytics or any third-party sink in any build variant
    }
}
