package dev.roadloader.client.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.roadloader.client.BuildConfig
import dev.roadloader.client.data.ClientSettings
import dev.roadloader.client.session.EngineStats
import dev.roadloader.client.ui.components.AccentSlider
import dev.roadloader.client.ui.components.BrandHeader
import dev.roadloader.client.ui.components.GlassCard
import dev.roadloader.client.ui.components.GlassDivider
import dev.roadloader.client.ui.components.GlassPillButton
import dev.roadloader.client.ui.components.SectionHeader
import dev.roadloader.client.ui.components.ToggleRow
import dev.roadloader.client.ui.theme.RoadColors

@Composable
fun SettingsScreen(
    settings: ClientSettings,
    stats: EngineStats,
    coreVersion: Int,
    abi: String,
    onUpdate: ((ClientSettings) -> ClientSettings) -> Unit,
    onResetCrashGuard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { BrandHeader(title = "Settings", subtitle = "Engine, client and build") }

        item { SectionHeader(title = "Engine") }
        item {
            GlassCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
                AccentSlider(
                    label = "Heap cap",
                    value = settings.heapLimitMb.toFloat(),
                    valueText = "${settings.heapLimitMb} MB",
                    range = 512f..2048f,
                    steps = 14,
                    onChange = { value ->
                        onUpdate { it.copy(heapLimitMb = (value / 128f).toInt() * 128) }
                    },
                )
                AccentSlider(
                    label = "Render distance",
                    value = settings.renderDistance.toFloat(),
                    valueText = "${settings.renderDistance} chunks",
                    range = 2f..32f,
                    steps = 29,
                    onChange = { value -> onUpdate { it.copy(renderDistance = value.toInt()) } },
                )
                AccentSlider(
                    label = "Audio volume",
                    value = settings.volume,
                    valueText = "${(settings.volume * 100).toInt()}%",
                    range = 0f..1f,
                    onChange = { value -> onUpdate { it.copy(volume = value) } },
                )
            }
        }

        item {
            GlassCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                ToggleRow(
                    title = "BetterRenderDragon",
                    subtitle = "Post-process shaders over the 32-bit frame",
                    checked = settings.shaders,
                    onCheckedChange = { value -> onUpdate { it.copy(shaders = value) } },
                )
                GlassDivider()
                ToggleRow(
                    title = "Safe Mode",
                    subtitle = "Disable Rust fast paths when a build misbehaves",
                    checked = settings.safeMode,
                    onCheckedChange = { value -> onUpdate { it.copy(safeMode = value) } },
                )
                GlassDivider()
                ToggleRow(
                    title = "Stream audio",
                    subtitle = "Route the companion's audio into the 64-bit mixer",
                    checked = settings.streamAudio,
                    onCheckedChange = { value -> onUpdate { it.copy(streamAudio = value) } },
                )
            }
        }

        item { SectionHeader(title = "Client") }
        item {
            GlassCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                ToggleRow(
                    title = "Auto sign-in",
                    subtitle = "Refresh the Microsoft session when a world launches",
                    checked = settings.autoSignIn,
                    onCheckedChange = { value -> onUpdate { it.copy(autoSignIn = value) } },
                )
                GlassDivider()
                ToggleRow(
                    title = "Open menu on launch",
                    subtitle = "Show the mod menu with the first frame",
                    checked = settings.openMenuOnLaunch,
                    onCheckedChange = { value -> onUpdate { it.copy(openMenuOnLaunch = value) } },
                )
                GlassDivider()
                ToggleRow(
                    title = "Developer mode",
                    subtitle = "Expose live engine counters and the crash guard",
                    checked = settings.developerMode,
                    onCheckedChange = { value -> onUpdate { it.copy(developerMode = value) } },
                )
            }
        }

        if (settings.developerMode) {
            item { SectionHeader(title = "Developer") }
            item {
                GlassCard(contentPadding = PaddingValues(20.dp)) {
                    StatLine("Companion pid", if (stats.pid > 0) stats.pid.toString() else "not running")
                    StatLine("Frames", stats.frameCount.toString())
                    StatLine("FPS", stats.fps.toString())
                    StatLine("Heartbeat", "${stats.pingMs} ms")
                    StatLine("Heap", "${stats.heapUsedMb} / ${stats.heapLimitMb} MB")
                    StatLine("Render", "${stats.renderAvgUs / 1000.0} ms")
                    StatLine("Input", "${stats.inputPushed} pushed · ${stats.inputConsumed} consumed")
                    StatLine("Audio blocks", stats.audioPushed.toString())
                    StatLine("Self-test", "0x${stats.selfTest.toString(16)}")
                    Spacer(Modifier.height(12.dp))
                    GlassPillButton(
                        text = "Reset crash guard",
                        onClick = onResetCrashGuard,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item { SectionHeader(title = "About") }
        item {
            GlassCard(contentPadding = PaddingValues(20.dp)) {
                StatLine("RoadClient", BuildConfig.VERSION_NAME)
                StatLine("Engine core", "v$coreVersion")
                StatLine("ABI", abi)
                StatLine("Build", BuildConfig.BUILD_TYPE)
                StatLine("Renderer", "GLES 3 + Dear ImGui 1.91.5")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Frames, input and audio travel over a shared memory region " +
                        "created by RoadLoader's 32-bit companion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RoadColors.TextMuted,
                )
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = RoadColors.TextMuted)
        Text(value, style = MaterialTheme.typography.labelLarge, color = RoadColors.TextPrimary)
    }
}
