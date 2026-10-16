package com.partyoke.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.partyoke.tv.model.PartyState
import com.partyoke.tv.net.QrGenerator
import com.partyoke.tv.ui.theme.PartyCard
import com.partyoke.tv.ui.theme.PartyMuted
import com.partyoke.tv.ui.theme.PartyPink
import com.partyoke.tv.ui.theme.parseAccent

@Composable
fun SetupScreen(onPickFolder: () -> Unit, onScanStorage: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("PartyOke", fontSize = 72.sp, fontWeight = FontWeight.Black, color = PartyPink)
            Text("Scan. Sing. Party.", fontSize = 26.sp, color = PartyMuted)
            Spacer(Modifier.height(36.dp))
            Text(
                "Plug in your USB drive, then scan connected storage\nor select a folder containing your karaoke videos.",
                fontSize = 20.sp,
                color = Color.White,
            )
            Spacer(Modifier.height(28.dp))
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Button(onClick = onScanStorage, modifier = Modifier.focusRequester(focus)) {
                Text("Scan Connected USB / Storage", fontSize = 22.sp)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onPickFolder) {
                Text("Select USB Folder (System Picker)", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun LobbyScreen(state: PartyState, onStart: () -> Unit) {
    val accent = parseAccent(state.accentColor)
    Row(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        // Left: QR + join info
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(state.partyName, fontSize = 44.sp, fontWeight = FontWeight.Black, color = accent)
            Spacer(Modifier.height(8.dp))
            Text("Scan to Join the Party", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            QrCode(state.joinUrl, 360)
            Spacer(Modifier.height(16.dp))
            Text(state.joinUrl, fontSize = 20.sp, color = PartyMuted)
            Text("${state.songCount} songs loaded", fontSize = 18.sp, color = PartyMuted)
        }

        Spacer(Modifier.width(48.dp))

        // Right: players + start
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text("Players (${state.players.size})", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            if (state.players.isEmpty()) {
                Text("Waiting for players to join…", fontSize = 22.sp, color = PartyMuted)
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.players) { p ->
                        val isAdmin = p.id == state.adminPlayerId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PartyCard)
                                .padding(18.dp),
                        ) {
                            Text(
                                text = if (isAdmin) "${p.name}  👑 Admin" else p.name,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isAdmin) accent else Color.White,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val startFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { startFocus.requestFocus() }
            Button(onClick = onStart, modifier = Modifier.focusRequester(startFocus)) {
                Text("Start Party", fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun PlayingScreen(
    state: PartyState,
    onSongEnded: () -> Unit,
    onSkip: () -> Unit,
    currentUri: String?,
    key: String? = null,
) {
    val accent = parseAccent(state.accentColor)
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (currentUri != null) {
            // keying by the turn id guarantees a fresh player per song
            androidx.compose.runtime.key(key) {
                VideoPlayer(
                    uri = currentUri,
                    modifier = Modifier.fillMaxSize(),
                    onEnded = onSongEnded,
                )
            }
        }



        // 2. Top Right: Up Next (Single line box only)
        state.queue.firstOrNull()?.let { nextTurn ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC140A24))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Up Next: ${nextTurn.playerName}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
            }
        }



        if (currentUri == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No song playing", fontSize = 32.sp, color = Color.White)
                Text("Waiting for the next singer…", fontSize = 20.sp, color = PartyMuted)
            }
        }

        // 3. Bottom Right: Skip / Next button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp),
        ) {
            Button(onClick = onSkip) {
                Text("Skip / Next", fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun QrCode(content: String, sizeDp: Int) {
    val bitmap = remember(content) { QrGenerator.generate(content, 512) }
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Join QR code",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("No network", color = Color.Black)
        }
    }
}
