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

/**
 * The single source of truth for the whole party. The TV renders it and the
 * local server serializes it to phones over WebSocket so everyone stays in sync.
 */
@Serializable
data class PartyState(
    val phase: PartyPhase = PartyPhase.SETUP,
    val joinUrl: String = "",
    val songCount: Int = 0,
    val players: List<Player> = emptyList(),
    val queue: List<Turn> = emptyList(),
    val nowPlaying: Turn? = null,
)
