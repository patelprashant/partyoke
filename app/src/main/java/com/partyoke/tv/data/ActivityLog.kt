package com.partyoke.tv.data

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/** Types of events we record during a party. */
enum class ActivityType {
    PLAYER_JOINED,
    PLAYER_LEFT,
    SONG_ASSIGNED,
    TURN_STARTED,
    TURN_COMPLETED,
    TURN_SKIPPED,
    PARTY_STARTED,
    CATALOG_LOADED,
    ADMIN_ASSIGNED,
    QUEUE_REORDERED,
    TURN_REMOVED,
    APPLAUSE,
    PARTY_RENAMED,
    ACCENT_CHANGED,
}

data class ActivityEvent(
    val type: ActivityType,
    val timestampMillis: Long,
    val playerId: String? = null,
    val detail: String = "",
)

/**
 * Records all user activities during a session.
 *
 * This is intentionally behind a small interface so the storage can later be
 * swapped (e.g. a file-backed log, Room, or a remote backend) without touching
 * game logic.
 */
interface ActivityLog {
    fun record(event: ActivityEvent)
    fun snapshot(): List<ActivityEvent>
}

/** Default in-memory implementation; also mirrors to Logcat for debugging. */
class InMemoryActivityLog : ActivityLog {
    private val events = CopyOnWriteArrayList<ActivityEvent>()

    override fun record(event: ActivityEvent) {
        events.add(event)
        Log.d(TAG, "${event.type} player=${event.playerId} ${event.detail}")
    }

    override fun snapshot(): List<ActivityEvent> = events.toList()

    private companion object {
        const val TAG = "PartyOkeActivity"
    }
}
