package com.example.hotelreception

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Контент рисуется под системными панелями; отступы учитывает Scaffold
        enableEdgeToEdge()
        // Вспоминаем сохранённый язык (русский или английский)
        AppLanguage.load(this)
        setContent {
            HotelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HotelScreen()
                }
            }
        }
    }
}
