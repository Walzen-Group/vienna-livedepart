package com.walzengroup.viennadepart.data

/** What the departures screen renders, mapped from the raw monitor response. */
data class DeparturesUi(
    val stopName: String,
    val line: String,
    val lineType: String?, // vehicle.type, drives the mode color
    val groups: List<DirectionGroup>,
)

/** One line + destination + direction, with its next departures. */
data class DirectionGroup(
    val towards: String,
    val direction: String, // "H" / "R"
    val departures: List<DepartureUi>,
)

data class DepartureUi(
    val countdown: Int,
    val cooling: Boolean,
    val barrierFree: Boolean,
    val trafficjam: Boolean,
)
