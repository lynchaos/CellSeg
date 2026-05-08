package uk.yaylali.cellseg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import uk.yaylali.cellseg.data.datastore.SettingsRepository
import uk.yaylali.cellseg.navigation.AppNavGraph
import uk.yaylali.cellseg.navigation.Home
import uk.yaylali.cellseg.navigation.Onboarding
import uk.yaylali.cellseg.ui.theme.CellSegTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Determine start destination synchronously from DataStore on first launch only
        val licenceAcknowledged = runBlocking {
            settingsRepository.licenceAcknowledged.first()
        }
        val onboardingCompleted = runBlocking {
            settingsRepository.onboardingCompleted.first()
        }

        val startDestination: Any = when {
            !licenceAcknowledged || !onboardingCompleted -> Onboarding
            else -> Home
        }

        setContent {
            CellSegTheme {
                val navController = rememberNavController()
                AppNavGraph(
                    navController = navController,
                    startDestination = startDestination,
                )
            }
        }
    }
}
