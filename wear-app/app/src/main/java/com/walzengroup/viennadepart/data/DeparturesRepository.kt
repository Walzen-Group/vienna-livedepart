package com.walzengroup.viennadepart.data

import com.walzengroup.viennadepart.data.stops.PhysicalStop

/**
 * Live departures for a chosen stop, built from one monitor call over the stop's
 * platforms (RBLs). Two queries:
 *   - [linesAtStop]: which lines serve the stop right now, with their termini.
 *   - [departuresForLine]: the next departures for one line, grouped by platform.
 */
class DeparturesRepository {

    /** Distinct lines currently at the stop, each with its destinations. */
    suspend fun linesAtStop(stop: PhysicalStop): List<LineOption> {
        val response = cachedMonitor(stop.rbls)

        class Acc {
            var type: String? = null
            val towards = LinkedHashSet<String>()
        }
        val byLine = LinkedHashMap<String, Acc>()

        response.data?.monitors.orEmpty().forEach { monitor ->
            monitor.lines.forEach { line ->
                if (line.name.isBlank()) return@forEach
                val acc = byLine.getOrPut(line.name) { Acc() }
                if (line.towards.isNotBlank()) acc.towards.add(line.towards.trim())
                if (acc.type == null) acc.type = line.departures.departure.firstOrNull()?.vehicle?.type
            }
        }

        return byLine.entries
            .map { (name, acc) -> LineOption(name, acc.type, acc.towards.toList()) }
            .sortedWith(compareBy({ numericKey(it.name) }, { it.name }))
    }

    /** Next departures for one line at the stop, grouped by platform. */
    suspend fun departuresForLine(stop: PhysicalStop, line: String): DeparturesUi {
        val response = cachedMonitor(stop.rbls)
        var lineType: String? = null

        val groups = response.data?.monitors.orEmpty().flatMap { monitor ->
            val rbl = monitor.locationStop.properties.attributes.rbl
            val compass = stop.compassFor(rbl)
            monitor.lines
                .filter { it.name == line && it.name.isNotBlank() }
                .map { l ->
                    val departures = l.departures.departure.take(2).map { d ->
                        val v = d.vehicle
                        if (lineType == null) lineType = v?.type
                        DepartureUi(
                            countdown = d.departureTime.countdown,
                            cooling = v?.cooling == true,
                            barrierFree = v?.barrierFree == true,
                            trafficjam = v?.trafficjam == true,
                        )
                    }
                    PlatformGroup(
                        towards = l.towards.trim(),
                        direction = l.direction,
                        compass = compass,
                        rbl = rbl,
                        showPlatformLabel = false, // decided below
                        departures = departures,
                    )
                }
        }.filter { it.departures.isNotEmpty() }

        // Show the platform label only when a direction has more than one platform.
        val perDirection = groups.groupingBy { it.direction }.eachCount()
        val labeled = groups
            .map { it.copy(showPlatformLabel = (perDirection[it.direction] ?: 0) >= 2) }
            .sortedWith(compareBy({ it.direction }, { it.departures.first().countdown }))

        // One page per direction (H before R), each with a representative terminus
        // for its tab: the destination most platforms serve, so short-turns lose.
        val pages = labeled.groupBy { it.direction }
            .map { (dir, grps) ->
                val label = grps.groupingBy { it.towards }.eachCount()
                    .maxByOrNull { it.value }?.key ?: dir
                DirectionPage(dir, label, grps)
            }
            .sortedBy { directionOrder(it.direction) }

        return DeparturesUi(stop.name, line, lineType, pages)
    }

    // Sort trams/buses by number; U-Bahn and letter lines fall to the end.
    private fun numericKey(name: String): Int =
        name.filter { it.isDigit() }.toIntOrNull() ?: 9999

    // H (Hin) reads as the first page, R (Rück) second; anything else trails.
    private fun directionOrder(direction: String): Int = when (direction) {
        "H" -> 0
        "R" -> 1
        else -> 2
    }

    // One monitor call per stop, reused briefly across the line list, the
    // departures screen, and back-navigation so those feel instant.
    private suspend fun cachedMonitor(rbls: List<Int>): MonitorResponse {
        val key = rbls.sorted().joinToString(",")
        MonitorCache.get(key)?.let { return it }
        return WienerLinienApi.monitor(rbls).also { MonitorCache.put(key, it) }
    }
}

private object MonitorCache {
    private const val TTL_MS = 20_000L
    private class Entry(val at: Long, val response: MonitorResponse)
    private val entries = HashMap<String, Entry>()

    @Synchronized
    fun get(key: String): MonitorResponse? {
        val entry = entries[key] ?: return null
        return if (System.currentTimeMillis() - entry.at < TTL_MS) entry.response
        else { entries.remove(key); null }
    }

    @Synchronized
    fun put(key: String, response: MonitorResponse) {
        entries[key] = Entry(System.currentTimeMillis(), response)
    }
}
