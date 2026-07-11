package com.walzengroup.viennadepart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
import com.walzengroup.viennadepart.data.DeparturesUi
import com.walzengroup.viennadepart.data.DepartureUi
import com.walzengroup.viennadepart.data.PlatformGroup
import com.walzengroup.viennadepart.data.stops.PhysicalStop
import com.walzengroup.viennadepart.ui.common.Glyph
import com.walzengroup.viennadepart.ui.common.Loadable
import com.walzengroup.viennadepart.ui.common.MutedText
import com.walzengroup.viennadepart.ui.common.RowSurface
import com.walzengroup.viennadepart.ui.common.SquareBadge
import com.walzengroup.viennadepart.ui.theme.ModeColor

@Composable
fun DeparturesScreen(stop: PhysicalStop, line: String) {
    val repo = remember { DeparturesRepository() }
    Loadable(loader = { repo.departuresForLine(stop, line) }) { ui ->
        DeparturesContent(ui)
    }
}

@Composable
private fun DeparturesContent(ui: DeparturesUi) {
    val lineColor = ModeColor.forLine(ui.line, ui.lineType)
    val deepTint = lerp(Color.Black, lineColor, 0.22f)
    val listState = rememberScalingLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(0f to deepTint, 0.55f to Color.Black, 1f to Color.Black)
            )
    ) {
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

                if (ui.platforms.isEmpty()) {
                    item {
                        Text("No upcoming departures", color = MutedText, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }

                ui.platforms.forEachIndexed { index, group ->
                    item(key = "head-$index") { GroupHeader(group) }
                    items(group.departures, key = { "$index-${it.countdown}" }) { dep ->
                        DepartureRow(dep)
                    }
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
        SquareBadge(line, lineColor, size = 24)
        Spacer(Modifier.width(7.dp))
        Text(stopName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun GroupHeader(group: PlatformGroup) {
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
        if (group.showPlatformLabel && group.compass.isNotEmpty()) {
            Text("${group.compass}·${group.rbl}", color = MutedText, fontSize = 9.5.sp)
            Spacer(Modifier.width(5.dp))
        }
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
