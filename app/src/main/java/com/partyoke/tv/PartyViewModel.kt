package com.partyoke.tv

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.partyoke.tv.data.InMemoryActivityLog
import com.partyoke.tv.data.UsbSongScanner
import com.partyoke.tv.game.PartyManager
import com.partyoke.tv.net.NetworkUtils
import com.partyoke.tv.net.PartyServer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Bridges the Android framework (SAF, lifecycle) with the pure-Kotlin game core
 * ([PartyManager]) and the embedded [PartyServer]. Exposes a single observable
 * [state] for the Compose UI.
 */
class PartyViewModel(app: Application) : AndroidViewModel(app) {

    private val activityLog = InMemoryActivityLog()
    private val manager = PartyManager(activityLog)
    private val scanner = UsbSongScanner(app)
    private val server = PartyServer(manager, PORT)

    val state: StateFlow<com.partyoke.tv.model.PartyState> = manager.state

    private var serverStarted = false

    /** Persist read permission + scan the picked USB folder for videos. */
    fun onFolderPicked(treeUri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(treeUri, flags)
        } catch (_: SecurityException) {
            // Some providers only grant read; that's fine for playback.
        }
        viewModelScope.launch {
            val songs = scanner.scan(treeUri)
            manager.setCatalog(songs)
            ensureServer()
        }
    }

    /** Scan for "PartyOkeSongs" folder across storage/USB. */
    fun onFallbackFolderPicked() {
        viewModelScope.launch {
            android.util.Log.d("PartyViewModel", "Scanning for 'PartyOkeSongs' folder on storage/USB...")
            val songs = scanner.scanPartyOkeSongs()
            android.util.Log.d("PartyViewModel", "Loaded ${songs.size} songs into catalog from 'PartyOkeSongs'.")
            manager.setCatalog(songs)
            ensureServer()
        }
    }

    private fun ensureServer() {
        if (serverStarted) return
        val ip = NetworkUtils.getLocalIpAddress()
        if (ip != null) {
            manager.setJoinUrl("http://$ip:$PORT")
            server.start()
            serverStarted = true
        } else {
            manager.setJoinUrl("No Wi-Fi connection detected")
        }
    }

    fun startParty() = manager.startParty()
    fun completeCurrent() = manager.completeCurrent()
    fun skipCurrent() = manager.skipCurrent()
    fun reshuffleCurrent() = manager.reshuffleCurrent()
    fun currentSongUri(): String? = manager.currentSongUri()

    override fun onCleared() {
        super.onCleared()
        server.stop()
    }

    private companion object {
        const val PORT = 8888
    }
}
