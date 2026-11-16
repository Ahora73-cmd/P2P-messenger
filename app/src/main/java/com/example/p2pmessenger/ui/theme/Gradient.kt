package com.example.p2pmessenger.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Живой фон в стиле Gemini: глубокая тёмная база и несколько больших мягких
 * цветовых облаков, которые медленно движутся по разным орбитам.
 *
 * Фаза читается только внутри drawBehind. Благодаря этому анимация не запускает
 * рекомпозицию всего экрана и не создаёт ANR на слабом или software GPU.
 */
object LiveGradient {

    @Composable
    fun phase(label: String = "gradientPhase"): State<Float> {
        val transition = rememberInfiniteTransition(label = label)
        return transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 16000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = label,
        )
    }

    fun orb(dark: Boolean, phase: Float): Brush {
        val strength = 0.68f + (phase * 0.32f)
        return Brush.radialGradient(
            if (dark) {
                listOf(
                    Color(0xFFE98A62).copy(alpha = 0.55f * strength),
                    Color(0xFF9A6BD1).copy(alpha = 0.26f * strength),
                    Color.Transparent,
                )
            } else {
                listOf(
                    Color(0xFFF3A26F).copy(alpha = 0.78f * strength),
                    Color(0xFFC58BD8).copy(alpha = 0.34f * strength),
                    Color.Transparent,
                )
            },
        )
    }

    /** Статичный акцент: кнопки, иконки, drag handle. */
    fun accent(dark: Boolean): Brush = Brush.linearGradient(
        if (dark) {
            listOf(Color(0xFFE98A62), Color(0xFFD2647F), Color(0xFF9A6BD1))
        } else {
            listOf(Color(0xFFEF8354), Color(0xFFD96A86), Color(0xFF8E6BC7))
        },
    )

    fun disabledAccent(): Brush = Brush.linearGradient(
        listOf(Color(0xFFB9ADB5), Color(0xFF9E949F)),
    )

    fun bubbleMine(dark: Boolean): Brush = Brush.linearGradient(
        if (dark) {
            listOf(Color(0xFF3C5A50), Color(0xFF37506B))
        } else {
            listOf(Color(0xFFD6ECCB), Color(0xFFC9E3E8))
        },
    )

    fun bubbleTheir(dark: Boolean): Brush = Brush.linearGradient(
        if (dark) {
            listOf(Color(0xFF332D38), Color(0xFF2C2A36))
        } else {
            listOf(Color(0xFFFFFFFF), Color(0xFFFBF4EC))
        },
    )

    fun sheet(dark: Boolean): Brush = Brush.linearGradient(
        if (dark) {
            listOf(Color(0xFF241F2C), Color(0xFF1E2A2B), Color(0xFF241F2C))
        } else {
            listOf(Color(0xFFFDF4EA), Color(0xFFF4E6DC), Color(0xFFEDF2E4))
        },
    )
}

/** Анимированный фон: движение выполняется только в draw-фазе. */
fun Modifier.liveBackground(phase: State<Float>, dark: Boolean): Modifier =
    drawBehind {
        drawGeminiBackground(phase.value, dark)
    }

private fun DrawScope.drawGeminiBackground(phase: Float, dark: Boolean) {
    val width = size.width
    val height = size.height

    if (!dark) {
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFFFDF3E7), Color(0xFFFBE0D2), Color(0xFFE6EFD9), Color(0xFFFDF3E7)),
                start = Offset(phase * width - width * 0.25f, 0f),
                end = Offset(phase * width + width, height),
            ),
        )
        return
    }

    // Глубокий фон, поверх которого смешиваются большие radial gradients.
    drawRect(Color(0xFF11121D))

    val time = phase * (2.0 * PI)
    val radius = maxOf(width, height) * 0.72f

    // Каждое облако движется по своей орбите: медленно, асинхронно и без
    // резких переходов. Слой прозрачности создаёт «туманное» смешивание.
    val clouds = listOf(
        Cloud(
            color = Color(0xFF6A4FD0),
            x = 0.18f + 0.10f * cos(time).toFloat(),
            y = 0.18f + 0.15f * sin(time * 0.82).toFloat(),
            alpha = 0.58f,
        ),
        Cloud(
            color = Color(0xFFDD5D82),
            x = 0.82f + 0.13f * sin(time * 0.72 + 1.2).toFloat(),
            y = 0.25f + 0.18f * cos(time * 0.58).toFloat(),
            alpha = 0.48f,
        ),
        Cloud(
            color = Color(0xFF238B91),
            x = 0.26f + 0.16f * sin(time * 0.52 + 2.4).toFloat(),
            y = 0.82f + 0.11f * cos(time * 0.74).toFloat(),
            alpha = 0.42f,
        ),
        Cloud(
            color = Color(0xFFF08B59),
            x = 0.78f + 0.12f * cos(time * 0.64 + 3.0).toFloat(),
            y = 0.78f + 0.15f * sin(time * 0.48).toFloat(),
            alpha = 0.36f,
        ),
    )

    clouds.forEach { cloud ->
        val center = Offset(width * cloud.x, height * cloud.y)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    cloud.color.copy(alpha = cloud.alpha),
                    cloud.color.copy(alpha = cloud.alpha * 0.34f),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }

    // Тонкая полупрозрачная вуаль сглаживает цветовые пересечения.
    drawRect(Color(0xFF0E1020).copy(alpha = 0.13f))
}

private data class Cloud(
    val color: Color,
    val x: Float,
    val y: Float,
    val alpha: Float,
)

/** Пульсирующее свечение за центральной иконкой. */
fun Modifier.liveOrb(phase: State<Float>, dark: Boolean): Modifier =
    drawBehind {
        val radius = size.minDimension / 2f
        drawCircle(
            brush = LiveGradient.orb(dark, phase.value),
            radius = radius,
        )
    }
