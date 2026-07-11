package com.walzengroup.viennadepart.data

/** A line available at a stop, with its distinct destinations (both directions). */
data class LineOption(
    val name: String,
    val type: String?, // vehicle.type, drives the mode color
    val termini: List<String>,
)

/** Everything the departures screen renders for one stop + line. */
data class DeparturesUi(
    val stopName: String,
    val line: String,
    val lineType: String?,
    val directions: List<DirectionPage>,
)

/**
 * One swipe page: a single direction (H/R) at the stop, with a tab label (the
 * representative terminus) and the platform groups that serve that direction.
 */
data class DirectionPage(
    val direction: String, // "H" / "R"
    val label: String,     // shown as the tab, e.g. "Gersthof"
    val platforms: List<PlatformGroup>,
)

/**
 * One platform's departures for the line. `showPlatformLabel` is true when the
 * direction is served by more than one platform, so the compass + RBL are worth
 * showing to tell them apart.
 */
data class PlatformGroup(
    val towards: String,
    val direction: String, // "H" / "R"
    val compass: String,
    val rbl: Int,
    val showPlatformLabel: Boolean,
    val departures: List<DepartureUi>,
)

data class DepartureUi(
    val countdown: Int,
    val time: String, // wall-clock departure time, "HH:mm"
    val cooling: Boolean,
    val barrierFree: Boolean,
    val trafficjam: Boolean,
)
