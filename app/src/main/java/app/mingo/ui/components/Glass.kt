package app.mingo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mingo.ui.theme.LocalIsDark
import kotlin.math.cos
import kotlin.math.sin

/** Space the floating navigation bar occupies; scrollable screens pad their content by this much. */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/**
 * Slowly drifting color glows painted behind every screen. They give the glass surfaces
 * something to refract, which is what makes the frosted effect visible.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalIsDark.current
    val glowAlpha = if (dark) 0.30f else 0.45f
    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "aurora-phase",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .drawBehind {
                fun glow(color: Color, cx: Float, cy: Float, radius: Float) = drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
                val w = size.width
                val h = size.height
                glow(colors.primary, w * (0.15f + 0.10f * cos(t)), h * (0.08f + 0.05f * sin(t)), w * 0.9f)
                glow(colors.tertiary, w * (0.95f + 0.08f * sin(t)), h * (0.35f + 0.06f * cos(t)), w * 0.8f)
                glow(colors.secondary, w * (0.30f + 0.12f * sin(t + 1f)), h * (0.85f + 0.04f * cos(t)), w * 0.9f)
            },
        content = content,
    )
}

/** Frosted, translucent surface with a soft light edge, used for cards that sit on the aurora. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tintAlpha: Float = if (LocalIsDark.current) 0.55f else 0.62f,
    borderWidth: Dp = 1.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val edge = if (LocalIsDark.current) Color.White else Color.White
    Box(
        modifier = modifier
            .clip(shape)
            .background(tint.copy(alpha = tintAlpha))
            .border(
                width = borderWidth,
                brush = Brush.verticalGradient(
                    listOf(edge.copy(alpha = if (LocalIsDark.current) 0.18f else 0.75f), edge.copy(alpha = 0.04f)),
                ),
                shape = shape,
            ),
        content = content,
    )
}
