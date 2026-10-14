package com.partyoke.tv.game

import com.partyoke.tv.data.ActivityEvent
import com.partyoke.tv.data.ActivityLog
import com.partyoke.tv.data.ActivityType
import com.partyoke.tv.model.PartyPhase
import com.partyoke.tv.model.PartyState
import com.partyoke.tv.model.Player
import com.partyoke.tv.model.Song
import com.partyoke.tv.model.Turn
import com.partyoke.tv.model.TurnStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Owns the authoritative party state and all game logic. Thread-safe: all
 * mutations are synchronized because they can arrive both from the TV UI thread
 * and from server (phone) threads.
 *
 * Random song assignment avoids repeats: it draws from a shuffled bag and only
 * refills once every song has been handed out.
 */
class PartyManager(private val activityLog: ActivityLog) {

    private val _state = MutableStateFlow(PartyState())
    val state: StateFlow<PartyState> = _state.asStateFlow()

    private val lock = Any()

    private var catalog: List<Song> = emptyList()
    private var orderCounter = 0

    /** Songs not yet assigned in the current pass through the catalog. */
    private val songBag = ArrayDeque<Song>()

    // --- Setup -----------------------------------------------------------

    fun setCatalog(songs: List<Song>) = synchronized(lock) {
        catalog = songs
        refillBag()
        activityLog.record(
            ActivityEvent(
                ActivityType.CATALOG_LOADED,
                System.currentTimeMillis(),
                detail = "${songs.size} songs",
            )
        )
        _state.value = _state.value.copy(
            songCount = songs.size,
            phase = if (songs.isNotEmpty()) PartyPhase.LOBBY else PartyPhase.SETUP,
        )
    }

    fun setJoinUrl(url: String) = synchronized(lock) {
        _state.value = _state.value.copy(joinUrl = url)
    }

    fun startParty() = synchronized(lock) {
        if (_state.value.phase != PartyPhase.LOBBY) return
        activityLog.record(ActivityType.PARTY_STARTED.event())
        _state.value = _state.value.copy(phase = PartyPhase.PLAYING)
        advance()
    }

    // --- Players ---------------------------------------------------------

    /** Adds a player (from a phone) and immediately queues them a random song. */
    fun addPlayer(name: String): Player = synchronized(lock) {
        val player = Player(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Guest" }.take(24),
            joinedAtMillis = System.currentTimeMillis(),
        )
        activityLog.record(
            ActivityEvent(ActivityType.PLAYER_JOINED, player.joinedAtMillis, player.id, player.name)
        )
        _state.value = _state.value.copy(players = _state.value.players + player)
        enqueueTurn(player)
        player
    }

    fun removePlayer(playerId: String) = synchronized(lock) {
        activityLog.record(
            ActivityEvent(ActivityType.PLAYER_LEFT, System.currentTimeMillis(), playerId)
        )
        _state.value = _state.value.copy(
            players = _state.value.players.filterNot { it.id == playerId },
            queue = _state.value.queue.filterNot { it.playerId == playerId },
        )
    }

    // --- Turn flow -------------------------------------------------------

    /** Marks the current song done and moves to the next queued turn. */
    fun completeCurrent() = synchronized(lock) {
        _state.value.nowPlaying?.let {
            activityLog.record(
                ActivityEvent(ActivityType.TURN_COMPLETED, System.currentTimeMillis(), it.playerId, it.songTitle)
            )
            // Re-queue the singer with a fresh random song so the party continues.
            players(it.playerId)?.let { p -> enqueueTurn(p) }
        }
        advance()
    }

    fun skipCurrent() = synchronized(lock) {
        _state.value.nowPlaying?.let {
            activityLog.record(
                ActivityEvent(ActivityType.TURN_SKIPPED, System.currentTimeMillis(), it.playerId, it.songTitle)
            )
            players(it.playerId)?.let { p -> enqueueTurn(p) }
        }
        advance()
    }

    /** Reshuffle just the current singer's assigned song (the "I don't know this" path). */
    fun reshuffleCurrent() = synchronized(lock) {
        val current = _state.value.nowPlaying ?: return
        val player = players(current.playerId) ?: return
        val song = drawSong() ?: return
        val updated = current.copy(songId = song.id, songTitle = song.title)
        activityLog.record(
            ActivityEvent(ActivityType.SONG_ASSIGNED, System.currentTimeMillis(), player.id, song.title)
        )
        _state.value = _state.value.copy(nowPlaying = updated)
    }

    fun currentSongUri(): String? = synchronized(lock) {
        val current = _state.value.nowPlaying ?: return null
        catalog.firstOrNull { it.id == current.songId }?.uri
    }

    // --- Internal helpers ------------------------------------------------

    private fun advance() {
        val queue = _state.value.queue.toMutableList()
        val next = queue.removeFirstOrNull()
        if (next != null) {
            val playing = next.copy(status = TurnStatus.NOW_PLAYING)
            activityLog.record(
                ActivityEvent(ActivityType.TURN_STARTED, System.currentTimeMillis(), playing.playerId, playing.songTitle)
            )
            _state.value = _state.value.copy(nowPlaying = playing, queue = queue)
        } else {
            _state.value = _state.value.copy(nowPlaying = null, queue = queue)
        }
    }

    private fun enqueueTurn(player: Player) {
        val song = drawSong() ?: return
        val turn = Turn(
            id = UUID.randomUUID().toString(),
            playerId = player.id,
            playerName = player.name,
            songId = song.id,
            songTitle = song.title,
            order = orderCounter++,
        )
        activityLog.record(
            ActivityEvent(ActivityType.SONG_ASSIGNED, System.currentTimeMillis(), player.id, song.title)
        )
        _state.value = _state.value.copy(queue = _state.value.queue + turn)
    }

    /** Draws a random song without repeating until the catalog is exhausted. */
    private fun drawSong(): Song? {
        if (catalog.isEmpty()) return null
        if (songBag.isEmpty()) refillBag()
        return songBag.removeFirstOrNull()
    }

    private fun refillBag() {
        songBag.clear()
        songBag.addAll(catalog.shuffled())
    }

    private fun players(id: String): Player? =
        _state.value.players.firstOrNull { it.id == id }

    private fun ActivityType.event() =
        ActivityEvent(this, System.currentTimeMillis())
}
