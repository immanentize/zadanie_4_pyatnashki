package com.example.zadanie_4_pyatnashki

import android.content.Context
import android.view.MotionEvent
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameViewTest {
    private lateinit var view: GameView
    private var savedBest = 0

    @Before
    fun createView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        savedBest = instrumentation.targetContext.getSharedPreferences("game", Context.MODE_PRIVATE)
            .getInt("best", 0)
        instrumentation.runOnMainSync {
            view = GameView(instrumentation.targetContext, null)
        }
        write("cellSize", 100f)
        write("offsetX", 20f)
        write("offsetY", 200f)
        setBoard(IntArray(16) { if (it == 15) 0 else it + 1 })
    }

    @After
    fun restoreBestScore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val editor = context.getSharedPreferences("game", Context.MODE_PRIVATE).edit()
        if (savedBest == 0) editor.remove("best") else editor.putInt("best", savedBest)
        editor.commit()
    }

    private fun read(name: String): Any? {
        val field = GameView::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(view)
    }

    private fun write(name: String, value: Any) {
        val field = GameView::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(view, value)
    }

    private fun board(): IntArray = read("board") as IntArray

    private fun setBoard(values: IntArray) {
        values.copyInto(board())
        write("emptyIndex", values.indexOf(0))
    }

    private fun tap(x: Float, y: Float) {
        val down = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(0, 100, MotionEvent.ACTION_UP, x, y, 0)
        view.onTouchEvent(down)
        view.onTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun tapCell(index: Int) {
        tap(20f + index % 4 * 100f + 50f, 200f + index / 4 * 100f + 50f)
    }

    @Test
    fun shuffledBoardsAreSolvable() {
        repeat(100) {
            view.resetGame()
            val board = board()
            assertEquals((0..15).toList(), board.sorted())
            assertEquals(board.indexOf(0), read("emptyIndex"))
            var inversions = 0
            for (i in board.indices) {
                for (j in i + 1 until board.size) {
                    if (board[i] != 0 && board[j] != 0 && board[i] > board[j]) inversions++
                }
            }
            val emptyRowFromBottom = 4 - board.indexOf(0) / 4
            assertEquals(1, (inversions + emptyRowFromBottom) % 2)
            assertEquals(0, read("moves"))
        }
    }

    @Test
    fun adjacentTapMovesOneTileAndCountsMove() {
        tapCell(14)
        assertEquals(15, board()[15])
        assertEquals(0, board()[14])
        assertEquals(14, read("emptyIndex"))
        assertEquals(1, read("moves"))
    }

    @Test
    fun invalidTapsDoNotMoveTiles() {
        val initial = board().copyOf()
        tapCell(0)
        tapCell(10)
        tapCell(15)
        tap(19f, 550f)
        tap(421f, 550f)
        tap(370f, 199f)
        tap(370f, 600f)
        assertArrayEquals(initial, board())
        assertEquals(0, read("moves"))
    }

    @Test
    fun rowEdgesAreNotNeighbors() {
        val values = board().copyOf()
        values[4] = 0
        values[15] = 5
        setBoard(values)
        tapCell(3)
        assertArrayEquals(values, board())
        assertEquals(0, read("moves"))
    }

    @Test
    fun finalMoveWinsAndStopsMoves() {
        val values = board().copyOf()
        values[14] = 0
        values[15] = 15
        setBoard(values)
        tapCell(15)
        assertEquals(true, read("gameWin"))
        assertEquals(1, read("moves"))
        val solved = board().copyOf()
        tapCell(14)
        assertArrayEquals(solved, board())
        assertEquals(1, read("moves"))
    }

    @Test
    fun resetClearsMovesAndVictory() {
        write("moves", 25)
        write("gameWin", true)
        view.resetGame()
        assertEquals(0, read("moves"))
        assertEquals(false, read("gameWin"))
        assertEquals((0..15).toList(), board().sorted())
    }

    private fun winWithMoves(moves: Int) {
        setBoard(IntArray(16) { if (it == 14) 0 else if (it == 15) 15 else it + 1 })
        write("moves", moves - 1)
        write("gameWin", false)
        tapCell(15)
    }

    @Test
    fun timerRunsAndFreezesOnVictory() {
        write("startTime", System.currentTimeMillis() - 12000L)
        val method = GameView::class.java.getDeclaredMethod("getElapsedSeconds")
        method.isAccessible = true
        assertTrue((method.invoke(view) as Long) >= 12L)
        winWithMoves(10)
        val endTime = read("finalTime") as Long
        assertTrue(endTime > 0L)
        write("startTime", endTime - 12000L)
        assertEquals(12L, method.invoke(view))
    }

    @Test
    fun resetRestartsTimerAndKeepsRecord() {
        write("startTime", System.currentTimeMillis() - 60000L)
        write("finalTime", System.currentTimeMillis())
        write("gameWin", true)
        write("bestScore", 10)
        view.resetGame()
        val method = GameView::class.java.getDeclaredMethod("getElapsedSeconds")
        method.isAccessible = true
        assertEquals(0L, method.invoke(view))
        assertEquals(0L, read("finalTime"))
        assertEquals(10, read("bestScore"))
    }

    @Test
    fun firstVictorySavesRecordAndReloadsIt() {
        write("bestScore", 0)
        winWithMoves(10)
        assertEquals(10, read("bestScore"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertEquals(10, instrumentation.targetContext.getSharedPreferences("game", Context.MODE_PRIVATE)
            .getInt("best", 0))
        instrumentation.runOnMainSync {
            view = GameView(instrumentation.targetContext, null)
        }
        assertEquals(10, read("bestScore"))
    }

    @Test
    fun onlyFewerMovesImproveRecord() {
        write("bestScore", 0)
        winWithMoves(10)
        winWithMoves(15)
        assertEquals(10, read("bestScore"))
        winWithMoves(4)
        assertEquals(4, read("bestScore"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(4, context.getSharedPreferences("game", Context.MODE_PRIVATE).getInt("best", 0))
    }

    @Test
    fun resetButtonStartsNewGame() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                view = activity.findViewById(R.id.gameView)
                write("moves", 25)
                write("gameWin", true)
                activity.findViewById<Button>(R.id.btnReset).performClick()
                assertEquals(0, read("moves"))
                assertEquals(false, read("gameWin"))
                assertEquals((0..15).toList(), board().sorted())
            }
        }
    }
}
