package eu.kanade.presentation.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.security.OtherSideLock
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PIN_LENGTH = 4

/**
 * Mode of the [OtherSidePinDialog].
 */
enum class OtherSidePinMode {
    /** Enter the existing PIN once to unlock. */
    UNLOCK,

    /** Enter a new PIN twice to confirm, then report it via [OtherSidePinDialog]'s onPinSet. */
    SET,
}

/**
 * Reusable PIN entry surface for the OtherSide "Suppress" lock.
 *
 * In [OtherSidePinMode.UNLOCK] it verifies against [OtherSideLock] and calls [onUnlocked] on
 * success. In [OtherSidePinMode.SET] it asks for a new PIN twice and reports the confirmed raw
 * PIN via [onPinSet] (the caller is responsible for hashing/storing it).
 */
@Composable
fun OtherSidePinDialog(
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit,
    mode: OtherSidePinMode = OtherSidePinMode.UNLOCK,
    onPinSet: (String) -> Unit = {},
    onMaxAttempts: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var entered by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    // Holds the first entry while confirming a new PIN in SET mode.
    var firstEntry by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }

    fun triggerShake() {
        scope.launch {
            val keyframes = floatArrayOf(0f, -16f, 16f, -12f, 12f, -6f, 6f, 0f)
            for (target in keyframes) {
                shakeOffset.animateTo(target, animationSpec = tween(durationMillis = 40))
            }
        }
    }

    fun submit(pin: String) {
        when (mode) {
            OtherSidePinMode.UNLOCK -> {
                if (OtherSideLock.tryUnlock(pin)) {
                    onUnlocked()
                } else {
                    val remaining = OtherSideLock.onFailedAttempt()
                    entered = ""
                    triggerShake()
                    if (remaining == 0) {
                        onMaxAttempts()
                    } else {
                        message = "Incorrect PIN. $remaining attempt(s) left."
                    }
                }
            }
            OtherSidePinMode.SET -> {
                val first = firstEntry
                if (first == null) {
                    firstEntry = pin
                    entered = ""
                    message = "Re-enter the new PIN to confirm."
                } else if (first == pin) {
                    onPinSet(pin)
                } else {
                    firstEntry = null
                    entered = ""
                    triggerShake()
                    message = "PINs did not match. Try again."
                }
            }
        }
    }

    fun onDigit(digit: Int) {
        if (entered.length >= PIN_LENGTH) return
        entered += digit.toString()
        if (entered.length == PIN_LENGTH) {
            val pin = entered
            submit(pin)
        }
    }

    fun onBackspace() {
        if (entered.isNotEmpty()) {
            entered = entered.dropLast(1)
        }
    }

    val title = when (mode) {
        OtherSidePinMode.UNLOCK -> "OtherSide is Suppressed"
        OtherSidePinMode.SET -> if (firstEntry == null) "Set OtherSide PIN" else "Confirm OtherSide PIN"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_otherside),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(title, textAlign = TextAlign.Center)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 4-dot indicator with shake.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                ) {
                    repeat(PIN_LENGTH) { index ->
                        val filled = index < entered.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .then(
                                    if (filled) {
                                        Modifier.background(MaterialTheme.colorScheme.primary)
                                    } else {
                                        Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                                    },
                                ),
                        )
                    }
                }

                message?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                Keypad(
                    onDigit = ::onDigit,
                    onBackspace = ::onBackspace,
                )
            }
        },
    )
}

@Composable
private fun Keypad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9),
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                row.forEach { digit ->
                    KeypadButton(
                        label = digit.toString(),
                        onClick = { onDigit(digit) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Empty spacer to keep 0 centered.
            Spacer(Modifier.weight(1f))
            KeypadButton(
                label = "0",
                onClick = { onDigit(0) },
                modifier = Modifier.weight(1f),
            )
            KeypadButton(
                label = "⌫",
                onClick = onBackspace,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.aspectRatio(1.6f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
