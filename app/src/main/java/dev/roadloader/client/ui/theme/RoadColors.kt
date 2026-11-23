package dev.roadloader.client.ui.theme

import androidx.compose.ui.graphics.Color

/** Palette for the client: deep navy glass with a violet/cyan/mint accent ramp. */
object RoadColors {
    val Ink = Color(0xFF060915)
    val Night = Color(0xFF0A1024)
    val NightElevated = Color(0xFF111A33)

    val Violet = Color(0xFF7C5CFF)
    val VioletDeep = Color(0xFF5B3DF5)
    val Cyan = Color(0xFF22D3EE)
    val Mint = Color(0xFF34D399)
    val Amber = Color(0xFFFBBF24)
    val Rose = Color(0xFFFB7185)

    val TextPrimary = Color(0xFFEAF0FF)
    val TextSecondary = Color(0xFF9AA8C7)
    val TextMuted = Color(0xFF6B7897)

    val GlassFill = Color(0x14FFFFFF)
    val GlassFillStrong = Color(0x1FFFFFFF)
    val GlassBorder = Color(0x24FFFFFF)
    val GlassBorderStrong = Color(0x3DFFFFFF)

    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFF59E0B)
    val Danger = Color(0xFFF87171)

    /** Accent presets offered in Appearance settings. */
    val accents: List<Accent> = listOf(
        Accent("Violet", Violet, Cyan),
        Accent("Aurora", Mint, Cyan),
        Accent("Sunset", Color(0xFFFF7A59), Amber),
        Accent("Rose", Rose, Violet),
        Accent("Ice", Cyan, Color(0xFF8AB4FF)),
    )
}

data class Accent(val name: String, val start: Color, val end: Color)
