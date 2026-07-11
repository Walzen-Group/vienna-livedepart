package com.walzengroup.viennadepart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.walzengroup.viennadepart.data.DeparturesRepository
import com.walzengroup.viennadepart.data.LineOption
import com.walzengroup.viennadepart.data.stops.PhysicalStop
import com.walzengroup.viennadepart.ui.common.Loadable
import com.walzengroup.viennadepart.ui.common.MutedText
import com.walzengroup.viennadepart.ui.common.RowSurface
import com.walzengroup.viennadepart.ui.common.SquareBadge
import com.walzengroup.viennadepart.ui.theme.ModeColor

@Composable
fun StopLinesScreen(stop: PhysicalStop, onSelect: (String) -> Unit) {
    val repo = remember { DeparturesRepository() }
    Loadable(loader = { repo.linesAtStop(stop) }) { lines ->
        LineList(stop.name, lines, onSelect)
    }
}

@Composable
private fun LineList(stopName: String, lines: List<LineOption>, onSelect: (String) -> Unit) {
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
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stopName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("which line?", color = MutedText, fontSize = 10.5.sp,
                        modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            if (lines.isEmpty()) {
                item { Text("No lines running now", color = MutedText, fontSize = 13.sp) }
            }
            items(lines, key = { it.name }) { opt ->
                LineRow(opt) { onSelect(opt.name) }
            }
        }
    }
}

@Composable
private fun LineRow(opt: LineOption, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(RowSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        SquareBadge(opt.name, ModeColor.forLine(opt.name, opt.type), size = 32)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            val shown = opt.termini.take(2).ifEmpty { listOf("—") }
            shown.forEach { terminus ->
                Text(terminus, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text("›", color = MutedText, fontSize = 14.sp)
    }
}
