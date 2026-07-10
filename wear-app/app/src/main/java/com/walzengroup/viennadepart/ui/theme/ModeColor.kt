package com.walzengroup.viennadepart.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Transport color mapping. The Wiener Linien feed publishes no colors, so this
 * lives in the app: U-Bahn by line name, everything else by vehicle.type.
 *
 * U-Bahn hexes are authoritative (Wikidata P465). Tram red and S-Bahn blue are
 * solid; bus and Badner Bahn blues are conventional and should be confirmed
 * against the official brand guide before shipping.
 */
object ModeColor {
    private val U1 = Color(0xFFE20613)
    private val U2 = Color(0xFFA762A3)
    private val U3 = Color(0xFFF07D00)
    private val U4 = Color(0xFF009540)
    private val U5 = Color(0xFF008E95)
    private val U6 = Color(0xFF9D6930)
    private val Tram = Color(0xFFE20613)
    private val SBahn = Color(0xFF159DD9)
    private val Bus = Color(0xFF1C3F94)
    private val Wlb = Color(0xFF0069B4)

    fun forLine(name: String, type: String?): Color {
        when (name.uppercase()) {
            "U1" -> return U1
            "U2" -> return U2
            "U3" -> return U3
            "U4" -> return U4
            "U5" -> return U5
            "U6" -> return U6
        }
        return when (type) {
            "ptTram" -> Tram
            "ptTrainS" -> SBahn
            "ptBusCity", "ptBusNight", "ptRufBus" -> Bus
            "ptTramWLB" -> Wlb
            else -> Tram // trams are the default in this network
        }
    }
}
