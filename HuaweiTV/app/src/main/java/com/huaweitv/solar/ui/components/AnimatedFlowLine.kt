package com.huaweitv.solar.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.abs

enum class FlowDirection {
    FORWARD,
    BACKWARD,
    IDLE
}

/**
 * Animated Canvas drawing a connecting energy flow line with pulsating particles.
 * Speed and direction adapt dynamically to the energy flow direction and wattage.
 */
@Composable
fun AnimatedFlowLine(
    modifier: Modifier = Modifier,
    startOffset: Offset,
    endOffset: Offset,
    color: Color,
    powerW: Int,
    direction: FlowDirection = FlowDirection.FORWARD
) {
    val isFlowing = direction != FlowDirection.IDLE && abs(powerW) > 15

    // Adjust animation speed: higher power = faster pulse (between 600ms and 2400ms)
    val durationMs = when {
        !isFlowing -> 3000
        abs(powerW) > 3000 -> 700
        abs(powerW) > 1500 -> 1100
        abs(powerW) > 500 -> 1600
        else -> 2200
    }

    val infiniteTransition = rememberInfiniteTransition(label = "flow_particles")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_phase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val dx = endOffset.x - startOffset.x
        val dy = endOffset.y - startOffset.y

        // Base guide line (dim dashed or solid)
        val baseLineColor = if (isFlowing) color.copy(alpha = 0.35f) else Color.DarkGray.copy(alpha = 0.25f)
        drawLine(
            color = baseLineColor,
            start = startOffset,
            end = endOffset,
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
        )

        // Draw animated traveling pulses when active
        if (isFlowing) {
            val numParticles = 3
            for (i in 0 until numParticles) {
                val offsetFraction = (progress + (i.toFloat() / numParticles)) % 1f
                val effectiveFraction = if (direction == FlowDirection.BACKWARD) {
                    1f - offsetFraction
                } else {
                    offsetFraction
                }

                val particleX = startOffset.x + dx * effectiveFraction
                val particleY = startOffset.y + dy * effectiveFraction

                // Glow halo
                drawCircle(
                    color = color.copy(alpha = 0.3f),
                    radius = 9.dp.toPx(),
                    center = Offset(particleX, particleY)
                )

                // Bright core
                drawCircle(
                    color = color,
                    radius = 5.dp.toPx(),
                    center = Offset(particleX, particleY)
                )
            }
        }
    }
}
