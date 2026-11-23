package dev.roadloader.client.data

import java.util.Locale

/** Human readable size, used on instance cards and import results. */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.US, if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
}

/** Play time as `1h 04m` / `12m`. */
fun formatPlayTime(millis: Long): String {
    if (millis <= 0) return "never played"
    val minutes = millis / 60_000
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%dh %02dm", hours, minutes % 60)
    } else {
        "${minutes}m"
    }
}
