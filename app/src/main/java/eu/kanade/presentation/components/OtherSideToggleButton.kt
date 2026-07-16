package eu.kanade.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import eu.kanade.tachiyomi.R

/**
 * Reusable per-item OtherSide toggle button.
 *
 * On every change of [active] the icon performs a full 360 degree spin and its tint animates
 * between a muted color (inactive) and the OtherSide accent (active). This is the "spin + color"
 * animation for toggle-selection buttons, distinct from the full-screen crossover ripple.
 */
@Composable
fun OtherSideToggleButton(
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(active) {
        rotation.animateTo(
            targetValue = rotation.value + 360f,
            animationSpec = tween(durationMillis = 450),
        )
    }

    val tint by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "otherSideTint",
    )

    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            painter = painterResource(R.drawable.ic_otherside),
            contentDescription = "Toggle OtherSide",
            tint = tint,
            modifier = Modifier.rotate(rotation.value),
        )
    }
}
