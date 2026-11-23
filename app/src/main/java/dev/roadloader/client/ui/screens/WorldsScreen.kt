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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.roadloader.client.data.formatBytes
import dev.roadloader.client.data.formatPlayTime
import dev.roadloader.client.model.Instance
import dev.roadloader.client.ui.components.BrandHeader
import dev.roadloader.client.ui.components.InstanceIcon
import dev.roadloader.client.ui.components.SearchField
import dev.roadloader.client.ui.components.StatChip
import dev.roadloader.client.ui.theme.LocalAccent
import dev.roadloader.client.ui.theme.RoadColors

@Composable
fun WorldsScreen(
    instances: List<Instance>,
    selectedId: String?,
    onLaunch: (Instance) -> Unit,
    onSelect: (Instance) -> Unit,
    onRemove: (Instance) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Instance?>(null) }
    val filtered = remember(instances, query) {
        if (query.isBlank()) instances else instances.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                (it.packageName ?: "").contains(query, ignoreCase = true) ||
                it.shortVersion.contains(query, ignoreCase = true)
        }
    }
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                BrandHeader(
                    title = "Worlds",
                    subtitle = "${instances.size} installed · ${instances.count { it.isArm32 }} 32-bit",
                )
            }
            item {
                SearchField(
                    query = query,
                    placeholder = "Search by name, package or version",
                    onQueryChange = { query = it },
                )
            }
            if (filtered.isEmpty()) {
                item { EmptyWorlds(hasQuery = query.isNotBlank(), onImport = onImport) }
            }
            items(filtered, key = { it.id }) { instance ->
                WorldRow(
                    instance = instance,
                    selected = instance.id == selectedId,
                    onLaunch = { onLaunch(instance) },
                    onSelect = { onSelect(instance) },
                    onDelete = { pendingDelete = instance },
                )
            }
        }

        FloatingAddButton(
            onClick = onImport,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 108.dp),
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${target.displayName}?") },
            text = { Text("The imported APK copy is deleted from app storage.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(target)
                    pendingDelete = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
            containerColor = RoadColors.NightElevated,
            titleContentColor = RoadColors.TextPrimary,
            textContentColor = RoadColors.TextSecondary,
        )
    }
}

@Composable
private fun WorldRow(
    instance: Instance,
    selected: Boolean,
    onLaunch: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = if (selected) 0.11f else 0.055f))
            .border(
                1.dp,
                if (selected) accent.start.copy(alpha = 0.55f) else RoadColors.GlassBorder,
                RoundedCornerShape(24.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InstanceIcon(instance = instance, size = 54.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = instance.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = RoadColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatChip(
                    label = "v${instance.shortVersion}",
                    value = "",
                    tint = RoadColors.TextPrimary,
                )
                StatChip(
                    label = formatBytes(instance.sizeBytes),
                    value = "",
                    tint = RoadColors.TextPrimary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${instance.packageName ?: "unknown package"} · ${formatPlayTime(instance.playTimeMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = RoadColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(accent.start, accent.end)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onLaunch,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Launch",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDelete,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "Remove",
                tint = RoadColors.TextMuted,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun EmptyWorlds(hasQuery: Boolean, onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, RoadColors.GlassBorder, RoundedCornerShape(24.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (hasQuery) "Nothing matches that search" else "No worlds imported",
            style = MaterialTheme.typography.titleLarge,
            color = RoadColors.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (hasQuery) {
                "Try a version number such as 1.17"
            } else {
                "Pick a Minecraft APK and RoadClient will inspect its 32-bit engine"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = RoadColors.TextMuted,
        )
        Spacer(Modifier.height(16.dp))
        if (!hasQuery) {
            TextButton(onClick = onImport) { Text("Choose APK") }
        }
    }
}

@Composable
private fun FloatingAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Box(
        modifier = modifier
            .size(60.dp)
            .shadow(20.dp, CircleShape, ambientColor = accent.start, spotColor = accent.start)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent.start, accent.end)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = "Import APK", tint = Color.White, modifier = Modifier.size(28.dp))
    }
}
