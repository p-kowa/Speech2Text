package com.example.speech2text

import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity

class VoiceCloning : AppCompatActivity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clonevoice)

        enableEdgeToEdge()
        window.navigationBarColor = getColor(R.color.blueLight)

    }
}