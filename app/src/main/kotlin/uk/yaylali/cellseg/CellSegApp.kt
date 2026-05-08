package uk.yaylali.cellseg

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import uk.yaylali.cellseg.util.Logger

@HiltAndroidApp
class CellSegApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.init(BuildConfig.DEBUG)
    }
}
