package dev.roadloader.client.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.roadloader.client.ui.theme.LocalAccent
import dev.roadloader.client.ui.theme.RoadColors
import dev.roadloader.client.vm.ClientTab

private data class TabItem(val tab: ClientTab, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem(ClientTab.Home, "Home", Icons.Rounded.Home),
    TabItem(ClientTab.Worlds, "Worlds", Icons.Rounded.SportsEsports),
    TabItem(ClientTab.Mods, "Mods", Icons.Rounded.Tune),
    TabItem(ClientTab.Account, "Account", Icons.Rounded.Person),
    TabItem(ClientTab.Settings, "Settings", Icons.Rounded.Settings),
)

/** Floating glass navigation bar with a gradient highlight on the active tab. */
@Composable
fun BottomBar(
    selected: ClientTab,
    onSelect: (ClientTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalAccent.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(Color(0xFA0A1020))
            .border(1.dp, RoadColors.GlassBorderStrong, RoundedCornerShape(30.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { item ->
            val active = item.tab == selected
            val width by animateDpAsState(
                targetValue = if (active) 104.dp else 46.dp,
                animationSpec = spring(dampingRatio = 0.7f),
                label = "tabWidth",
            )
            val tint by animateColorAsState(
                targetValue = if (active) Color.White else RoadColors.TextMuted,
                label = "tabTint",
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(
                        if (active) {
                            Modifier.background(Brush.horizontalGradient(listOf(accent.start, accent.end)))
                        } else {
                            Modifier
                        },
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(item.tab) }
                    .height(44.dp)
                    .width(width),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                    if (active) {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Brand row: gradient mark, product name and an optional trailing slot. */
@Composable
fun BrandHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = LocalAccent.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(accent.start, accent.end)))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = RoadColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = RoadColors.TextMuted)
        }
        trailing?.invoke()
    }
}
