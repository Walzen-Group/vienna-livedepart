package com.walzengroup.viennadepart.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.walzengroup.viennadepart.R

/** Landing screen: a Nearby button. Location runs only when it's tapped. */
@Composable
fun HomeScreen(onNearby: () -> Unit) {
    Scaffold(timeText = { TimeText() }) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onNearby,
                colors = ButtonDefaults.secondaryButtonColors(),
                modifier = Modifier.size(88.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_pin),
                    contentDescription = "Find nearby stops",
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("Nearby", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}
