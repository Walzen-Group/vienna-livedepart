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
     * Ordered physical stops along [lineName] for the crown's stop strip. The live [termini]
     * (monitor `towards` labels) only pick the *direction* among the line's primary routes; they
     * never shrink the strip. Without this, a short-turn service (e.g. line 44 running only to
     * Joachimsthalerplatz at night) would collapse the strip to those few stops, hiding the rest
     * of the usual daytime line. So we match termini only against primary routes (at least half as
     * long as the longest), and fall back to the longest route containing [currentDiva]. Prefers a
     * matching primary route that contains the opened stop, then the longest.
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
            val maxLen = routes.maxOf { it.divas.size }
            fun isPrimary(r: Route) = r.divas.size * 2 >= maxLen

            // Backbone: a primary route — direction-matched to the live termini and containing the
            // opened stop when possible, else the longest primary, else the longest overall. Short
            // variants never form the backbone, so a short-turn can't shrink the line.
            val backbone =
                routes.filter { isPrimary(it) && matchesTerminus(it) && currentDiva in it.divas }.maxByOrNull { it.divas.size }
                    ?: routes.filter { isPrimary(it) && matchesTerminus(it) }.maxByOrNull { it.divas.size }
                    ?: routes.filter { isPrimary(it) && currentDiva in it.divas }.maxByOrNull { it.divas.size }
                    ?: routes.filter { isPrimary(it) }.maxByOrNull { it.divas.size }
                    ?: routes.maxByOrNull { it.divas.size }
                    ?: return@withContext emptyList<PhysicalStop>()

            // Splice on any short-turn/extension that is actually running now — its headsign is a
            // live terminus — and that meets the backbone at an endpoint (e.g. line 44's night
            // shuttle out to the Joachimsthalerplatz depot siding). This keeps those stops crown-
            // reachable while the run is active, whichever stop you open, without letting them into
            // the line's normal shape (stop-picking and the static seed stay primary-only).
            var seq = backbone.divas
            for (ext in routes) {
                if (!isPrimary(ext) && matchesTerminus(ext)) seq = spliceExtension(seq, ext.divas)
            }
            seq.mapNotNull { StopRepository.stopForDiva(context, it) }
        }
        chainCache[cacheKey] = chain
        return chain
    }

    /** The stop closest to (lat, lon) among the stops on [lineName]'s primary routes; null if unknown. */
    suspend fun nearestStopOnLine(
        context: Context,
        lineName: String,
        lat: Double,
        lon: Double,
    ): PhysicalStop? {
        ensureLoaded(context.applicationContext)
        val routes = byLine?.get(lineName).orEmpty()
        if (routes.isEmpty()) return null
        // Only consider stops on the line's primary routes. The GTFS export gives many lines a
        // tiny extra variant (short-turn, depot run, SEV replacement) under its own headsign that
        // carries a stop or two the line barely serves — e.g. line 44's 3-stop "Joachimsthalerplatz"
        // variant. Searching the union of every variant's stops can land a favorite on such a
        // phantom stop, which then shows no departures. Keep only routes at least half as long as
        // the longest, so those short variants drop out.
        val maxLen = routes.maxOf { it.divas.size }
        val divas = routes.filter { it.divas.size * 2 >= maxLen }.flatMapTo(HashSet()) { it.divas }
        return withContext(Dispatchers.Default) {
            val out = FloatArray(1)
            divas.mapNotNull { StopRepository.stopForDiva(context, it) }
                .minByOrNull { s ->
                    Location.distanceBetween(lat, lon, s.centerLat, s.centerLon, out)
                    out[0]
                }
        }
    }

    /**
     * The line's canonical stop path — its longest route — resolved straight from the bundled
     * route data with no dependency on live departures. Used to seed the crown's stop strip the
     * moment a departures screen opens, so the crown works even at a stop with no live monitors
     * (e.g. a favorite that landed on a stop the line barely serves). [chainFor] later refines
     * this to the correct direction once live termini arrive. Empty if the line is unknown.
     */
    suspend fun mainChain(context: Context, lineName: String): List<PhysicalStop> {
        ensureLoaded(context.applicationContext)
        val chosen = byLine?.get(lineName)?.maxByOrNull { it.divas.size } ?: return emptyList()
        return withContext(Dispatchers.Default) {
            chosen.divas.mapNotNull { StopRepository.stopForDiva(context, it) }
        }
    }

    /**
     * Every DIVA that appears in any line's route — i.e. the stops we can actually build a
     * live route chain for. Nearby uses this to skip physically-closer stops that aren't on
     * a serviced route (terminal loops / depots the GTFS export dropped), so opening a located
     * stop always lands on a working departures pager. Empty if the route data failed to load.
     */
    suspend fun servicedDivas(context: Context): Set<String> {
        ensureLoaded(context.applicationContext)
        return byLine?.values?.flatMapTo(HashSet()) { routes -> routes.flatMap { it.divas } } ?: emptySet()
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
                lines.forEach { row ->
                    if (row.isEmpty() || row.startsWith("#")) return@forEach // metadata comment
                    val c = row.split(';')
                    if (c.size < 4) return@forEach // the header row's seq isn't an int, so it drops out below
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

    /**
     * Attach [ext]'s new stops to [base] when the two meet at an endpoint, so an active short-turn
     * extends the strip past the terminus it branches from. The extension is oriented to its shared
     * stop and its remaining stops are appended (or prepended, if it branches off the start).
     * Returns [base] unchanged when they don't meet at an endpoint, so a mid-line branch or an
     * unrelated spur can't distort the line.
     */
    private fun spliceExtension(base: List<String>, ext: List<String>): List<String> {
        if (base.isEmpty() || ext.isEmpty()) return base
        val oriented = when {
            ext.first() in base -> ext
            ext.last() in base -> ext.reversed()
            else -> return base
        }
        val shared = oriented.first()
        val tail = oriented.drop(1).filter { it !in base }
        if (tail.isEmpty()) return base
        return when (shared) {
            base.last() -> base + tail
            base.first() -> tail.reversed() + base
            else -> base
        }
    }
}
