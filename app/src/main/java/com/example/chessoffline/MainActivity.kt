package com.example.chessoffline

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var chessView: ChessView
    private lateinit var statusText: TextView
    private lateinit var difficultyGroup: RadioGroup
    private lateinit var newGameButton: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private var aiThinking = false

    // 0 = Easy, 1 = Medium, 2 = Hard
    private var difficulty = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chessView = findViewById(R.id.chessView)
        statusText = findViewById(R.id.statusText)
        difficultyGroup = findViewById(R.id.difficultyGroup)
        newGameButton = findViewById(R.id.newGameButton)

        difficultyGroup.setOnCheckedChangeListener { _, checkedId ->
            difficulty = when (checkedId) {
                R.id.radioEasy -> 0
                R.id.radioMedium -> 1
                else -> 2
            }
        }

        newGameButton.setOnClickListener { startNewGame() }

        chessView.onMove = { move -> onPlayerMove(move) }

        startNewGame()
    }

    private fun startNewGame() {
        aiThinking = false
        chessView.state = GameState.newGame()
        chessView.interactionEnabled = true
        chessView.resetSelection()
        chessView.invalidate()
        updateStatus()
    }

    private fun onPlayerMove(move: Move) {
        if (aiThinking) return
        ChessEngine.applyMove(chessView.state, move)
        chessView.invalidate()
        if (!checkGameOver()) {
            updateStatus()
            triggerAiMove()
        }
    }

    private fun triggerAiMove() {
        aiThinking = true
        chessView.interactionEnabled = false
        statusText.text = "Computer is thinking..."

        val stateSnapshot = chessView.state.clone()
        val level = difficulty

        Thread {
            val move = ChessEngine.findBestMove(stateSnapshot, level)
            mainHandler.post {
                if (move != null) {
                    ChessEngine.applyMove(chessView.state, move)
                    chessView.invalidate()
                }
                aiThinking = false
                chessView.interactionEnabled = true
                if (!checkGameOver()) {
                    updateStatus()
                }
            }
        }.start()
    }

    /** Returns true if the game has ended (checkmate/stalemate). */
    private fun checkGameOver(): Boolean {
        val status = ChessEngine.gameStatus(chessView.state)
        return when (status) {
            ChessEngine.GameStatus.CHECKMATE -> {
                val winner = if (chessView.state.whiteToMove) "Black" else "White"
                statusText.text = "Checkmate! $winner wins."
                chessView.interactionEnabled = false
                true
            }
            ChessEngine.GameStatus.STALEMATE -> {
                statusText.text = "Stalemate! It's a draw."
                chessView.interactionEnabled = false
                true
            }
            ChessEngine.GameStatus.ONGOING -> false
        }
    }

    private fun updateStatus() {
        val turn = if (chessView.state.whiteToMove) "White" else "Black"
        val check = if (ChessEngine.isInCheck(chessView.state, chessView.state.whiteToMove)) " — Check!" else ""
        statusText.text = "$turn to move$check"
    }
}
