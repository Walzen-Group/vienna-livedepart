package com.walzengroup.viennadepart.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.walzengroup.viennadepart.data.AppSettings
import com.walzengroup.viennadepart.data.TransitData
import com.walzengroup.viennadepart.ui.common.MutedText
import kotlinx.coroutines.launch

private val CardSurface = Color(0xFF1B1B21)
private val CardValue = Color(0xFFE8E8EA)

/**
 * Settings home page (a slide next to Search): choose where the app opens, and keep the
 * bundled stop/route data current from the OGD server + GitHub without shipping an update.
 */
@Composable
fun SettingsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var updatedAt by remember { mutableStateOf(TransitData.lastUpdated(context)) }
    var stopsDate by remember { mutableStateOf(TransitData.stopsDate(context)) }
    var routesDate by remember { mutableStateOf(TransitData.routesDate(context)) }
    var openToFavorites by remember { mutableStateOf(AppSettings.openToFavorites(context)) }
    var futureCount by remember { mutableStateOf(AppSettings.futureDepartures(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Settings", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))

        ToggleChip(
            checked = openToFavorites,
            onCheckedChange = {
                openToFavorites = it
                AppSettings.setOpenToFavorites(context, it)
            },
            label = { Text(if (openToFavorites) "Open to favorites" else "Open to home", fontSize = 13.sp) },
            toggleControl = {
                Icon(
                    imageVector = ToggleChipDefaults.switchIcon(checked = openToFavorites),
                    contentDescription = if (openToFavorites) "On" else "Off",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))

        Text("Future departures: $futureCount", fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        InlineSlider(
            value = futureCount,
            onValueChange = {
                futureCount = it
                AppSettings.setFutureDepartures(context, it)
            },
            valueProgression = AppSettings.MIN_FUTURE_DEPARTURES..AppSettings.MAX_FUTURE_DEPARTURES,
            decreaseIcon = { Icon(InlineSliderDefaults.Decrease, "Fewer") },
            increaseIcon = { Icon(InlineSliderDefaults.Increase, "More") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))

        // Transit data card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(CardSurface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text("Transit data", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(Modifier.height(7.dp))
            InfoRow("Stops", stopsDate ?: "bundled")
            InfoRow("Routes", routesDate ?: "bundled")
            InfoRow(
                "Updated",
                updatedAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() } ?: "never",
            )
        }

        Spacer(Modifier.height(8.dp))

        Chip(
            onClick = {
                if (updating) return@Chip
                updating = true
                status = null
                scope.launch {
                    val result = TransitData.refresh(context)
                    updating = false
                    if (result.isSuccess) {
                        updatedAt = TransitData.lastUpdated(context)
                        stopsDate = TransitData.stopsDate(context)
                        routesDate = TransitData.routesDate(context)
                        status = "Up to date"
                    } else {
                        status = "Update failed — check Wi-Fi"
                    }
                }
            },
            label = { Text(if (updating) "Updating…" else "Update transit data", fontSize = 13.sp) },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        status?.let { s ->
            Spacer(Modifier.height(4.dp))
            Text(s, color = MutedText, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

/** One label/value line inside the transit-data card. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MutedText, fontSize = 11.sp)
        Text(value, color = CardValue, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
