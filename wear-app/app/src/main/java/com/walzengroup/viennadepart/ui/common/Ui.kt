package com.walzengroup.viennadepart.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.wear.compose.material.Text
import kotlin.math.roundToInt

val MutedText = Color(0xFF8B8B92)
val RowSurface = Color(0xFF1E1E25)

/** Runs [loader], showing a spinner while it runs and an error + retry on failure. */
@Composable
fun <T> Loadable(loader: suspend () -> T, content: @Composable (T) -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<Result<T>?>(initialValue = null, attempt) {
        value = null
        value = runCatching { loader() }
    }
    when (val s = state) {
        null -> CenteredProgress()
        else -> s.fold(
            onSuccess = { content(it) },
            onFailure = { ErrorView(it.message ?: "Something went wrong") { attempt++ } },
        )
    }
}

@Composable
fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
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
