package com.walzengroup.viennadepart.data

/**
 * Turns the raw monitor response into the UI model.
 *
 * Phase 1 is hardcoded to one stop + line: Stephansplatz, line U1. A central
 * U-Bahn line runs most of the day (and around the clock on weekend nights), so
 * it's a reliable demo - a daytime tram is often empty at night. Its two
 * platforms (one per direction) are:
 *   RBL 4111 -> Leopoldau (H)
 *   RBL 4118 -> Oberlaa (R)
 * Phase 2 replaces this with location + the bundled stop CSV.
 */
class DeparturesRepository {

    private val stopName = "Stephansplatz"
    private val line = "U1"
    private val rbls = listOf(4111, 4118)

    suspend fun loadHardcodedStop(): DeparturesUi {
        val response = WienerLinienApi.monitor(rbls)

        var lineType: String? = null

        val groups = response.data?.monitors.orEmpty()
            .flatMap { monitor -> monitor.lines }
            .filter { it.name == line }
            .map { l ->
                val departures = l.departures.departure.take(2).map { d ->
                    val v = d.vehicle
                    if (lineType == null) lineType = v?.type
                    DepartureUi(
                        countdown = d.departureTime.countdown,
                        cooling = v?.cooling ?: false,
                        barrierFree = v?.barrierFree ?: false,
                        trafficjam = v?.trafficjam ?: false,
                    )
                }
                DirectionGroup(
                    towards = l.towards.trim(),
                    direction = l.direction,
                    departures = departures,
                )
            }
            .filter { it.departures.isNotEmpty() }
            .sortedBy { it.departures.first().countdown }

        return DeparturesUi(
            stopName = stopName,
            line = line,
            lineType = lineType,
            groups = groups,
        )
    }
}
