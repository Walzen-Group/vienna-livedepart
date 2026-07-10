package com.walzengroup.viennadepart.data

import kotlinx.serialization.Serializable

/**
 * Kotlin mirror of the Wiener Linien "monitor" JSON response. Only the fields the
 * app reads are declared; `Json { ignoreUnknownKeys = true }` drops the rest.
 *
 * Shape (proved in Phase 0):
 *   data.monitors[].lines[].departures.departure[].departureTime.countdown
 *   data.monitors[].lines[].departures.departure[].vehicle.{cooling,barrierFree,trafficjam,type}
 */
@Serializable
data class MonitorResponse(
    val data: MonitorData? = null,
    val message: Message? = null,
)

@Serializable
data class Message(
    val value: String? = null, // "OK" when the endpoint is healthy
)

@Serializable
data class MonitorData(
    val monitors: List<Monitor> = emptyList(),
)

@Serializable
data class Monitor(
    val locationStop: LocationStop = LocationStop(),
    val lines: List<Line> = emptyList(),
)

@Serializable
data class LocationStop(
    val properties: StopProperties = StopProperties(),
)

@Serializable
data class StopProperties(
    val title: String = "",
    val attributes: StopAttributes = StopAttributes(),
)

@Serializable
data class StopAttributes(
    val rbl: Int = 0,
)

@Serializable
data class Line(
    val name: String = "",
    val towards: String = "",
    val direction: String = "", // "H" (Hin) or "R" (Rück)
    val departures: Departures = Departures(),
)

@Serializable
data class Departures(
    val departure: List<Departure> = emptyList(),
)

@Serializable
data class Departure(
    val departureTime: DepartureTime = DepartureTime(),
    val vehicle: Vehicle? = null, // present only when it differs from the line default
)

@Serializable
data class DepartureTime(
    val timePlanned: String? = null,
    val timeReal: String? = null,
    val countdown: Int = 0, // whole minutes until departure
)

@Serializable
data class Vehicle(
    val name: String? = null,
    val towards: String? = null,
    val direction: String? = null,
    val barrierFree: Boolean = false,
    val cooling: Boolean = false,   // air conditioning; new in doc V1.5
    val trafficjam: Boolean = false, // congestion on approach
    val type: String? = null,       // ptTram, ptBusCity, ptMetro, ptTrainS, ...
)
