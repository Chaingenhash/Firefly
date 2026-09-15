package dev.chaingenhash.firefly.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A battery drawn as a horizontal pill, filled to [level], with the fill edge carrying a
 * soft glow — the same motif as the launcher icon.
 *
 * The bar is decorative: the level is already announced by the heading beside it, so the
 * gauge exposes a single combined description rather than competing with it.
 */
@Composable
fun BatteryGauge(
    level: Int,
    plugged: Boolean,
    accent: Color,
    track: Color,
    outline: Color,
    modifier: Modifier = Modifier,
) {
    val fraction by animateFloatAsState(
        targetValue = (level.coerceIn(0, 100)) / 100f,
        label = "batteryFill",
    )
    val description = if (plugged) "Battery at $level percent, charging" else "Battery at $level percent"

    Box(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .semantics { contentDescription = description },
    ) {
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val capWidth = size.width * 0.022f
            val bodyWidth = size.width - capWidth - 4.dp.toPx()
            val radius = CornerRadius(size.height / 2.6f, size.height / 2.6f)
            val inset = 3.dp.toPx()

            // Track
            drawRoundRect(
                color = track,
                size = Size(bodyWidth, size.height),
                cornerRadius = radius,
            )
            drawRoundRect(
                color = outline,
                size = Size(bodyWidth, size.height),
                cornerRadius = radius,
                style = Stroke(width = 1.5.dp.toPx()),
            )

            // Terminal cap
            drawRoundRect(
                color = outline,
                topLeft = Offset(bodyWidth + 4.dp.toPx(), size.height * 0.3f),
                size = Size(capWidth, size.height * 0.4f),
                cornerRadius = CornerRadius(capWidth / 2, capWidth / 2),
            )

            // Fill
            val fillWidth = (bodyWidth - inset * 2) * fraction
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(inset, inset),
                    size = Size(fillWidth, size.height - inset * 2),
                    cornerRadius = CornerRadius(radius.x * 0.8f, radius.y * 0.8f),
                )

                // The glow at the charge edge — the firefly sitting on the fill line.
                val edge = inset + fillWidth
                listOf(14f to 0.20f, 9f to 0.30f, 4.5f to 0.85f).forEach { (r: Float, alpha: Float) ->
                    drawCircle(
                        color = accent.copy(alpha = alpha),
                        radius = r.dp.toPx() / 2,
                        center = Offset(edge, size.height / 2),
                    )
                }
            }
        }
    }
}
