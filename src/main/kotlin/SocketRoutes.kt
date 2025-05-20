package com.example

import com.example.models.MakeTurn
import com.example.models.TicTacToeGame
import io.ktor.http.HttpMessage
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.json.Json
import mu.KotlinLogging

fun Route.socket(game: TicTacToeGame){
    val logger = KotlinLogging.logger {}
    
    route("/play"){
        webSocket {
            logger.info { "New WebSocket connection attempt" }
            val player = game.connectPlayer(this)

            if (player == null){
                logger.warn { "Connection rejected: 2 players already connected" }
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT,"2 players already connected"))
                return@webSocket
            }

            logger.info { "WebSocket connection established for player $player" }

            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text){
                        val message = frame.readText()
                        logger.debug { "Received message from player $player: $message" }
                        
                        if (message.isBlank()) {
                            logger.warn { "Received empty message from player $player" }
                            return@consumeEach
                        }
                        
                        val action = extractAction(message)
                        if (action.x >= 0 && action.y >= 0) {
                            game.finishTurn(player, action.x, action.y)
                        } else {
                            logger.warn { "Invalid move coordinates received from player $player: (${action.x}, ${action.y})" }
                        }
                    }
                }
            } catch (e: Exception){
                logger.error(e) { "Error in WebSocket connection for player $player" }
                e.printStackTrace()
            } finally {
                logger.info { "WebSocket connection closed for player $player" }
                game.disconnectPlayer(player)
            }
        }
    }
}

private fun extractAction(message: String): MakeTurn {
    val logger = KotlinLogging.logger {}
    
    if (!message.contains("#")) {
        logger.warn { "Invalid message format: missing '#' separator" }
        return MakeTurn(-1, -1)
    }
    
    val type = message.substringBefore("#")
    val body = message.substringAfter("#")

    return if (type == "make_turn"){
        try {
            Json.decodeFromString(body)
        } catch (e: Exception) {
            logger.error(e) { "Error decoding make_turn action: $body" }
            MakeTurn(-1, -1)
        }
    } else {
        logger.warn { "Unknown action type: $type" }
        MakeTurn(-1, -1)
    }
}