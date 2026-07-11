package com.walzengroup.viennadepart.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.walzengroup.viennadepart.data.RouteRepository
import com.walzengroup.viennadepart.data.stops.PhysicalStop
import com.walzengroup.viennadepart.location.LocationProvider
import com.walzengroup.viennadepart.ui.common.Loadable

/**
 * Tapping a favorite line: locate, find the nearest stop that serves the line, and
 * hand it back so the caller can open its departures. Shows a "Locating…" state.
 */
@Composable
fun FavoriteOpenScreen(line: String, onResolved: (PhysicalStop) -> Unit) {
    val context = LocalContext.current
    Loadable(
        loader = {
            val loc = LocationProvider(context).current()
                ?: error("Couldn't get your location. In the emulator open Extended controls → Location and send a point.")
            RouteRepository.nearestStopOnLine(context, line, loc.latitude, loc.longitude)
                ?: error("No stop found for line $line.")
        },
        loadingLabel = "Locating…",
    ) { stop ->
        LaunchedEffect(stop) { onResolved(stop) }
    }
}
