package dev.roadloader.client.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.roadloader.client.ui.theme.RoadColors

/**
 * Pill-shaped switch. The track fills with the accent gradient when on and the
 * knob springs across, matching the in-game ImGui toggle so both feel like the
 * same control.
 */
@Composable
fun PillToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = RoadColors.Violet,
    accentEnd: Color = RoadColors.Cyan,
    trackWidth: Dp = 52.dp,
    trackHeight: Dp = 30.dp,
) {
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "progress",
    )
    val knob by animateDpAsState(
        targetValue = if (checked) trackWidth - trackHeight + 3.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        label = "knob",
    )
    val interaction = remember { MutableInteractionSource() }
    val off = Color.White.copy(alpha = 0.10f)
    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        lerp(off, accent, progress),
                        lerp(off, accentEnd, progress),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = lerp(RoadColors.GlassBorder, Color.White.copy(alpha = 0.38f), progress),
                shape = CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .offset(x = knob, y = 3.dp)
                .size(trackHeight - 6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (enabled) 0.96f else 0.45f)),
        )
    }
}

/** A labelled settings row with the pill toggle on the trailing edge. */
@Composable
fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = RoadColors.Violet,
    accentEnd: Color = RoadColors.Cyan,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) RoadColors.TextPrimary else RoadColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RoadColors.TextMuted,
                    maxLines = 2,
                )
            }
        }
        Box(Modifier.padding(start = 14.dp)) {
            PillToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                accent = accent,
                accentEnd = accentEnd,
            )
        }
    }
}
