package com.egepaksoy.ghost.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

private val Light = lightColorScheme(
    primary = Color(0xFF17695A), onPrimary = Color.White,
    primaryContainer = Color(0xFFB4F0DC), onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4B635B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8DD), onSecondaryContainer = Color(0xFF07201A),
    tertiary = Color(0xFFC2410C), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0CC), onTertiaryContainer = Color(0xFF3A1200),
    background = Color(0xFFF2F5F3), onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF2F5F3), onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDCE5E0), onSurfaceVariant = Color(0xFF56615C),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEAEFEC), surfaceContainerHigh = Color(0xFFE5EBE8),
    surfaceContainerHighest = Color(0xFFDDE4E1),
    outline = Color(0xFF707975), outlineVariant = Color(0xFFC6CFCB),
    error = Color(0xFFBA1A1A), onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF86D6BE), onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005142), onPrimaryContainer = Color(0xFFB4F0DC),
    secondary = Color(0xFFB1CCC1), onSecondary = Color(0xFF1D352E),
    secondaryContainer = Color(0xFF334B44), onSecondaryContainer = Color(0xFFCDE8DD),
    tertiary = Color(0xFFFFB68F), onTertiary = Color(0xFF552000),
    tertiaryContainer = Color(0xFF6E2F08), onTertiaryContainer = Color(0xFFFFDBC8),
    background = Color(0xFF0E1412), onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0E1412), onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFAEB9B4),
    surfaceContainerLowest = Color(0xFF090E0D), surfaceContainerLow = Color(0xFF19201D),
    surfaceContainer = Color(0xFF1C2421), surfaceContainerHigh = Color(0xFF26302C),
    surfaceContainerHighest = Color(0xFF313B37),
    outline = Color(0xFF89938F), outlineVariant = Color(0xFF3B4541),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

private val base = Typography()
private val AppType = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun GhostTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = AppType, content = content)
}

/** Strava-style route colours. */
val RouteOrange = Color(0xFFFC5200)
val StartGreen = Color(0xFF22B14C)
val EndGray = Color(0xFF8A9095)

/** Every place gets its own colour, the same on every screen. */
private val PlacePalette = listOf(
    Color(0xFF17806B), Color(0xFF3B6FD6), Color(0xFF8B4FD0), Color(0xFFCF3F78),
    Color(0xFF4F8A23), Color(0xFF1E8FB0), Color(0xFF5961C2), Color(0xFF9A6234),
)

fun placeColor(key: String): Color = PlacePalette[Math.floorMod(key.hashCode(), PlacePalette.size)]
