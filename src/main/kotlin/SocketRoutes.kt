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

fun Route.socket(game: TicTacToeGame){
    route("/play"){
        webSocket {
            val player = game.connectPlayer(this)

            if (player == null){
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT,"2 players already connected"))
                return@webSocket
            }

            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text){
                        val action = extractAction(frame.readText())
                        game.finishTurn(player,action.x,action.y)
                    }
                }
            } catch (e: Exception){
                e.printStackTrace()
            } finally {
                game.disconnectPlayer(player)
            }

        }
    }
}

private fun extractAction(message: String): MakeTurn {
    //make_turn

    val type = message.substringBefore("#")
    val body = message.substringAfter("#")

    return if (type == "make_turn"){
        Json.decodeFromString(body)
    } else MakeTurn(-1,-1)
}