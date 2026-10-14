package com.partyoke.tv.net

import com.partyoke.tv.game.PartyManager
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class JoinRequest(val name: String)

@Serializable
private data class JoinResponse(val id: String, val name: String)

@Serializable
private data class SkipRequest(val playerId: String)

/**
 * Embedded HTTP + WebSocket server that phones connect to over local Wi-Fi.
 * Serves the join page, accepts joins/skips, and streams [PartyManager.state]
 * to every connected phone in real time.
 */
class PartyServer(
    private val manager: PartyManager,
    private val port: Int = 8080,
) {
    private var engine: ApplicationEngine? = null
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun start() {
        if (engine != null) return
        engine = embeddedServer(Netty, port = port, host = "0.0.0.0") {
            install(WebSockets)
            install(ContentNegotiation) { json(json) }

            routing {
                get("/") {
                    call.respondText(JoinPage.HTML, ContentType.Text.Html)
                }

                post("/join") {
                    val req = call.receive<JoinRequest>()
                    val player = manager.addPlayer(req.name)
                    call.respond(JoinResponse(player.id, player.name))
                }

                post("/skip") {
                    val req = call.receive<SkipRequest>()
                    // A guest can only shuffle/skip when it's their turn on screen.
                    val current = manager.state.value.nowPlaying
                    if (current?.playerId == req.playerId) {
                        manager.reshuffleCurrent()
                    }
                    call.respondText("ok")
                }

                webSocket("/ws") {
                    // Push the full state on every change to this phone.
                    manager.state.collectLatest { state ->
                        send(Frame.Text(json.encodeToString(state)))
                    }
                }
            }
        }.also { it.start(wait = false) }
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 300, timeoutMillis = 1000)
        engine = null
    }
}
