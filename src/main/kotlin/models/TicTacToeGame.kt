package com.example.models

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import mu.KotlinLogging

class TicTacToeGame {
    private val logger = KotlinLogging.logger {}
    private val state = MutableStateFlow(GameState())

    private val playerSockets = ConcurrentHashMap<Char, WebSocketSession>()

    private val gameScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var delayGameJob: Job? = null
    init {
        state.onEach { gameState ->
            logger.info { "Broadcasting game state: $gameState" }
            broadcast(gameState)
        }.launchIn(gameScope)
    }
    
    fun connectPlayer(session: WebSocketSession): Char? {
        val isPlayerX = state.value.connectedPlayers.any{ it == 'X'}
        val player = if (isPlayerX) 'O' else 'X'
        
        logger.info { "Player $player attempting to connect" }

        state.update {
            if (state.value.connectedPlayers.contains(player)){
                logger.warn { "Player $player already connected" }
                return null
            }
            
            // Store the socket before updating state
            playerSockets[player] = session
            
            logger.info { "Player $player connected successfully" }
            it.copy(
              connectedPlayers = it.connectedPlayers + player
            )
        }
        return player
    }
    
    fun disconnectPlayer(player: Char){
        logger.info { "Player $player disconnecting" }
        playerSockets.remove(player)
        state.update {
            it.copy(
                connectedPlayers = it.connectedPlayers - player
            )
        }
    }

    suspend fun broadcast(state: GameState){
        logger.debug { "Broadcasting to ${playerSockets.size} players" }
        playerSockets.values.forEach { socket->
            try {
                socket.send(
                    Json.encodeToString(state)
                )
            } catch (e: Exception) {
                logger.error(e) { "Error broadcasting to player" }
            }
        }
    }

    fun finishTurn(player: Char, x: Int,y: Int){
        logger.info { "Player $player attempting move at ($x, $y)" }
        
        // Validate coordinates
        if (x < 0 || x > 2 || y < 0 || y > 2) {
            logger.warn { "Invalid move: coordinates out of bounds ($x, $y)" }
            return
        }
        
        if (state.value.field[y][x] != null || state.value.winningPlayer != null){
            logger.warn { "Invalid move: position already taken or game finished" }
            return
        }

        if (state.value.playerAtTurn != player){
            logger.warn { "Invalid move: not player's turn" }
            return
        }
        
        val currentPlayer = state.value.playerAtTurn
        state.update {
            val newField = it.field.also { field->
                field[y][x] = currentPlayer
            }

            val isBoardFull = newField.all {
                it.all { it != null }
            }
            if (isBoardFull){
                logger.info { "Board is full, starting new round" }
                startNewRoundDelayed()
            }
            it.copy(
                playerAtTurn = if (currentPlayer == 'X') 'O' else 'X',
                field = newField,
                isBoardFull = isBoardFull,
                winningPlayer = getWinningPLayer()?.also{
                    logger.info { "Player $it won the game" }
                    startNewRoundDelayed()
                }
            )
        }
    }

    private fun startNewRoundDelayed() {
        delayGameJob?.cancel()
        delayGameJob = gameScope.launch {
            delay(5000L)
            state.update {
                it.copy(
                    playerAtTurn = 'X',
                    field = GameState.emptyField(),
                    winningPlayer = null,
                    isBoardFull = false
                )
            }
        }
    }

    private fun getWinningPLayer() : Char?{
        val field = state.value.field
        return if (field[0][0] != null && field[0][0] == field[0][1] && field[0][1] == field[0][2]) {
            field[0][0]
        } else if (field[1][0] != null && field[1][0] == field[1][1] && field[1][1] == field[1][2]) {
            field[1][0]
        } else if (field[2][0] != null && field[2][0] == field[2][1] && field[2][1] == field[2][2]) {
            field[2][0]
        } else if (field[0][0] != null && field[0][0] == field[1][0] && field[1][0] == field[2][0]) {
            field[0][0]
        } else if (field[0][1] != null && field[0][1] == field[1][1] && field[1][1] == field[2][1]) {
            field[0][1]
        } else if (field[0][2] != null && field[0][2] == field[1][2] && field[1][2] == field[2][2]) {
            field[0][2]
        } else if (field[0][0] != null && field[0][0] == field[1][1] && field[1][1] == field[2][2]) {
            field[0][0]
        } else if (field[0][2] != null && field[0][2] == field[1][1] && field[1][1] == field[2][0]) {
            field[0][2]
        } else null
    }
}