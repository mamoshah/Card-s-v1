package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ResultScreen
import com.example.ui.screens.TargetSelectionScreen
import com.example.ui.screens.TrackingProgressScreen
import com.example.ui.screens.UploadScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppScreen
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
                    CardTrackerApp()
                }
            }
        }
    }
}

@Composable
fun CardTrackerApp(
    viewModel: CardTrackerViewModel = viewModel()
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
                onDismissError = { viewModel.dismissError() }
            )
        }

        AppScreen.SELECT_TARGET -> {
            BackHandler {
                viewModel.resetToUpload()
            }
            TargetSelectionScreen(
                videoTitle = activeTitle,
                selectionState = selectionState,
                onFrameTapped = { x, y -> viewModel.onFrameTapped(x, y) },
                onTargetSizeChanged = { viewModel.setTargetSizeScale(it) },
                onConfirmTarget = { viewModel.startAnalysis() },
                onBack = { viewModel.resetToUpload() }
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
