package uk.yaylali.cellseg.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import uk.yaylali.cellseg.ui.screen.analyze.AnalyzeScreen
import uk.yaylali.cellseg.ui.screen.attribution.AttributionScreen
import uk.yaylali.cellseg.ui.screen.batch.BatchScreen
import uk.yaylali.cellseg.ui.screen.capture.CaptureScreen
import uk.yaylali.cellseg.ui.screen.gallery.GalleryScreen
import uk.yaylali.cellseg.ui.screen.history.HistoryScreen
import uk.yaylali.cellseg.ui.screen.home.HomeScreen
import uk.yaylali.cellseg.ui.screen.licence.LicenceAckScreen
import uk.yaylali.cellseg.ui.screen.model.ModelManagementScreen
import uk.yaylali.cellseg.ui.screen.onboarding.OnboardingScreen
import uk.yaylali.cellseg.ui.screen.run.RunDetailScreen
import uk.yaylali.cellseg.ui.screen.run.RunProgressScreen
import uk.yaylali.cellseg.ui.screen.sample.SampleContextScreen
import uk.yaylali.cellseg.ui.screen.settings.SettingsScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: Any,
) {
    NavHost(navController = navController, startDestination = startDestination) {

        composable<Onboarding> {
            OnboardingScreen(
                onContinue = { navController.navigate(LicenceAck) }
            )
        }

        composable<LicenceAck> {
            LicenceAckScreen(
                onAcknowledged = {
                    navController.navigate(Home) {
                        popUpTo<Onboarding> { inclusive = true }
                    }
                }
            )
        }

        composable<Home> {
            HomeScreen(
                onCapture = { navController.navigate(Capture) },
                onGallery = { navController.navigate(Gallery) },
                onHistory = { navController.navigate(History) },
                onSettings = { navController.navigate(Settings) },
                onBatch = { navController.navigate(BatchQueue) },
            )
        }

        composable<Capture> {
            CaptureScreen(
                onImageCaptured = { sampleId, imageUri ->
                    navController.navigate(SampleContext(sampleId, imageUri))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Gallery> {
            GalleryScreen(
                onImagePicked = { sampleId, imageUri ->
                    navController.navigate(SampleContext(sampleId, imageUri))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<SampleContext> { backStack ->
            val route = backStack.toRoute<SampleContext>()
            SampleContextScreen(
                sampleId = route.sampleId,
                imageUri = route.imageUri,
                onContinue = { sampleId, imageUri ->
                    navController.navigate(Analyze(sampleId, imageUri))
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Analyze> { backStack ->
            val route = backStack.toRoute<Analyze>()
            AnalyzeScreen(
                sampleId = route.sampleId,
                imageUri = route.imageUri,
                onRunStarted = { runId ->
                    navController.navigate(RunProgress(runId)) {
                        popUpTo<Analyze> { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<RunProgress> { backStack ->
            val route = backStack.toRoute<RunProgress>()
            RunProgressScreen(
                runId = route.runId,
                onCompleted = { runId ->
                    navController.navigate(RunDetail(runId)) {
                        popUpTo<RunProgress> { inclusive = true }
                    }
                },
                onError = { navController.popBackStack() },
            )
        }

        composable<RunDetail> { backStack ->
            val route = backStack.toRoute<RunDetail>()
            RunDetailScreen(
                runId = route.runId,
                onBack = { navController.navigate(History) { popUpTo<Home>() } },
                onReRun = { sampleId, imageUri ->
                    navController.navigate(Analyze(sampleId, imageUri))
                },
            )
        }

        composable<History> {
            HistoryScreen(
                onRunSelected = { runId -> navController.navigate(RunDetail(runId)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Settings> {
            SettingsScreen(
                onModelManagement = { navController.navigate(ModelManagement) },
                onAttribution = { navController.navigate(Attribution) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<ModelManagement> {
            ModelManagementScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable<Attribution> {
            AttributionScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable<BatchQueue> {
            BatchScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
