package dev.dreamteam.sportpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.dreamteam.sportpro.navigation.SportProNavHost
import dev.dreamteam.sportpro.ui.theme.SportProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SportProTheme {
                SportProNavHost()
            }
        }
    }
}
