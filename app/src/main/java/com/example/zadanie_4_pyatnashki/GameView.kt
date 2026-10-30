package com.example.zadanie_4_pyatnashki

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

class GameView(context: Context, attrs: AttributeSet?) :
    SurfaceView(context, attrs), Runnable {

    private val GRID_SIZE = 4
    private var cellSize = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private val board = IntArray(GRID_SIZE * GRID_SIZE)
    private var emptyIndex = 0
    private var moves = 0
    private var gameWin = false
    private var startTime = System.currentTimeMillis()
    private var finalTime = 0L
    private val preferences = context.getSharedPreferences("game", Context.MODE_PRIVATE)
    private var bestScore = preferences.getInt("best", 0)

    private val tilePaint = Paint().apply {
        color = Color.parseColor("#3A86FF")
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val tileTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 80f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val emptyPaint = Paint().apply {
        color = Color.parseColor("#1B263B")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private var thread: Thread? = null
    @Volatile
    private var isRunning = false

    init {
        post {
            val topMargin = 200f
            val bottomMargin = 100f
            val availableHeight = height - topMargin - bottomMargin
            val availableWidth = width.toFloat()
            val fieldSize = Math.min(availableWidth, availableHeight)
            cellSize = fieldSize / GRID_SIZE
            offsetX = (width - fieldSize) / 2f
            offsetY = topMargin
            resetGame()
        }

        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                startGame()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                stopGame()
            }
        })
    }

    private fun startGame() {
        if (thread == null) {
            isRunning = true
            thread = Thread(this)
            thread?.start()
        }
    }

    private fun stopGame() {
        isRunning = false
        thread?.join()
        thread = null
    }

    override fun run() {
        while (isRunning) {
            draw()
            try {
                Thread.sleep(16)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    @Synchronized
    private fun draw() {
        val canvas = holder.lockCanvas() ?: return
        canvas.drawColor(Color.parseColor("#0D1B2A"))

        for (i in 0 until GRID_SIZE * GRID_SIZE) {
            val row = i / GRID_SIZE
            val col = i % GRID_SIZE
            val left = offsetX + col * cellSize
            val top = offsetY + row * cellSize
            val right = left + cellSize
            val bottom = top + cellSize
            if (board[i] == 0) {
                canvas.drawRect(left + 4f, top + 4f, right - 4f, bottom - 4f, emptyPaint)
            } else {
                canvas.drawRoundRect(
                    left + 4f, top + 4f, right - 4f, bottom - 4f,
                    15f, 15f, tilePaint
                )
                val textX = left + cellSize / 2
                val textY = top + cellSize / 2 - (tileTextPaint.descent() + tileTextPaint.ascent()) / 2
                canvas.drawText(board[i].toString(), textX, textY, tileTextPaint)
            }
        }

        val fieldRight = offsetX + GRID_SIZE * cellSize
        val fieldBottom = offsetY + GRID_SIZE * cellSize
        canvas.drawRect(offsetX, offsetY, fieldRight, fieldBottom, borderPaint)

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 60f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(8f, 4f, 4f, Color.BLACK)
        }
        canvas.drawText("Ходы: $moves", offsetX + 20f, offsetY - 40f, textPaint)
        val elapsed = getElapsedSeconds()
        val timeText = "Время: $elapsed с"
        canvas.drawText(
            timeText,
            fieldRight - 20f - textPaint.measureText(timeText),
            offsetY - 40f,
            textPaint
        )
        val bestText = if (bestScore == 0) "Рекорд: -" else "Рекорд: $bestScore"
        canvas.drawText(bestText, offsetX + 20f, fieldBottom + 80f, textPaint)

        if (gameWin) {
            val winPaint = Paint().apply {
                color = Color.parseColor("#76FF03")
                textSize = 100f
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                setShadowLayer(10f, 5f, 5f, Color.BLACK)
            }
            val centerX = offsetX + GRID_SIZE * cellSize / 2
            val centerY = offsetY + GRID_SIZE * cellSize / 2
            val overlayPaint = Paint().apply {
                color = Color.parseColor("#CC000000")
                style = Paint.Style.FILL
            }
            canvas.drawRect(
                centerX - 400f, centerY - 100f,
                centerX + 400f, centerY + 100f, overlayPaint
            )
            canvas.drawText("ПОБЕДА!", centerX, centerY + 30f, winPaint)
        }
        holder.unlockCanvasAndPost(canvas)
    }

    private fun shuffleBoard() {
        repeat(1000) {
            val moves = mutableListOf<Int>()
            val row = emptyIndex / GRID_SIZE
            val col = emptyIndex % GRID_SIZE
            if (row > 0) moves.add(emptyIndex - GRID_SIZE)
            if (row < GRID_SIZE - 1) moves.add(emptyIndex + GRID_SIZE)
            if (col > 0) moves.add(emptyIndex - 1)
            if (col < GRID_SIZE - 1) moves.add(emptyIndex + 1)
            val move = moves.random()
            swapTiles(emptyIndex, move)
            emptyIndex = move
        }
    }

    private fun swapTiles(a: Int, b: Int) {
        val temp = board[a]
        board[a] = board[b]
        board[b] = temp
    }

    @Synchronized
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gameWin || cellSize <= 0f) return true
        if (event.action == MotionEvent.ACTION_UP) {
            if (event.x < offsetX || event.y < offsetY ||
                event.x >= offsetX + GRID_SIZE * cellSize ||
                event.y >= offsetY + GRID_SIZE * cellSize) return true

            val col = ((event.x - offsetX) / cellSize).toInt()
            val row = ((event.y - offsetY) / cellSize).toInt()
            val index = row * GRID_SIZE + col
            if (canMove(index)) {
                swapTiles(emptyIndex, index)
                emptyIndex = index
                moves++
                if (checkWin()) {
                    gameWin = true
                }
            }
        }
        return true
    }

    private fun canMove(index: Int): Boolean {
        val row = index / GRID_SIZE
        val col = index % GRID_SIZE
        val emptyRow = emptyIndex / GRID_SIZE
        val emptyCol = emptyIndex % GRID_SIZE
        return (Math.abs(row - emptyRow) == 1 && col == emptyCol) ||
            (Math.abs(col - emptyCol) == 1 && row == emptyRow)
    }

    private fun checkWin(): Boolean {
        for (i in 0 until GRID_SIZE * GRID_SIZE - 1) {
            if (board[i] != i + 1) return false
        }
        if (board[GRID_SIZE * GRID_SIZE - 1] != 0) return false
        finalTime = System.currentTimeMillis()
        if (bestScore == 0 || moves < bestScore) {
            bestScore = moves
            preferences.edit().putInt("best", bestScore).apply()
        }
        return true
    }

    private fun getElapsedSeconds(): Long {
        val endTime = if (gameWin) finalTime else System.currentTimeMillis()
        return (endTime - startTime) / 1000
    }

    @Synchronized
    fun resetGame() {
        for (i in 0 until GRID_SIZE * GRID_SIZE - 1) {
            board[i] = i + 1
        }
        board[GRID_SIZE * GRID_SIZE - 1] = 0
        emptyIndex = GRID_SIZE * GRID_SIZE - 1
        shuffleBoard()
        moves = 0
        gameWin = false
        startTime = System.currentTimeMillis()
        finalTime = 0L
    }
}
