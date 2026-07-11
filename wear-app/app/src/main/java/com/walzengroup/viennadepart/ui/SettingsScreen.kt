package com.walzengroup.viennadepart.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.walzengroup.viennadepart.data.AppSettings
import com.walzengroup.viennadepart.data.TransitData
import com.walzengroup.viennadepart.ui.common.MutedText
import kotlinx.coroutines.launch

/**
 * Settings home page (a slide next to Search). For now: refresh the bundled
 * stop/route data from the OGD server so the app needn't ship an update for it.
 */
@Composable
fun SettingsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var updatedAt by remember { mutableStateOf(TransitData.lastUpdated(context)) }
    var openToFavorites by remember { mutableStateOf(AppSettings.openToFavorites(context)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Settings", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text("Transit data", color = MutedText, fontSize = 11.sp)
        val label = updatedAt?.let { "Updated ${DateUtils.getRelativeTimeSpanString(it)}" }
            ?: "Using bundled data"
        Text(label, color = MutedText, fontSize = 10.sp, textAlign = TextAlign.Center)
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

        Spacer(Modifier.height(14.dp))
        Text("Startup", color = MutedText, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
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
    }
}
