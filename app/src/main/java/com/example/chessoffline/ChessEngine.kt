package com.example.chessoffline

import kotlin.math.max
import kotlin.random.Random

/**
 * Board representation: board[row][col], row 0 = rank 8 (black back rank, top of screen),
 * row 7 = rank 1 (white back rank, bottom of screen). col 0 = file 'a'.
 * Uppercase letters = white pieces, lowercase = black pieces, '.' = empty.
 * P/N/B/R/Q/K
 */
data class Move(
    val fromRow: Int,
    val fromCol: Int,
    val toRow: Int,
    val toCol: Int,
    val promotion: Char? = null,      // 'Q','R','B','N' (always uppercase letter, color inferred)
    val isCastleKingside: Boolean = false,
    val isCastleQueenside: Boolean = false,
    val isEnPassant: Boolean = false
)

class GameState(
    var board: Array<CharArray>,
    var whiteToMove: Boolean = true,
    var whiteKingMoved: Boolean = false,
    var blackKingMoved: Boolean = false,
    var whiteRookAMoved: Boolean = false, // a1 rook (queenside)
    var whiteRookHMoved: Boolean = false, // h1 rook (kingside)
    var blackRookAMoved: Boolean = false, // a8 rook (queenside)
    var blackRookHMoved: Boolean = false, // h8 rook (kingside)
    var enPassantRow: Int? = null,        // square behind the double-moved pawn
    var enPassantCol: Int? = null
) {
    fun clone(): GameState {
        val newBoard = Array(8) { r -> board[r].copyOf() }
        return GameState(
            newBoard, whiteToMove, whiteKingMoved, blackKingMoved,
            whiteRookAMoved, whiteRookHMoved, blackRookAMoved, blackRookHMoved,
            enPassantRow, enPassantCol
        )
    }

    companion object {
        fun newGame(): GameState {
            val b = Array(8) { CharArray(8) { '.' } }
            val backRank = "RNBQKBNR"
            for (c in 0..7) {
                b[0][c] = backRank[c].lowercaseChar()
                b[1][c] = 'p'
                b[6][c] = 'P'
                b[7][c] = backRank[c]
            }
            return GameState(b)
        }
    }
}

object ChessEngine {

    private const val MATE_SCORE = 1_000_000

    fun isWhitePiece(c: Char) = c != '.' && c.isUpperCase()
    fun isBlackPiece(c: Char) = c != '.' && c.isLowerCase()
    private fun isOwn(c: Char, white: Boolean) = c != '.' && (if (white) c.isUpperCase() else c.isLowerCase())
    private fun isEnemy(c: Char, white: Boolean) = c != '.' && (if (white) c.isLowerCase() else c.isUpperCase())
    private fun inBounds(r: Int, c: Int) = r in 0..7 && c in 0..7

    // ---------- Pseudo-legal move generation ----------

    private fun pseudoMoves(state: GameState): List<Move> {
        val moves = ArrayList<Move>()
        val board = state.board
        val white = state.whiteToMove
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = board[r][c]
                if (piece == '.' || !isOwn(piece, white)) continue
                when (piece.uppercaseChar()) {
                    'P' -> pawnMoves(state, r, c, white, moves)
                    'N' -> knightMoves(state, r, c, white, moves)
                    'B' -> slidingMoves(state, r, c, white, moves, arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1))
                    'R' -> slidingMoves(state, r, c, white, moves, arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1))
                    'Q' -> slidingMoves(
                        state, r, c, white, moves,
                        arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1)
                    )
                    'K' -> kingMoves(state, r, c, white, moves)
                }
            }
        }
        return moves
    }

    private fun pawnMoves(state: GameState, r: Int, c: Int, white: Boolean, moves: MutableList<Move>) {
        val board = state.board
        val dir = if (white) -1 else 1
        val startRow = if (white) 6 else 1
        val promoRow = if (white) 0 else 7
        val oneRow = r + dir
        if (inBounds(oneRow, c) && board[oneRow][c] == '.') {
            addPawnMove(moves, r, c, oneRow, c, promoRow)
            val twoRow = r + 2 * dir
            if (r == startRow && board[twoRow][c] == '.') {
                moves.add(Move(r, c, twoRow, c))
            }
        }
        for (dc in intArrayOf(-1, 1)) {
            val nc = c + dc
            if (!inBounds(oneRow, nc)) continue
            val target = board[oneRow][nc]
            if (isEnemy(target, white)) {
                addPawnMove(moves, r, c, oneRow, nc, promoRow)
            } else if (target == '.' && state.enPassantRow == oneRow && state.enPassantCol == nc) {
                moves.add(Move(r, c, oneRow, nc, isEnPassant = true))
            }
        }
    }

    private fun addPawnMove(moves: MutableList<Move>, fr: Int, fc: Int, tr: Int, tc: Int, promoRow: Int) {
        if (tr == promoRow) {
            for (p in charArrayOf('Q', 'R', 'B', 'N')) {
                moves.add(Move(fr, fc, tr, tc, promotion = p))
            }
        } else {
            moves.add(Move(fr, fc, tr, tc))
        }
    }

    private fun knightMoves(state: GameState, r: Int, c: Int, white: Boolean, moves: MutableList<Move>) {
        val offsets = arrayOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1
        )
        for ((dr, dc) in offsets) {
            val nr = r + dr; val nc = c + dc
            if (!inBounds(nr, nc)) continue
            val target = state.board[nr][nc]
            if (!isOwn(target, white)) moves.add(Move(r, c, nr, nc))
        }
    }

    private fun slidingMoves(
        state: GameState, r: Int, c: Int, white: Boolean, moves: MutableList<Move>, dirs: Array<Pair<Int, Int>>
    ) {
        val board = state.board
        for ((dr, dc) in dirs) {
            var nr = r + dr; var nc = c + dc
            while (inBounds(nr, nc)) {
                val target = board[nr][nc]
                if (target == '.') {
                    moves.add(Move(r, c, nr, nc))
                } else {
                    if (isEnemy(target, white)) moves.add(Move(r, c, nr, nc))
                    break
                }
                nr += dr; nc += dc
            }
        }
    }

    private fun kingMoves(state: GameState, r: Int, c: Int, white: Boolean, moves: MutableList<Move>) {
        val board = state.board
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = r + dr; val nc = c + dc
                if (!inBounds(nr, nc)) continue
                val target = board[nr][nc]
                if (!isOwn(target, white)) moves.add(Move(r, c, nr, nc))
            }
        }
        // Castling
        val kingRow = if (white) 7 else 0
        if (r == kingRow && c == 4) {
            val kingMoved = if (white) state.whiteKingMoved else state.blackKingMoved
            if (!kingMoved && !isSquareAttacked(state, r, c, !white)) {
                val rookHMoved = if (white) state.whiteRookHMoved else state.blackRookHMoved
                if (!rookHMoved && board[kingRow][5] == '.' && board[kingRow][6] == '.' &&
                    board[kingRow][7] == (if (white) 'R' else 'r') &&
                    !isSquareAttacked(state, kingRow, 5, !white) &&
                    !isSquareAttacked(state, kingRow, 6, !white)
                ) {
                    moves.add(Move(r, c, kingRow, 6, isCastleKingside = true))
                }
                val rookAMoved = if (white) state.whiteRookAMoved else state.blackRookAMoved
                if (!rookAMoved && board[kingRow][1] == '.' && board[kingRow][2] == '.' && board[kingRow][3] == '.' &&
                    board[kingRow][0] == (if (white) 'R' else 'r') &&
                    !isSquareAttacked(state, kingRow, 3, !white) &&
                    !isSquareAttacked(state, kingRow, 2, !white)
                ) {
                    moves.add(Move(r, c, kingRow, 2, isCastleQueenside = true))
                }
            }
        }
    }

    // ---------- Attack detection ----------

    fun isSquareAttacked(state: GameState, row: Int, col: Int, byWhite: Boolean): Boolean {
        val board = state.board
        // Pawn attacks
        val pawnDir = if (byWhite) 1 else -1 // attacking pawn sits "behind" relative to attack direction
        for (dc in intArrayOf(-1, 1)) {
            val pr = row + pawnDir
            val pc = col + dc
            if (inBounds(pr, pc)) {
                val p = board[pr][pc]
                if (p == (if (byWhite) 'P' else 'p')) return true
            }
        }
        // Knight attacks
        val knightOffsets = arrayOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
        for ((dr, dc) in knightOffsets) {
            val nr = row + dr; val nc = col + dc
            if (inBounds(nr, nc) && board[nr][nc] == (if (byWhite) 'N' else 'n')) return true
        }
        // King adjacency
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = row + dr; val nc = col + dc
            if (inBounds(nr, nc) && board[nr][nc] == (if (byWhite) 'K' else 'k')) return true
        }
        // Sliding: bishop/queen diagonals
        for ((dr, dc) in arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)) {
            var nr = row + dr; var nc = col + dc
            while (inBounds(nr, nc)) {
                val p = board[nr][nc]
                if (p != '.') {
                    if (p == (if (byWhite) 'B' else 'b') || p == (if (byWhite) 'Q' else 'q')) return true
                    break
                }
                nr += dr; nc += dc
            }
        }
        // Sliding: rook/queen orthogonals
        for ((dr, dc) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            var nr = row + dr; var nc = col + dc
            while (inBounds(nr, nc)) {
                val p = board[nr][nc]
                if (p != '.') {
                    if (p == (if (byWhite) 'R' else 'r') || p == (if (byWhite) 'Q' else 'q')) return true
                    break
                }
                nr += dr; nc += dc
            }
        }
        return false
    }

    private fun findKing(state: GameState, white: Boolean): Pair<Int, Int> {
        val target = if (white) 'K' else 'k'
        for (r in 0..7) for (c in 0..7) if (state.board[r][c] == target) return r to c
        return -1 to -1
    }

    fun isInCheck(state: GameState, white: Boolean): Boolean {
        val (kr, kc) = findKing(state, white)
        if (kr == -1) return false
        return isSquareAttacked(state, kr, kc, !white)
    }

    // ---------- Applying moves ----------

    fun applyMove(state: GameState, move: Move) {
        val board = state.board
        val white = state.whiteToMove
        val piece = board[move.fromRow][move.fromCol]

        // Reset en passant target; set again below if applicable
        state.enPassantRow = null
        state.enPassantCol = null

        if (move.isEnPassant) {
            board[move.toRow][move.toCol] = piece
            board[move.fromRow][move.fromCol] = '.'
            board[move.fromRow][move.toCol] = '.' // captured pawn sits on the from-row
        } else if (move.isCastleKingside) {
            val row = move.fromRow
            board[row][6] = piece
            board[row][4] = '.'
            board[row][5] = board[row][7]
            board[row][7] = '.'
        } else if (move.isCastleQueenside) {
            val row = move.fromRow
            board[row][2] = piece
            board[row][4] = '.'
            board[row][3] = board[row][0]
            board[row][0] = '.'
        } else {
            board[move.toRow][move.toCol] =
                if (move.promotion != null) (if (white) move.promotion else move.promotion.lowercaseChar())
                else piece
            board[move.fromRow][move.fromCol] = '.'
            // Double pawn push -> set en passant target
            if (piece.uppercaseChar() == 'P' && kotlin.math.abs(move.toRow - move.fromRow) == 2) {
                state.enPassantRow = (move.fromRow + move.toRow) / 2
                state.enPassantCol = move.fromCol
            }
        }

        // Track castling rights
        when {
            piece == 'K' -> state.whiteKingMoved = true
            piece == 'k' -> state.blackKingMoved = true
            piece == 'R' && move.fromRow == 7 && move.fromCol == 0 -> state.whiteRookAMoved = true
            piece == 'R' && move.fromRow == 7 && move.fromCol == 7 -> state.whiteRookHMoved = true
            piece == 'r' && move.fromRow == 0 && move.fromCol == 0 -> state.blackRookAMoved = true
            piece == 'r' && move.fromRow == 0 && move.fromCol == 7 -> state.blackRookHMoved = true
        }
        // If a rook is captured on its home square, revoke castling too
        if (move.toRow == 7 && move.toCol == 0) state.whiteRookAMoved = true
        if (move.toRow == 7 && move.toCol == 7) state.whiteRookHMoved = true
        if (move.toRow == 0 && move.toCol == 0) state.blackRookAMoved = true
        if (move.toRow == 0 && move.toCol == 7) state.blackRookHMoved = true

        state.whiteToMove = !state.whiteToMove
    }

    // ---------- Legal move generation ----------

    fun generateLegalMoves(state: GameState): List<Move> {
        val white = state.whiteToMove
        val legal = ArrayList<Move>()
        for (m in pseudoMoves(state)) {
            val next = state.clone()
            applyMove(next, m)
            if (!isInCheck(next, white)) legal.add(m)
        }
        return legal
    }

    fun legalMovesFrom(state: GameState, row: Int, col: Int): List<Move> =
        generateLegalMoves(state).filter { it.fromRow == row && it.fromCol == col }

    enum class GameStatus { ONGOING, CHECKMATE, STALEMATE }

    fun gameStatus(state: GameState): GameStatus {
        val moves = generateLegalMoves(state)
        if (moves.isNotEmpty()) return GameStatus.ONGOING
        return if (isInCheck(state, state.whiteToMove)) GameStatus.CHECKMATE else GameStatus.STALEMATE
    }

    // ---------- Evaluation ----------

    private fun pieceValue(p: Char): Int = when (p.uppercaseChar()) {
        'P' -> 100
        'N' -> 320
        'B' -> 330
        'R' -> 500
        'Q' -> 900
        'K' -> 20000
        else -> 0
    }

    // Small center-control bonus table, symmetric, values in centipawns
    private val centerBonus = arrayOf(
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0),
        intArrayOf(0, 5, 5, 5, 5, 5, 5, 0),
        intArrayOf(0, 5, 10, 10, 10, 10, 5, 0),
        intArrayOf(0, 5, 10, 20, 20, 10, 5, 0),
        intArrayOf(0, 5, 10, 20, 20, 10, 5, 0),
        intArrayOf(0, 5, 10, 10, 10, 10, 5, 0),
        intArrayOf(0, 5, 5, 5, 5, 5, 5, 0),
        intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
    )

    // Positive = good for white
    private fun evaluate(board: Array<CharArray>): Int {
        var score = 0
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p == '.') continue
                val value = pieceValue(p) + centerBonus[r][c]
                score += if (p.isUpperCase()) value else -value
            }
        }
        return score
    }

    // ---------- AI: negamax with alpha-beta pruning ----------

    private fun negamax(state: GameState, depth: Int, alphaIn: Int, betaIn: Int): Int {
        var alpha = alphaIn
        val moves = generateLegalMoves(state)
        if (moves.isEmpty()) {
            return if (isInCheck(state, state.whiteToMove)) -MATE_SCORE - depth else 0
        }
        if (depth == 0) {
            val raw = evaluate(state.board)
            return if (state.whiteToMove) raw else -raw
        }
        var best = Int.MIN_VALUE + 1
        for (m in moves) {
            val next = state.clone()
            applyMove(next, m)
            val score = -negamax(next, depth - 1, -betaIn, -alpha)
            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= betaIn) break
        }
        return best
    }

    /**
     * Picks a move for the side to move.
     * difficulty: 0 = Easy, 1 = Medium, 2 = Hard
     */
    fun findBestMove(state: GameState, difficulty: Int): Move? {
        val moves = generateLegalMoves(state).shuffled()
        if (moves.isEmpty()) return null

        // Easy plays a random legal move part of the time, and only looks 1 ply ahead otherwise.
        if (difficulty == 0 && Random.nextInt(100) < 45) {
            return moves.random()
        }

        val depth = when (difficulty) {
            0 -> 1
            1 -> 2
            else -> 3
        }

        var bestMove: Move? = null
        var bestScore = Int.MIN_VALUE
        var alpha = Int.MIN_VALUE + 1
        val beta = Int.MAX_VALUE - 1
        for (m in moves) {
            val next = state.clone()
            applyMove(next, m)
            val score = -negamax(next, depth - 1, -beta, -alpha)
            if (score > bestScore) {
                bestScore = score
                bestMove = m
            }
            if (score > alpha) alpha = score
        }
        return bestMove ?: moves.first()
    }
}
