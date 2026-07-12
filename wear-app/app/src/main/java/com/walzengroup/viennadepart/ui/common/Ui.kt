package com.walzengroup.viennadepart.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import android.os.SystemClock
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Text
import kotlin.math.roundToInt

val MutedText = Color(0xFF8B8B92)
val RowSurface = Color(0xFF1E1E25)

/**
 * Runs [loader], showing a spinner while it runs and an error + retry on failure.
 * [loadingLabel] is shown under the spinner to say what's happening (e.g. "Locating…").
 * When [refreshMs] > 0 the loader re-runs on that interval, replacing the content in
 * place (no spinner) and keeping the last good value if a refresh fails.
 * [key] re-runs the loader when it changes; the previous content stays on screen
 * during the reload (no spinner) unless nothing has loaded yet — so stepping between
 * inputs (e.g. crown = next stop) stays smooth.
 * [initial] seeds the content so the spinner is skipped when the caller already has a
 * value (e.g. this composable was just recreated and we don't want to re-flash).
 * When [refreshOnResumeAfterMs] > 0, returning to the foreground (ON_RESUME) with the
 * last successful load older than that many millis forces an immediate reload — the
 * periodic loop is frozen while the screen is off, so this avoids showing stale data.
 */
@Composable
fun <T> Loadable(
    loader: suspend () -> T,
    loadingLabel: String? = null,
    refreshMs: Long = 0,
    key: Any? = Unit,
    initial: T? = null,
    refreshOnResumeAfterMs: Long = 0,
    content: @Composable (T) -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<Result<T>?>(initial?.let { Result.success(it) }) }
    // Monotonic timestamp of the last successful load (0 = none yet); drives resume refresh.
    var lastSuccessAt by remember { mutableLongStateOf(0L) }
    // refreshMs is a key so toggling live refresh (e.g. only the on-screen stop) restarts the loop.
    LaunchedEffect(attempt, key, refreshMs) {
        val result = runCatching { loader() }
        // Replace on success; on failure keep prior content unless there's none yet.
        if (result.isSuccess || state == null) state = result
        if (result.isSuccess) lastSuccessAt = SystemClock.elapsedRealtime()
        if (refreshMs > 0) {
            while (true) {
                delay(refreshMs)
                val next = runCatching { loader() }
                if (next.isSuccess) {
                    state = next // silent refresh; keep old on failure
                    lastSuccessAt = SystemClock.elapsedRealtime()
                }
            }
        }
    }
    if (refreshOnResumeAfterMs > 0) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                // On resume, if the last good load is stale, reload now. Bumping `attempt`
                // reuses the effect above: immediate load, then restart the periodic loop.
                // addObserver replays ON_RESUME at registration; the >0 guard skips the
                // first entry, which the LaunchedEffect already loads.
                if (event == Lifecycle.Event.ON_RESUME &&
                    lastSuccessAt > 0L &&
                    SystemClock.elapsedRealtime() - lastSuccessAt >= refreshOnResumeAfterMs
                ) {
                    attempt++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
    when (val s = state) {
        null -> CenteredProgress(loadingLabel)
        else -> s.fold(
            onSuccess = { content(it) },
            onFailure = { ErrorView(it.message ?: "Something went wrong") { attempt++ } },
        )
    }
}

@Composable
fun CenteredProgress(label: String? = null) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (label != null) {
            Spacer(Modifier.height(10.dp))
            Text(label, color = MutedText, fontSize = 12.sp)
        }
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = MutedText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Chip(onClick = onRetry, label = { Text("Retry") }, colors = ChipDefaults.primaryChipColors())
    }
}

@Composable
fun Glyph(symbol: String, color: Color) {
    Text(symbol, color = color, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
}

@Composable
fun SquareBadge(line: String, color: Color, size: Int = 22) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape((size / 3).dp))
            .background(color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(line, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.6f).sp)
    }
}

fun formatDistance(meters: Float): String =
    if (meters < 1000) "${meters.roundToInt()} m" else String.format("%.1f km", meters / 1000)

private val ClearBg = Color(0xFFCFD2EE)
private val ClearText = Color(0xFF5B5B66)

private val DeleteRed = Color(0xFFCF4B3B)

/**
 * Full-screen confirm overlay for deleting one recent: "Remove <name>?" with a red Remove
 * and a Cancel. Tapping the dimmed scrim also cancels. Rendered on top of the page when a
 * long-press has selected an item.
 */
@Composable
fun ConfirmDeleteOverlay(name: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Remove", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                name,
                color = MutedText,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Chip(
                onClick = onConfirm,
                label = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Remove", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                },
                colors = ChipDefaults.chipColors(backgroundColor = DeleteRed, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(0.82f),
            )
            Spacer(Modifier.height(6.dp))
            Chip(
                onClick = onCancel,
                label = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Cancel")
                    }
                },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(0.82f),
            )
        }
    }
}

/** The native Material "Clear all" pill (same CompactChip the system uses for notifications). */
@Composable
fun ClearAllButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CompactChip(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(0.42f),
            colors = ChipDefaults.chipColors(backgroundColor = ClearBg, contentColor = ClearText),
            label = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Clear all", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            },
        )
    }
}
