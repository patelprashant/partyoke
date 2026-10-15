package com.partyoke.tv.model

import kotlinx.serialization.Serializable

/**
 * A karaoke video discovered on the USB drive.
 *
 * @param id        stable identifier (hash of the uri)
 * @param title     human-friendly title derived from the file name
 * @param uri       content:// uri from the Storage Access Framework; used by ExoPlayer
 */
@Serializable
data class Song(
    val id: String,
    val title: String,
    val uri: String,
)

/**
 * A guest who joined the party by scanning the QR code.
 */
@Serializable
data class Player(
    val id: String,
    val name: String,
    val joinedAtMillis: Long,
)

/** Lifecycle of a single turn in the queue. */
@Serializable
enum class TurnStatus { QUEUED, NOW_PLAYING, DONE, SKIPPED }

/** A player's applause tally, used for the leaderboard. */
@Serializable
data class ScoreEntry(
    val playerId: String,
    val playerName: String,
    val applause: Int,
)

/**
 * One entry in the turn queue: a player paired with their randomly-assigned song.
 */
@Serializable
data class Turn(
    val id: String,
    val playerId: String,
    val playerName: String,
    val songId: String,
    val songTitle: String,
    val status: TurnStatus = TurnStatus.QUEUED,
    val order: Int,
)

/** High-level phase the party is in, mirrored on the TV UI. */
@Serializable
enum class PartyPhase { SETUP, LOBBY, PLAYING }

/** Default party branding, applied on app start. */
object PartyDefaults {
    const val NAME = "PartyOke"
    const val ACCENT = "#FF2E92" // party pink
}

/**
 * The single source of truth for the whole party. The TV renders it and the
 * local server serializes it to phones over WebSocket so everyone stays in sync.
 *
 * @param partyName     editable party title (admin can rename); defaults to "PartyOke"
 * @param accentColor   hex accent used on TV + phones; defaults to party pink
 * @param adminPlayerId id of the player who is the admin (the first to join)
 * @param leaderboard   applause tallies, highest first
 */
@Serializable
data class PartyState(
    val phase: PartyPhase = PartyPhase.SETUP,
    val joinUrl: String = "",
    val songCount: Int = 0,
    val players: List<Player> = emptyList(),
    val queue: List<Turn> = emptyList(),
    val nowPlaying: Turn? = null,
    val partyName: String = PartyDefaults.NAME,
    val accentColor: String = PartyDefaults.ACCENT,
    val adminPlayerId: String? = null,
    val leaderboard: List<ScoreEntry> = emptyList(),
)
