package dev.dreamteam.sportpro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SportOrange,
    secondary = SportOrangeLight,
    tertiary = SportOrange,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkBorder,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFF9A9CA2)
)

private val LightColorScheme = lightColorScheme(
    primary = SportOrangeLight,
    secondary = SportOrange,
    tertiary = SportOrangeLight,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightBorder,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = LightText,
    onSurface = LightText,
    onSurfaceVariant = Color(0xFF64748B)
)

@Composable
fun SportProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
