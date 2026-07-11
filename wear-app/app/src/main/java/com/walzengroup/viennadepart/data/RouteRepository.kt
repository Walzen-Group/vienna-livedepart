package com.walzengroup.viennadepart.data

import android.content.Context
import android.location.Location
import com.walzengroup.viennadepart.data.stops.PhysicalStop
import com.walzengroup.viennadepart.data.stops.StopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Line routing from the bundled `assets/line_routes.csv` — a canonical
 * line → ordered physical stops table derived offline from Wiener Linien GTFS
 * (see `tools/build_routes.py`), keyed by the **headsign** that the live monitor
 * `towards` label carries. This replaced the `fahrwegverlaeufe.csv` pattern
 * heuristic, which mis-routed lines because the live destination is a headsign
 * ("Gersthof"), not a terminus stop name ("Wallrißstraße").
 *
 * CSV: `line;headsign;seq;diva` (ordered by seq, one row per physical stop).
 */
object RouteRepository {

    /** One direction/variant of a line: its headsign and the ordered stop DIVAs. */
    private data class Route(val headsign: String, val divas: List<String>)

    private val mutex = Mutex()
    @Volatile private var byLine: Map<String, List<Route>>? = null
    private val chainCache = ConcurrentHashMap<String, List<PhysicalStop>>()

    /**
     * Ordered physical stops along [lineName] for the route matching one of the live
     * [termini] (the monitor `towards` labels). Prefers a matching route that contains
     * [currentDiva] (the opened stop), then the longest; falls back to the longest
     * route that contains the stop, then the longest overall.
     */
    suspend fun chainFor(
        context: Context,
        lineName: String,
        termini: List<String>,
        currentDiva: String,
    ): List<PhysicalStop> {
        val cacheKey = "$lineName|$currentDiva|" + termini.sorted().joinToString(",")
        chainCache[cacheKey]?.let { return it }
        ensureLoaded(context.applicationContext)

        val routes = byLine?.get(lineName).orEmpty()
        if (routes.isEmpty()) {
            chainCache[cacheKey] = emptyList()
            return emptyList()
        }

        val chain = withContext(Dispatchers.Default) {
            val termNorms = termini.map { normalize(it) }.filter { it.isNotBlank() }
            fun matchesTerminus(r: Route): Boolean {
                val h = normalize(r.headsign)
                return termNorms.any { it.contains(h) || h.contains(it) }
            }
            val matching = routes.filter { matchesTerminus(it) }
            val chosen = matching.filter { currentDiva in it.divas }.maxByOrNull { it.divas.size }
                ?: matching.maxByOrNull { it.divas.size }
                ?: routes.filter { currentDiva in it.divas }.maxByOrNull { it.divas.size }
                ?: routes.maxByOrNull { it.divas.size }
                ?: return@withContext emptyList<PhysicalStop>()
            chosen.divas.mapNotNull { StopRepository.stopForDiva(context, it) }
        }
        chainCache[cacheKey] = chain
        return chain
    }

    /** The stop closest to (lat, lon) among all stops [lineName] serves; null if unknown. */
    suspend fun nearestStopOnLine(
        context: Context,
        lineName: String,
        lat: Double,
        lon: Double,
    ): PhysicalStop? {
        ensureLoaded(context.applicationContext)
        val routes = byLine?.get(lineName).orEmpty()
        if (routes.isEmpty()) return null
        val divas = routes.flatMapTo(HashSet()) { it.divas }
        return withContext(Dispatchers.Default) {
            val out = FloatArray(1)
            divas.mapNotNull { StopRepository.stopForDiva(context, it) }
                .minByOrNull { s ->
                    Location.distanceBetween(lat, lon, s.centerLat, s.centerLon, out)
                    out[0]
                }
        }
    }

    /** Drop parsed data so the next read reloads from the (possibly refreshed) file. */
    fun invalidate() {
        byLine = null
        chainCache.clear()
    }

    private suspend fun ensureLoaded(context: Context) {
        if (byLine != null) return
        mutex.withLock {
            if (byLine == null) byLine = load(context)
        }
    }

    // line_routes.csv: line;headsign;seq;diva
    private suspend fun load(context: Context): Map<String, List<Route>> =
        withContext(Dispatchers.IO) {
            val acc = LinkedHashMap<Pair<String, String>, MutableList<Pair<Int, String>>>()
            TransitData.open(context, "line_routes.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { row ->
                    val c = row.split(';')
                    if (c.size < 4) return@forEach
                    val line = c[0].trim()
                    val headsign = c[1].trim()
                    val seq = c[2].trim().toIntOrNull() ?: return@forEach
                    val diva = c[3].trim()
                    if (line.isEmpty() || diva.isEmpty()) return@forEach
                    acc.getOrPut(line to headsign) { mutableListOf() }.add(seq to diva)
                }
            }
            val out = HashMap<String, MutableList<Route>>()
            for ((key, rows) in acc) {
                val (line, headsign) = key
                val divas = rows.sortedBy { it.first }.map { it.second }
                out.getOrPut(line) { mutableListOf() }.add(Route(headsign, divas))
            }
            out
        }

    private fun normalize(s: String): String {
        var t = s.trim().lowercase()
        for ((a, b) in listOf("ä" to "ae", "ö" to "oe", "ü" to "ue", "ß" to "ss")) t = t.replace(a, b)
        return t
    }
}
