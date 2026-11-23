package dev.roadloader.client.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.roadloader.client.ui.theme.RoadColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slow aurora wash behind every screen. Four large radial gradients drift on
 * separate cycles so the background never looks static, without costing a
 * recomposition per frame.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    accentStart: Color = RoadColors.Violet,
    accentEnd: Color = RoadColors.Cyan,
    intensity: Float = 1f,
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 34_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "drift",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0xFF0B1024),
                        Color(0xFF070A18),
                        Color(0xFF05070F),
                    ),
                ),
            )
            val w = size.width
            val h = size.height
            fun blob(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to color.copy(alpha = alpha),
                            0.45f to color.copy(alpha = alpha * 0.45f),
                            1f to Color.Transparent,
                        ),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2f, radius * 2f),
                )
            }
            blob(
                cx = w * (0.22f + 0.06f * cos(drift)),
                cy = h * (0.16f + 0.05f * sin(drift)),
                radius = w * 0.62f * pulse,
                color = accentStart,
                alpha = 0.62f * intensity,
            )
            blob(
                cx = w * (0.86f + 0.05f * sin(drift * 0.8f)),
                cy = h * (0.30f + 0.06f * cos(drift * 0.7f)),
                radius = w * 0.55f * pulse,
                color = accentEnd,
                alpha = 0.50f * intensity,
            )
            blob(
                cx = w * (0.55f + 0.08f * sin(drift * 0.6f + 1.2f)),
                cy = h * (0.88f + 0.04f * cos(drift * 0.9f)),
                radius = w * 0.70f * pulse,
                color = RoadColors.Mint,
                alpha = 0.34f * intensity,
            )
            blob(
                cx = w * (0.05f + 0.04f * cos(drift * 1.3f)),
                cy = h * (0.72f + 0.05f * sin(drift * 1.1f)),
                radius = w * 0.42f * pulse,
                color = RoadColors.VioletDeep,
                alpha = 0.42f * intensity,
            )
        }
    }
}

/** Small helper so screens can use a shared vertical spacing rhythm. */
val ScreenPadding = 20.dp
