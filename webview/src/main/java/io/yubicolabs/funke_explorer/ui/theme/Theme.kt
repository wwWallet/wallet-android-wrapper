package io.yubicolabs.funke_explorer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = FunkeMain,
    secondary = FunkeSecondary,
    tertiary = FunkeTertiary
)

private val LightColorScheme = lightColorScheme(
    primary = FunkeMain,
    secondary = FunkeSecondary,
    tertiary = FunkeTertiary
)

@Composable
fun FunkeExplorerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}