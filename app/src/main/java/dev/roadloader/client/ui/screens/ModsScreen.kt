package dev.roadloader.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.roadloader.client.data.ClientSettings
import dev.roadloader.client.ui.components.AccentSlider
import dev.roadloader.client.ui.components.BrandHeader
import dev.roadloader.client.ui.components.GlassCard
import dev.roadloader.client.ui.components.GlassDivider
import dev.roadloader.client.ui.components.SectionHeader
import dev.roadloader.client.ui.components.ToggleRow
import dev.roadloader.client.ui.theme.LocalAccent
import dev.roadloader.client.ui.theme.RoadColors

private data class ModuleInfo(val id: String, val title: String, val subtitle: String)

private val modules = listOf(
    ModuleInfo("watermark", "Watermark", "Instance name, version and accent mark"),
    ModuleInfo("fps", "FPS", "Frame rate and the sparkline graph"),
    ModuleInfo("ping", "Ping", "Companion heartbeat age in milliseconds"),
    ModuleInfo("memory", "Heap", "32-bit heap usage against the cap"),
    ModuleInfo("cps", "CPS", "Input events forwarded per second"),
    ModuleInfo("session", "Session", "Time spent in the world"),
    ModuleInfo("render", "Render", "Average render cost from the engine"),
    ModuleInfo("input", "Input", "Pushed and consumed input events"),
    ModuleInfo("engine", "Engine", "Companion state and safe-mode flag"),
)

@Composable
fun ModsScreen(
    settings: ClientSettings,
    onToggleModule: (String, Boolean) -> Unit,
    onUpdate: ((ClientSettings) -> ClientSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            BrandHeader(
                title = "Mod menu",
                subtitle = "Mirrored by the in-game Dear ImGui menu",
            )
        }

        item {
            GlassCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                ToggleRow(
                    title = "BetterRenderDragon",
                    subtitle = "Post-process shaders on the 32-bit pipeline",
                    checked = settings.shaders,
                    onCheckedChange = { value -> onUpdate { it.copy(shaders = value) } },
                )
                GlassDivider()
                ToggleRow(
                    title = "Safe Mode",
                    subtitle = "Skip Rust fast paths and run the vanilla path",
                    checked = settings.safeMode,
                    onCheckedChange = { value -> onUpdate { it.copy(safeMode = value) } },
                )
                GlassDivider()
                ToggleRow(
                    title = "Stream audio",
                    subtitle = "Play the 32-bit audio through the 64-bit mixer",
                    checked = settings.streamAudio,
                    onCheckedChange = { value -> onUpdate { it.copy(streamAudio = value) } },
                )
            }
        }

        item { SectionHeader(title = "HUD modules") }

        item {
            GlassCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                modules.forEachIndexed { index, module ->
                    ToggleRow(
                        title = module.title,
                        subtitle = module.subtitle,
                        checked = settings.modules[module.id] ?: false,
                        onCheckedChange = { value -> onToggleModule(module.id, value) },
                    )
                    if (index != modules.lastIndex) GlassDivider()
                }
            }
        }

        item { SectionHeader(title = "Engine") }

        item {
            GlassCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
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
            }
        }

        item { SectionHeader(title = "Appearance") }

        item {
            GlassCard(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = "Accent",
                    style = MaterialTheme.typography.titleMedium,
                    color = RoadColors.TextPrimary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    RoadColors.accents.forEachIndexed { index, swatch ->
                        val selected = settings.accentIndex == index
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(swatch.start, swatch.end)))
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.25f),
                                        shape = CircleShape,
                                    )
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onUpdate { it.copy(accentIndex = index) } },
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = swatch.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) RoadColors.TextPrimary else RoadColors.TextMuted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                AccentSlider(
                    label = "HUD scale",
                    value = settings.hudScale,
                    valueText = String.format(java.util.Locale.US, "%.2fx", settings.hudScale),
                    range = 0.75f..1.55f,
                    onChange = { value -> onUpdate { it.copy(hudScale = value) } },
                )
                ToggleRow(
                    title = "Glass backdrop",
                    subtitle = "Blur the frame behind the menu panels",
                    checked = settings.menuBackdropBlur,
                    onCheckedChange = { value -> onUpdate { it.copy(menuBackdropBlur = value) } },
                    accent = accent.start,
                )
            }
        }

        item {
            GlassCard(contentPadding = PaddingValues(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accent.end),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Open the menu in game with the floating button, top right.",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoadColors.TextMuted,
                    )
                }
            }
        }
    }
}
