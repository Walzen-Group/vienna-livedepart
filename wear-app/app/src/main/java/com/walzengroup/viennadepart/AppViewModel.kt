package com.walzengroup.viennadepart

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.walzengroup.viennadepart.data.stops.PhysicalStop

/** Holds the selection as the user moves nearby → line → departures. */
class AppViewModel : ViewModel() {
    var selectedStop by mutableStateOf<PhysicalStop?>(null)
    var selectedLine by mutableStateOf<String?>(null)
}
