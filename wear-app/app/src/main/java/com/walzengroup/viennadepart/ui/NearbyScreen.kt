package com.walzengroup.viennadepart.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.walzengroup.viennadepart.data.stops.PhysicalStop
import com.walzengroup.viennadepart.data.stops.StopDistance
import com.walzengroup.viennadepart.data.stops.StopRepository
import com.walzengroup.viennadepart.location.LocationProvider
import com.walzengroup.viennadepart.location.hasLocationPermission
import com.walzengroup.viennadepart.ui.common.Loadable
import com.walzengroup.viennadepart.ui.common.MutedText
import com.walzengroup.viennadepart.ui.common.formatDistance

@Composable
fun NearbyScreen(onSelect: (PhysicalStop) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    if (!granted) {
        PermissionPrompt { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
        return
    }

    Loadable(
        loader = {
            val location = LocationProvider(context).current()
                ?: error("Couldn't get your location. In the emulator open Extended controls → Location and send a point.")
            StopRepository.nearest(context, location.latitude, location.longitude)
        },
    ) { nearby ->
        NearbyList(nearby, onSelect)
    }
}

@Composable
private fun NearbyList(nearby: List<StopDistance>, onSelect: (PhysicalStop) -> Unit) {
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
                Text("Near you", fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 2.dp))
            }
            if (nearby.isEmpty()) {
                item { Text("No stops found nearby", color = MutedText, fontSize = 13.sp) }
            }
            items(nearby, key = { it.stop.diva }) { sd ->
                Chip(
                    onClick = { onSelect(sd.stop) },
                    label = {
                        Text(sd.stop.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    secondaryLabel = { Text(formatDistance(sd.meters)) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Location is needed to find nearby stops.",
            color = MutedText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        Chip(onClick = onGrant, label = { Text("Allow") }, colors = ChipDefaults.primaryChipColors())
    }
}
