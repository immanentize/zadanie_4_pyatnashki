package com.example.zadanie_4_pyatnashki

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val gameView = findViewById<GameView>(R.id.gameView)
        val btnReset = findViewById<Button>(R.id.btnReset)
        btnReset.setOnClickListener {
            gameView.resetGame()
        }
    }
}
