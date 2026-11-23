package dev.roadloader.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.roadloader.client.ui.theme.RoadColors

/**
 * Frosted panel used for every surface in the client.
 *
 * The fill is a vertical white gradient at very low alpha, and the border is a
 * gradient too, which is what sells the "glass" read on top of the aurora.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    fillAlpha: Float = 0.085f,
    borderAlpha: Float = 0.16f,
    borderWidth: Dp = 1.dp,
    glow: Color? = null,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = fillAlpha + 0.045f),
                        Color.White.copy(alpha = fillAlpha * 0.55f),
                    ),
                ),
            )
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = borderAlpha + 0.10f),
                        Color.White.copy(alpha = borderAlpha * 0.35f),
                        if (glow != null) glow.copy(alpha = borderAlpha + 0.12f) else RoadColors.Violet.copy(alpha = borderAlpha * 0.5f),
                    ),
                ),
                shape = shape,
            ),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** Same glass treatment for content that is not a column. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    fillAlpha: Float = 0.09f,
    borderAlpha: Float = 0.18f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = fillAlpha + 0.05f),
                        Color.White.copy(alpha = fillAlpha * 0.5f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = borderAlpha),
                        Color.White.copy(alpha = borderAlpha * 0.3f),
                    ),
                ),
                shape = shape,
            ),
    ) {
        content()
    }
}
