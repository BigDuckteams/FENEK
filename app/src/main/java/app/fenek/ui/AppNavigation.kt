package app.fenek.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.fenek.AppRequest
import app.fenek.IncomingAudio
import app.fenek.ui.home.HomeScreen
import app.fenek.ui.models.ModelsScreen
import app.fenek.ui.record.RecordScreen
import app.fenek.ui.settings.SettingsScreen
import app.fenek.ui.transcript.TranscriptScreen
import kotlinx.coroutines.flow.MutableStateFlow

object Routes {
    const val HOME = "home"
    const val MODELS = "models"
    const val SETTINGS = "settings"
    const val RECORD = "record?autoStart={autoStart}"
    const val TRANSCRIPT = "transcript/{id}"
    fun transcript(id: String) = "transcript/$id"
    fun record(autoStart: Boolean = false) = "record?autoStart=$autoStart"
}

@Composable
fun FenekNavHost(requests: MutableStateFlow<AppRequest?>) {
    val nav = rememberNavController()
    val request by requests.collectAsStateWithLifecycle()
    LaunchedEffect(request) {
        when (val r = request) {
            is AppRequest.OpenTranscript -> {
                requests.value = null
                nav.navigate(Routes.transcript(r.id)) { launchSingleTop = true }
            }
            is AppRequest.Record -> {
                requests.value = null
                nav.navigate(Routes.record(r.autoStart)) { launchSingleTop = true }
            }
            // Handled by the home screen.
            is AppRequest.Transcribe, AppRequest.PickFile -> nav.backToHome()
            null -> Unit
        }
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                requests = requests,
                onOpenTranscript = { nav.navigate(Routes.transcript(it)) },
                onOpenRecorder = { nav.navigate(Routes.record()) { launchSingleTop = true } },
                onOpenModels = { nav.navigate(Routes.MODELS) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            Routes.RECORD,
            arguments = listOf(
                navArgument("autoStart") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            RecordScreen(
                autoStart = entry.arguments?.getBoolean("autoStart") == true,
                onBack = { nav.popBackStack() },
                onFinished = { recording ->
                    requests.value = AppRequest.Transcribe(
                        IncomingAudio(Uri.fromFile(recording.file), recording.displayName, owned = true),
                    )
                },
            )
        }
        composable(
            Routes.TRANSCRIPT,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            TranscriptScreen(
                id = entry.arguments?.getString("id").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.MODELS) {
            ModelsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenModels = { nav.navigate(Routes.MODELS) },
            )
        }
    }
}

private fun NavHostController.backToHome() {
    if (currentDestination?.route != Routes.HOME) popBackStack(Routes.HOME, inclusive = false)
}
