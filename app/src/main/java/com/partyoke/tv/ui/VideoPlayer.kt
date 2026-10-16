package com.partyoke.tv.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Full-screen ExoPlayer surface that plays the karaoke video at [uri].
 * Invokes [onEnded] when the song finishes so the party can advance.
 */
@Composable
fun VideoPlayer(
    uri: String,
    modifier: Modifier = Modifier,
    onEnded: () -> Unit,
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(uri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
        exoPlayer.prepare()
        exoPlayer.play()

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("VideoPlayer", "ExoPlayer playback error: ${error.message}", error)
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                keepScreenOn = true
            }
        },
        update = { view ->
            view.player = exoPlayer
        }
    )
}
