package dev.roadloader.client.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.roadloader.client.data.formatBytes
import dev.roadloader.client.data.formatPlayTime
import dev.roadloader.client.model.Instance
import dev.roadloader.client.ui.components.ActionTile
import dev.roadloader.client.ui.components.BrandHeader
import dev.roadloader.client.ui.components.GlassCard
import dev.roadloader.client.ui.components.GradientPillButton
import dev.roadloader.client.ui.components.InstanceIcon
import dev.roadloader.client.ui.components.SectionHeader
import dev.roadloader.client.ui.components.StatChip
import dev.roadloader.client.ui.theme.LocalAccent
import dev.roadloader.client.ui.theme.RoadColors
import dev.roadloader.client.vm.ClientTab
import dev.roadloader.client.vm.ClientUiState

@Composable
fun HomeScreen(
    state: ClientUiState,
    onLaunch: () -> Unit,
    onImport: () -> Unit,
    onNavigate: (ClientTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.selected
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            BrandHeader(
                title = "RoadClient",
                subtitle = "32-bit companion for Minecraft",
                trailing = {
                    AccountChip(
                        name = state.activeAccount?.displayName,
                        onClick = { onNavigate(ClientTab.Account) },
                    )
                },
            )
        }

        item {
            if (selected != null) {
                HeroCard(
                    instance = selected,
                    modulesOn = state.settings.modules.count { it.value },
                    heapMb = state.settings.heapLimitMb,
                    onLaunch = onLaunch,
                )
            } else {
                EmptyHero(onImport = onImport)
            }
        }

        item { SectionHeader(title = "Quick actions") }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(
                    icon = Icons.Rounded.FileDownload,
                    title = "Import APK",
                    subtitle = "Add a 1.16 – 1.17 build",
                    onClick = onImport,
                    modifier = Modifier.weight(1f),
                )
                ActionTile(
                    icon = Icons.Rounded.Tune,
                    title = "Mod menu",
                    subtitle = "HUD and engine toggles",
                    onClick = { onNavigate(ClientTab.Mods) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionTile(
                    icon = Icons.Rounded.Person,
                    title = "Account",
                    subtitle = "Microsoft sign-in",
                    onClick = { onNavigate(ClientTab.Account) },
                    modifier = Modifier.weight(1f),
                )
                ActionTile(
                    icon = Icons.Rounded.Settings,
                    title = "Settings",
                    subtitle = "Heap, audio, accent",
                    onClick = { onNavigate(ClientTab.Settings) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { SectionHeader(title = "How the bridge works") }
        item {
            GlassCard(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
                PipelineStep("01", "Resolve", "Signature scan of the 32-bit libminecraftpe.so")
                PipelineStep("02", "Hook", "Dobby trampolines on swapBuffers and input")
                PipelineStep("03", "Bridge", "Shared-memory ring buffers for frames and touches")
                PipelineStep("04", "Render", "GLES 3 composite with the Dear ImGui mod menu")
            }
        }

        if (state.instances.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Your worlds",
                    action = "See all",
                    onAction = { onNavigate(ClientTab.Worlds) },
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.instances, key = { it.id }) { instance ->
                        MiniWorldCard(
                            instance = instance,
                            selected = instance.id == state.selectedId,
                            onClick = { onNavigate(ClientTab.Worlds) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineStep(index: String, title: String, detail: String) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.start.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index,
                style = MaterialTheme.typography.labelSmall,
                color = accent.end,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = RoadColors.TextPrimary)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = RoadColors.TextMuted)
        }
    }
}

@Composable
private fun AccountChip(name: String?, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, RoadColors.GlassBorder, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(accent.start, accent.end))),
            contentAlignment = Alignment.Center,
        ) {
            if (name == null) {
                Icon(
                    Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = name ?: "Sign in",
            style = MaterialTheme.typography.labelLarge,
            color = RoadColors.TextPrimary,
            maxLines = 1,
        )
    }
}

@Composable
private fun HeroCard(
    instance: Instance,
    modulesOn: Int,
    heapMb: Int,
    onLaunch: () -> Unit,
) {
    val accent = LocalAccent.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        initialValue = 0.985f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "glow",
    )
    GlassCard(
        glow = accent.start,
        contentPadding = PaddingValues(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InstanceIcon(instance = instance, size = 66.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = instance.displayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = RoadColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatChip(label = instance.shortVersion, value = "", accent = accent.start)
                    StatChip(
                        label = if (instance.isArm32) "arm32" else "arm64",
                        value = "",
                        accent = if (instance.isArm32) RoadColors.Mint else RoadColors.Amber,
                    )
                    StatChip(label = formatBytes(instance.sizeBytes), value = "")
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        GradientPillButton(
            text = "Launch world",
            icon = Icons.Rounded.PlayArrow,
            onClick = onLaunch,
            modifier = Modifier
                .fillMaxWidth()
                .scale(glow),
            height = 58.dp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip(label = "ENGINE", value = "32-bit", icon = Icons.Rounded.Bolt)
            StatChip(label = "HEAP", value = "$heapMb MB")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$modulesOn of 9 HUD modules enabled",
            style = MaterialTheme.typography.bodySmall,
            color = RoadColors.TextMuted,
        )
        if (instance.lastPlayedAt != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = instance.playTimeMs.let { if (it <= 0) "Not played yet" else "Played ${formatPlayTime(it)}" },
                style = MaterialTheme.typography.bodySmall,
                color = RoadColors.TextMuted,
            )
        }
    }
}

@Composable
private fun EmptyHero(onImport: () -> Unit) {
    val accent = LocalAccent.current
    GlassCard(contentPadding = PaddingValues(22.dp), glow = accent.end) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(accent.start, accent.end))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.FileDownload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "No worlds yet",
                    style = MaterialTheme.typography.headlineMedium,
                    color = RoadColors.TextPrimary,
                )
                Text(
                    "Import a 32-bit Minecraft APK to start",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RoadColors.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        GradientPillButton(
            text = "Import APK",
            icon = Icons.Rounded.FileDownload,
            onClick = onImport,
            modifier = Modifier.fillMaxWidth(),
            height = 56.dp,
        )
    }
}

@Composable
private fun MiniWorldCard(
    instance: Instance,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = RoadColors.accents[((instance.accentIndex % 5) + 5) % 5]
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = if (selected) 0.12f else 0.06f))
            .border(
                1.dp,
                if (selected) accent.start.copy(alpha = 0.6f) else RoadColors.GlassBorder,
                RoundedCornerShape(22.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        InstanceIcon(instance = instance, size = 46.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = instance.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = RoadColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = instance.shortVersion,
            style = MaterialTheme.typography.bodySmall,
            color = RoadColors.TextMuted,
        )
    }
}
