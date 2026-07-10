package com.walzengroup.viennadepart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.walzengroup.viennadepart.DeparturesViewModel
import com.walzengroup.viennadepart.data.DepartureUi
import com.walzengroup.viennadepart.data.DeparturesUi
import com.walzengroup.viennadepart.data.DirectionGroup
import com.walzengroup.viennadepart.ui.theme.ModeColor
import com.walzengroup.viennadepart.ui.theme.ViennaTheme

private val MutedText = Color(0xFF8B8B92)
private val RowSurface = Color(0xFF1E1E25)

@Composable
fun DeparturesScreen(viewModel: DeparturesViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The mode color tints the screen; for Phase 1 the line is known up front.
    val lineColor = when (val s = state) {
        is DeparturesViewModel.State.Success -> ModeColor.forLine(s.ui.line, s.ui.lineType)
        else -> ModeColor.forLine("44", "ptTram")
    }
    val deepTint = lerp(Color.Black, lineColor, 0.22f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to deepTint,
                    0.55f to Color.Black,
                    1f to Color.Black,
                )
            )
    ) {
        when (val s = state) {
            is DeparturesViewModel.State.Loading -> CenteredProgress()
            is DeparturesViewModel.State.Error -> ErrorView(s.message, onRetry = viewModel::load)
            is DeparturesViewModel.State.Success -> DeparturesContent(s.ui, lineColor)
        }
    }
}

@Composable
private fun DeparturesContent(ui: DeparturesUi, lineColor: Color) {
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Header(ui.line, ui.stopName, lineColor) }

            if (ui.groups.isEmpty()) {
                item {
                    Text(
                        text = "No upcoming departures",
                        color = MutedText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            ui.groups.forEach { group ->
                item(key = "head-${group.direction}-${group.towards}") {
                    DirectionHeader(group)
                }
                items(group.departures, key = { "${group.direction}-${group.towards}-${it.countdown}" }) { dep ->
                    DepartureRow(dep)
                }
            }
        }
    }
}

@Composable
private fun Header(line: String, stopName: String, lineColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(lineColor)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(line, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.width(7.dp))
        Text(stopName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun DirectionHeader(group: DirectionGroup) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 1.dp),
    ) {
        Text("→ ", color = Color.White, fontSize = 12.sp)
        Text(
            text = group.towards,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        Text(group.direction, color = MutedText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

@Composable
private fun DepartureRow(dep: DepartureUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RowSurface)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    ) {
        Text("${dep.countdown}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(" min", color = MutedText, fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        if (dep.barrierFree) Glyph("♿", Color(0xFFCFD3D6))
        if (dep.cooling) Glyph("❄️", Color(0xFF2F9BD8))
        if (dep.trafficjam) Glyph("⚠️", Color(0xFFF0A020))
    }
}

@Composable
private fun Glyph(symbol: String, color: Color) {
    Text(symbol, color = color, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
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
        Chip(
            onClick = onRetry,
            label = { Text("Retry") },
            colors = ChipDefaults.primaryChipColors(),
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showBackground = true)
@Composable
private fun DeparturesPreview() {
    ViennaTheme {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            DeparturesContent(
                ui = DeparturesUi(
                    stopName = "Frauengasse",
                    line = "44",
                    lineType = "ptTram",
                    groups = listOf(
                        DirectionGroup(
                            "Ottakring", "H",
                            listOf(DepartureUi(4, cooling = true, barrierFree = false, trafficjam = false),
                                   DepartureUi(19, cooling = false, barrierFree = true, trafficjam = false)),
                        ),
                        DirectionGroup(
                            "Schottentor U", "R",
                            listOf(DepartureUi(2, cooling = false, barrierFree = true, trafficjam = true),
                                   DepartureUi(17, cooling = true, barrierFree = false, trafficjam = false)),
                        ),
                    ),
                ),
                lineColor = Color(0xFFE20613),
            )
        }
    }
}
