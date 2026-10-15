package com.partyoke.tv.game

import com.partyoke.tv.data.ActivityEvent
import com.partyoke.tv.data.ActivityLog
import com.partyoke.tv.data.ActivityType
import com.partyoke.tv.model.PartyPhase
import com.partyoke.tv.model.PartyState
import com.partyoke.tv.model.Player
import com.partyoke.tv.model.ScoreEntry
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

    /** playerId -> total applause received. */
    private val applauseByPlayer = mutableMapOf<String, Int>()

    /**
     * Records which guest has already applauded which turn, so a single phone
     * can only applaud a given singer's turn once (anti-spam).
     * Key = turnId, value = set of applauding playerIds.
     */
    private val applaudedTurns = mutableMapOf<String, MutableSet<String>>()

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
        // The first player to join becomes the admin.
        if (_state.value.adminPlayerId == null) {
            promoteToAdmin(player.id)
        }
        enqueueTurn(player)
        player
    }

    fun removePlayer(playerId: String) = synchronized(lock) {
        if (_state.value.adminPlayerId == playerId) return
        activityLog.record(
            ActivityEvent(ActivityType.PLAYER_LEFT, System.currentTimeMillis(), playerId)
        )
        val remaining = _state.value.players.filterNot { it.id == playerId }
        _state.value = _state.value.copy(
            players = remaining,
            queue = _state.value.queue.filterNot { it.playerId == playerId },
        )
        // If the admin left, auto-promote the next (earliest-joined) player.
        if (_state.value.adminPlayerId == playerId) {
            val next = remaining.minByOrNull { it.joinedAtMillis }
            if (next != null) promoteToAdmin(next.id)
            else _state.value = _state.value.copy(adminPlayerId = null)
        }
    }

    fun removePlayer(playerId: String, byPlayerId: String) = synchronized(lock) {
        if (!isAdmin(byPlayerId)) return
        removePlayer(playerId)
    }

    // --- Admin: party branding ------------------------------------------

    fun renameParty(name: String, byPlayerId: String) = synchronized(lock) {
        if (!isAdmin(byPlayerId)) return
        val clean = name.trim().take(32).ifEmpty { return }
        activityLog.record(
            ActivityEvent(ActivityType.PARTY_RENAMED, System.currentTimeMillis(), byPlayerId, clean)
        )
        _state.value = _state.value.copy(partyName = clean)
    }

    fun setAccentColor(hex: String, byPlayerId: String) = synchronized(lock) {
        if (!isAdmin(byPlayerId)) return
        val clean = hex.trim()
        if (!clean.matches(Regex("^#[0-9a-fA-F]{6}$"))) return
        activityLog.record(
            ActivityEvent(ActivityType.ACCENT_CHANGED, System.currentTimeMillis(), byPlayerId, clean)
        )
        _state.value = _state.value.copy(accentColor = clean)
    }

    // --- Admin: queue management ----------------------------------------

    /** Moves a queued turn to a new position (clamped to valid range). */
    fun moveTurn(turnId: String, newIndex: Int, byPlayerId: String) = synchronized(lock) {
        if (!isAdmin(byPlayerId)) return
        val queue = _state.value.queue.toMutableList()
        val from = queue.indexOfFirst { it.id == turnId }
        if (from < 0) return
        val target = newIndex.coerceIn(0, queue.size - 1)
        val item = queue.removeAt(from)
        queue.add(target, item)
        activityLog.record(
            ActivityEvent(ActivityType.QUEUE_REORDERED, System.currentTimeMillis(), byPlayerId, item.playerName)
        )
        _state.value = _state.value.copy(queue = queue)
    }

    /** Moves a turn to the front of the queue (plays next). */
    fun bumpToNext(turnId: String, byPlayerId: String) =
        moveTurn(turnId, 0, byPlayerId)

    /** Removes a queued turn entirely. */
    fun removeTurn(turnId: String, byPlayerId: String) = synchronized(lock) {
        if (!isAdmin(byPlayerId)) return
        val turn = _state.value.queue.firstOrNull { it.id == turnId } ?: return
        activityLog.record(
            ActivityEvent(ActivityType.TURN_REMOVED, System.currentTimeMillis(), byPlayerId, turn.playerName)
        )
        _state.value = _state.value.copy(queue = _state.value.queue.filterNot { it.id == turnId })
    }

    // --- Applause / leaderboard -----------------------------------------

    /**
     * Records one applause from [fromPlayerId] for the current singer.
     * Rules: must be an active turn, one applause per guest per turn, and a
     * singer cannot applaud themselves.
     */
    fun applaud(fromPlayerId: String) = synchronized(lock) {
        val current = _state.value.nowPlaying ?: return
        if (current.playerId == fromPlayerId) return // no self-applause
        val voters = applaudedTurns.getOrPut(current.id) { mutableSetOf() }
        if (!voters.add(fromPlayerId)) return // already applauded this turn
        val target = current.playerId
        applauseByPlayer[target] = (applauseByPlayer[target] ?: 0) + 1
        activityLog.record(
            ActivityEvent(ActivityType.APPLAUSE, System.currentTimeMillis(), fromPlayerId, current.playerName)
        )
        _state.value = _state.value.copy(leaderboard = buildLeaderboard())
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

    private fun isAdmin(playerId: String): Boolean =
        _state.value.adminPlayerId == playerId

    private fun promoteToAdmin(playerId: String) {
        activityLog.record(
            ActivityEvent(ActivityType.ADMIN_ASSIGNED, System.currentTimeMillis(), playerId)
        )
        _state.value = _state.value.copy(adminPlayerId = playerId)
    }

    /** Builds leaderboard entries, highest applause first, resolving names. */
    private fun buildLeaderboard(): List<ScoreEntry> =
        applauseByPlayer.entries
            .mapNotNull { (pid, score) ->
                val name = players(pid)?.name ?: return@mapNotNull null
                ScoreEntry(pid, name, score)
            }
            .sortedByDescending { it.applause }

    private fun ActivityType.event() =
        ActivityEvent(this, System.currentTimeMillis())
}
