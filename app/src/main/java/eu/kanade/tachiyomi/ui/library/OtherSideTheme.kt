package eu.kanade.tachiyomi.ui.library

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Distinct dark-purple color scheme applied when in OtherSide mode. Shared between the
// Library screen and the category-selection dialog so both "cross over" to the same look.
internal val OtherSideColorScheme = darkColorScheme(
    primary = Color(0xFF7030C0),
    onPrimary = Color.White,
    secondary = Color(0xFFCDB6FF),
    onSecondary = Color.White,
    tertiary = Color(0xFFCDB6FF),
    background = Color(0xFF0C0820),
    onBackground = Color.White,
    surface = Color(0xFF12111D),
    onSurface = Color.White,
    surfaceContainerHigh = Color(0xFF1B1830),
)
