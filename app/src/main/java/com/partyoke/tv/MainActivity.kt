package com.partyoke.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.partyoke.tv.model.PartyPhase
import com.partyoke.tv.ui.LobbyScreen
import com.partyoke.tv.ui.PlayingScreen
import com.partyoke.tv.ui.SetupScreen
import com.partyoke.tv.ui.theme.PartyBg
import com.partyoke.tv.ui.theme.PartyOkeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PartyViewModel by viewModels()

    // Storage Access Framework folder picker for the USB karaoke folder.
    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.onFolderPicked(it) }
    }

    private fun isEmulator(): Boolean {
        return android.os.Build.FINGERPRINT.startsWith("generic") ||
            android.os.Build.FINGERPRINT.startsWith("unknown") ||
            android.os.Build.MODEL.contains("google_sdk") ||
            android.os.Build.MODEL.contains("Emulator") ||
            android.os.Build.MODEL.contains("Android SDK") ||
            android.os.Build.BOARD == "unknown" ||
            android.os.Build.DEVICE.startsWith("generic") ||
            android.os.Build.PRODUCT.contains("sdk")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PartyOkeTheme {
                Box(modifier = Modifier.fillMaxSize().background(PartyBg)) {
                    val state by viewModel.state.collectAsState()

                    when (state.phase) {
                        PartyPhase.SETUP -> SetupScreen(
                            onScanStorage = {
                                viewModel.onFallbackFolderPicked()
                            },
                            onPickFolder = {
                                if (isEmulator()) {
                                    viewModel.onFallbackFolderPicked()
                                } else {
                                    try {
                                        folderPicker.launch(null)
                                    } catch (e: Exception) {
                                        android.util.Log.w("MainActivity", "Folder picker launch failed, falling back to storage scan", e)
                                        viewModel.onFallbackFolderPicked()
                                    }
                                }
                            }
                        )

                        PartyPhase.LOBBY -> LobbyScreen(
                            state = state,
                            onStart = { viewModel.startParty() },
                        )

                        PartyPhase.PLAYING -> PlayingScreen(
                            state = state,
                            // Re-derived whenever nowPlaying changes so ExoPlayer
                            // reloads the correct video for each turn.
                            currentUri = viewModel.currentSongUri(),
                            key = state.nowPlaying?.id,
                            onSongEnded = { viewModel.completeCurrent() },
                            onSkip = { viewModel.skipCurrent() },
                        )
                    }
                }
            }
        }
    }
}
