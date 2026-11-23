package dev.roadloader.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.roadloader.client.model.Instance
import dev.roadloader.client.ui.theme.RoadColors

/** Gradient tile standing in for an instance icon, tinted per package. */
@Composable
fun InstanceIcon(
    instance: Instance,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val accent = RoadColors.accents[instance.accentIndex.mod(RoadColors.accents.size)]
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.30f))
            .background(Brush.linearGradient(listOf(accent.start, accent.end)))
            .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(size * 0.30f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    ),
                ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = instance.shortVersion.split('.').take(2).joinToString("."),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = (size.value * 0.26f).sp),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = if (instance.isArm32) "32-BIT" else "64-BIT",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = (size.value * 0.13f).sp),
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
            )
        }
    }
}

/** Small status dot used by the engine banner. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}
