package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ResultScreen
import com.example.ui.screens.TargetSelectionScreen
import com.example.ui.screens.TrackingProgressScreen
import com.example.ui.screens.UploadScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.AuthViewModel
import com.example.viewmodel.CardTrackerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    CardTrackerRoot()
                }
            }
        }
    }
}

/**
 * Root Composable protecting all application screens behind the Room Database Login Screen.
 */
@Composable
fun CardTrackerRoot(
    authViewModel: AuthViewModel = viewModel(),
    trackerViewModel: CardTrackerViewModel = viewModel()
) {
    val isAuthenticated by authViewModel.isAuthenticated.collectAsState()
    val authUiState by authViewModel.uiState.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    Crossfade(targetState = isAuthenticated, label = "auth_screen_gate") { authenticated ->
        if (!authenticated) {
            LoginScreen(
                uiState = authUiState,
                onHostNameChange = { authViewModel.onHostNameChanged(it) },
                onPasswordChange = { authViewModel.onPasswordChanged(it) },
                onTogglePasswordVisibility = { authViewModel.togglePasswordVisibility() },
                onSubmitHostName = { authViewModel.submitHostName() },
                onSubmitPassword = { authViewModel.submitPassword() },
                onClearMessages = { authViewModel.clearMessages() }
            )
        } else {
            // Main protected application
            CardTrackerApp(
                viewModel = trackerViewModel,
                currentUser = currentUser,
                onLogout = {
                    trackerViewModel.resetToUpload()
                    authViewModel.logout()
                }
            )
        }
    }
}

@Composable
fun CardTrackerApp(
    viewModel: CardTrackerViewModel = viewModel(),
    currentUser: com.example.data.local.UserEntity? = null,
    onLogout: () -> Unit = {}
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val activeTitle by viewModel.activeVideoTitle.collectAsState()
    val selectionState by viewModel.selectionState.collectAsState()
    val progressState by viewModel.progressState.collectAsState()
    val trackingResult by viewModel.trackingResult.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isImportingVideo by viewModel.isImportingVideo.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    when (currentScreen) {
        AppScreen.UPLOAD -> {
            UploadScreen(
                isImportingVideo = isImportingVideo,
                onBenchmarkSelected = { viewModel.selectBenchmarkVideo(it) },
                onVideoUploaded = { uri, name -> viewModel.selectUploadedVideo(uri, name) },
                errorMessage = errorMessage,
                onDismissError = { viewModel.dismissError() },
                currentUser = currentUser,
                onLogout = onLogout
            )
        }

        AppScreen.SELECT_TARGET -> {
            BackHandler {
                viewModel.resetToUpload()
            }
            TargetSelectionScreen(
                videoTitle = activeTitle,
                selectionState = selectionState,
                onCardSelected = { viewModel.selectCardPosition(it) },
                onConfirmTarget = { viewModel.startAnalysis() },
                onBack = { viewModel.resetToUpload() },
                onFrameTapped = { x, y -> viewModel.onFrameTapped(x, y) }
            )
        }

        AppScreen.TRACKING_PROGRESS -> {
            TrackingProgressScreen(
                videoTitle = activeTitle,
                progressState = progressState
            )
        }

        AppScreen.RESULT_VIEW -> {
            BackHandler {
                viewModel.resetToUpload()
            }
            val result = trackingResult
            if (result != null) {
                ResultScreen(
                    result = result,
                    playbackState = playbackState,
                    onSeek = { viewModel.seekToFrame(it) },
                    onTogglePlay = { viewModel.togglePlayback() },
                    onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                    onTrailEffectChange = { viewModel.setTrailEffect(it) },
                    onTrailLengthChange = { viewModel.setTrailLength(it) },
                    onToggleBoundingBox = { viewModel.toggleBoundingBox() },
                    onToggleArrow = { viewModel.toggleArrow() },
                    onToggleTelemetryHud = { viewModel.toggleTelemetryHud() },
                    onRepickTarget = { viewModel.repickTarget() },
                    onRerun = { viewModel.startAnalysis() },
                    onResetToUpload = { viewModel.resetToUpload() }
                )
            } else {
                viewModel.resetToUpload()
            }
        }
    }
}
