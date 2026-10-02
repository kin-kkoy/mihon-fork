package eu.kanade.presentation.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.library.OtherSideColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * OtherSide crossover ripple, packaged for reuse: snapshot the old-themed frame, flip the mode,
 * then reveal the new theme inside a circle growing from [origin] (same effect as the Library).
 */
@Stable
class OtherSideReveal internal constructor(
    internal val layer: GraphicsLayer,
    private val scope: CoroutineScope,
) {
    /** Center of the yin-yang button, in window coordinates. */
    var origin by mutableStateOf<Offset?>(null)
    internal var snapshot by mutableStateOf<ImageBitmap?>(null)
    internal val progress = Animatable(0f)

    fun crossover(toggle: () -> Unit) {
        scope.launch {
            val old = runCatching { layer.toImageBitmap() }.getOrNull()
            toggle()
            snapshot = old
            if (old != null) {
                progress.snapTo(0f)
                progress.animateTo(1f, animationSpec = tween(durationMillis = 550))
            }
            snapshot = null
        }
    }
}

@Composable
fun rememberOtherSideReveal(): OtherSideReveal {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    return remember(layer) { OtherSideReveal(layer, scope) }
}

/** Hosts [content] in the normal or OtherSide theme and draws the crossover ripple on top. */
@Composable
fun OtherSideRevealBox(
    reveal: OtherSideReveal,
    otherSide: Boolean,
    content: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    reveal.layer.record { this@drawWithContent.drawContent() }
                    drawLayer(reveal.layer)
                },
        ) {
            MaterialTheme(
                colorScheme = if (otherSide) OtherSideColorScheme else MaterialTheme.colorScheme,
                content = content,
            )
        }

        val snapshot = reveal.snapshot
        if (snapshot != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.minDimension <= 0f) return@Canvas
                val origin = reveal.origin ?: Offset(size.width, 0f)
                val maxRadius = listOf(
                    Offset(0f, 0f),
                    Offset(size.width, 0f),
                    Offset(0f, size.height),
                    Offset(size.width, size.height),
                ).maxOf { (it - origin).getDistance() }
                val radius = reveal.progress.value * maxRadius
                val path = Path().apply {
                    addRect(Rect(Offset.Zero, size))
                    addOval(Rect(center = origin, radius = radius))
                    fillType = PathFillType.EvenOdd
                }
                clipPath(path) { drawImage(image = snapshot) }
                val ringAlpha = (0.35f * (1f - reveal.progress.value)).coerceAtLeast(0f)
                if (ringAlpha > 0f && radius > 0f) {
                    drawCircle(
                        color = Color.White.copy(alpha = ringAlpha),
                        radius = radius,
                        center = origin,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
        }
    }
}
