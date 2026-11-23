package dev.roadloader.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The client is dark-first by design: the game frames sit behind translucent
 * panels, so a light theme would wash the HUD out.
 */
@Composable
fun RoadTheme(
    accent: Accent = RoadColors.accents.first(),
    content: @Composable () -> Unit,
) {
    val scheme = darkColorScheme(
        primary = accent.start,
        onPrimary = Color.White,
        secondary = accent.end,
        onSecondary = RoadColors.Ink,
        tertiary = RoadColors.Mint,
        background = RoadColors.Ink,
        onBackground = RoadColors.TextPrimary,
        surface = RoadColors.Night,
        onSurface = RoadColors.TextPrimary,
        surfaceVariant = RoadColors.NightElevated,
        onSurfaceVariant = RoadColors.TextSecondary,
        outline = RoadColors.GlassBorder,
        error = RoadColors.Danger,
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(
            colorScheme = scheme,
            typography = RoadTypography,
            content = content,
        )
    }
}

val LocalAccent = staticCompositionLocalOf { RoadColors.accents.first() }
