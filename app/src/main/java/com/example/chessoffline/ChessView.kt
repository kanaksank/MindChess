package com.example.chessoffline

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ChessView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var state: GameState = GameState.newGame()
    var interactionEnabled: Boolean = true
    var onMove: ((Move) -> Unit)? = null

    private var selectedRow = -1
    private var selectedCol = -1
    private var legalTargets: List<Move> = emptyList()

    private var squareSize = 0f

    private val lightPaint = Paint().apply { color = Color.parseColor("#EEEED2") }
    private val darkPaint = Paint().apply { color = Color.parseColor("#769656") }
    private val selectedPaint = Paint().apply { color = Color.parseColor("#88F6F669") }
    private val targetPaint = Paint().apply {
        color = Color.parseColor("#8877DD77")
        style = Paint.Style.FILL
    }
    private val checkPaint = Paint().apply { color = Color.parseColor("#AAE05555") }
    private val piecePaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private val whiteGlyphs = mapOf(
        'P' to "\u2659", 'N' to "\u2658", 'B' to "\u2657",
        'R' to "\u2656", 'Q' to "\u2655", 'K' to "\u2654"
    )
    private val blackGlyphs = mapOf(
        'P' to "\u265F", 'N' to "\u265E", 'B' to "\u265D",
        'R' to "\u265C", 'Q' to "\u265B", 'K' to "\u265A"
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(size, size)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        squareSize = w / 8f
        piecePaint.textSize = squareSize * 0.75f
    }

    fun resetSelection() {
        selectedRow = -1
        selectedCol = -1
        legalTargets = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kingInCheckPos: Pair<Int, Int>? = run {
            if (!ChessEngine.isInCheck(state, state.whiteToMove)) return@run null
            var pos: Pair<Int, Int>? = null
            for (r in 0..7) for (c in 0..7) {
                val p = state.board[r][c]
                if (p == (if (state.whiteToMove) 'K' else 'k')) pos = r to c
            }
            pos
        }

        for (r in 0..7) {
            for (c in 0..7) {
                val paint = if ((r + c) % 2 == 0) lightPaint else darkPaint
                val left = c * squareSize
                val top = r * squareSize
                canvas.drawRect(left, top, left + squareSize, top + squareSize, paint)

                if (kingInCheckPos != null && kingInCheckPos.first == r && kingInCheckPos.second == c) {
                    canvas.drawRect(left, top, left + squareSize, top + squareSize, checkPaint)
                }
                if (r == selectedRow && c == selectedCol) {
                    canvas.drawRect(left, top, left + squareSize, top + squareSize, selectedPaint)
                }
            }
        }

        for (m in legalTargets) {
            val cx = m.toCol * squareSize + squareSize / 2
            val cy = m.toRow * squareSize + squareSize / 2
            canvas.drawCircle(cx, cy, squareSize * 0.16f, targetPaint)
        }

        for (r in 0..7) {
            for (c in 0..7) {
                val p = state.board[r][c]
                if (p == '.') continue
                val glyph = if (p.isUpperCase()) whiteGlyphs[p] else blackGlyphs[p.uppercaseChar()]
                if (glyph != null) {
                    piecePaint.color = if (p.isUpperCase()) Color.WHITE else Color.BLACK
                    piecePaint.style = Paint.Style.FILL
                    val cx = c * squareSize + squareSize / 2
                    val cy = r * squareSize + squareSize / 2 - (piecePaint.ascent() + piecePaint.descent()) / 2
                    canvas.drawText(glyph, cx, cy, piecePaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interactionEnabled) return true
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (squareSize == 0f) return true

        val col = (event.x / squareSize).toInt().coerceIn(0, 7)
        val row = (event.y / squareSize).toInt().coerceIn(0, 7)

        if (selectedRow == -1) {
            val piece = state.board[row][col]
            if (piece != '.' && ChessEngine.isWhitePiece(piece) == state.whiteToMove) {
                selectedRow = row
                selectedCol = col
                legalTargets = ChessEngine.legalMovesFrom(state, row, col)
                invalidate()
            }
        } else {
            val chosen = legalTargets.find { it.toRow == row && it.toCol == col }
            if (chosen != null) {
                resetSelection()
                onMove?.invoke(chosen)
            } else {
                val piece = state.board[row][col]
                if (piece != '.' && ChessEngine.isWhitePiece(piece) == state.whiteToMove) {
                    selectedRow = row
                    selectedCol = col
                    legalTargets = ChessEngine.legalMovesFrom(state, row, col)
                    invalidate()
                } else {
                    resetSelection()
                }
            }
        }
        return true
    }
}
