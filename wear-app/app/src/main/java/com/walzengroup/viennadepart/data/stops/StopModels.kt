package com.walzengroup.viennadepart.data.stops

/** One platform of a stop: an RBL with coordinates and a compass label. */
data class Platform(
    val rbl: Int,
    val lat: Double,
    val lon: Double,
    val compass: String, // "N".."NW", or "" when central / single-platform
)

/** A physical stop (one DIVA), collecting all its platforms. */
data class PhysicalStop(
    val diva: String,
    val name: String,
    val aliases: List<String>,
    val platforms: List<Platform>,
    val centerLat: Double,
    val centerLon: Double,
) {
    val rbls: List<Int> get() = platforms.map { it.rbl }

    fun compassFor(rbl: Int): String =
        platforms.firstOrNull { it.rbl == rbl }?.compass.orEmpty()
}

/** A stop paired with its distance from a given point. */
data class StopDistance(
    val stop: PhysicalStop,
    val meters: Float,
)
