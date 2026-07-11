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
 * Line routing from the bundled OGD reference (assets/linien.csv +
 * fahrwegverlaeufe.csv). Turns a line name into the ordered chain of physical
 * stops it serves, so the departures screen's crown can step to the next stop.
 *
 * fahrwegverlaeufe.StopID is the platform RBL (verified against haltepunkte);
 * Direction 1/2 is Hin/Rück. We take one direction's longest pattern (the full
 * route, not a short-turn) and collapse consecutive platforms of the same
 * physical stop into a single stop.
 */
object RouteRepository {

    private data class Seq(val pattern: String, val order: Int, val rbl: Int, val direction: String)

    private val mutex = Mutex()
    @Volatile private var lineIdByName: Map<String, String>? = null
    @Volatile private var seqByLineId: Map<String, List<Seq>>? = null
    private val chainCache = ConcurrentHashMap<String, List<PhysicalStop>>()

    /** The stop closest to (lat, lon) among all stops [lineName] serves; null if unknown. */
    suspend fun nearestStopOnLine(
        context: Context,
        lineName: String,
        lat: Double,
        lon: Double,
    ): PhysicalStop? {
        ensureLoaded(context.applicationContext)
        val lineId = lineIdByName?.get(lineName) ?: return null
        val rows = seqByLineId?.get(lineId).orEmpty()
        if (rows.isEmpty()) return null
        return withContext(Dispatchers.Default) {
            val out = FloatArray(1)
            rows.mapNotNull { StopRepository.stopForRbl(context, it.rbl) }
                .distinctBy { it.diva }
                .minByOrNull { s ->
                    Location.distanceBetween(lat, lon, s.centerLat, s.centerLon, out)
                    out[0]
                }
        }
    }

    /** Drop parsed data so the next read reloads from the (possibly refreshed) files. */
    fun invalidate() {
        lineIdByName = null
        seqByLineId = null
        chainCache.clear()
    }

    /**
     * Ordered physical stops along [lineName], following the route that ends at one
     * of [termini] (the line's live destinations). Matching the terminus avoids
     * depot/short-working patterns that "longest pattern" would otherwise pick.
     */
    suspend fun chainFor(context: Context, lineName: String, termini: List<String>): List<PhysicalStop> {
        val cacheKey = lineName + "|" + termini.sorted().joinToString(",")
        chainCache[cacheKey]?.let { return it }
        ensureLoaded(context.applicationContext)

        val lineId = lineIdByName?.get(lineName)
        val rows = lineId?.let { seqByLineId?.get(it) }.orEmpty()
        if (rows.isEmpty()) {
            chainCache[cacheKey] = emptyList()
            return emptyList()
        }

        val chain = withContext(Dispatchers.Default) {
            val byPattern = rows.groupBy { it.direction + "|" + it.pattern }
            val termNorms = termini.map { normalize(it) }.filter { it.isNotBlank() }

            fun matchesTerm(n: String) = termNorms.any { it.contains(n) || n.contains(it) }

            // Prefer the longest pattern whose BOTH ends are live termini — the real
            // end-to-end service. Matching only the terminus lets a rare long variant win
            // because its end happens to match (e.g. 44's 24-stop Winckelmannstraße →
            // Schottentor ends at a live terminus but starts off today's route), dragging
            // the far end out. Fall back to a terminus-only match, then longest overall.
            var bothEnds: List<Seq>? = null
            var oneEnd: List<Seq>? = null
            for ((_, seqs) in byPattern) {
                val ordered = seqs.sortedBy { it.order }
                val firstName = StopRepository.stopForRbl(context, ordered.first().rbl)?.name?.let { normalize(it) } ?: continue
                val lastName = StopRepository.stopForRbl(context, ordered.last().rbl)?.name?.let { normalize(it) } ?: continue
                when {
                    matchesTerm(firstName) && matchesTerm(lastName) ->
                        if (bothEnds == null || ordered.size > bothEnds.size) bothEnds = ordered
                    matchesTerm(lastName) ->
                        if (oneEnd == null || ordered.size > oneEnd.size) oneEnd = ordered
                }
            }
            val chosen = bothEnds ?: oneEnd
                ?: byPattern.values.maxByOrNull { it.size }?.sortedBy { it.order }.orEmpty()

            val out = ArrayList<PhysicalStop>()
            var lastDiva: String? = null
            for (s in chosen) {
                val stop = StopRepository.stopForRbl(context, s.rbl) ?: continue
                if (stop.diva != lastDiva) {
                    out.add(stop)
                    lastDiva = stop.diva
                }
            }
            out
        }
        chainCache[cacheKey] = chain
        return chain
    }

    private fun normalize(s: String): String {
        var t = s.trim().lowercase()
        for ((a, b) in listOf("ä" to "ae", "ö" to "oe", "ü" to "ue", "ß" to "ss")) t = t.replace(a, b)
        return t
    }

    private suspend fun ensureLoaded(context: Context) {
        if (lineIdByName != null && seqByLineId != null) return
        mutex.withLock {
            if (lineIdByName == null) lineIdByName = loadLinien(context)
            if (seqByLineId == null) seqByLineId = loadFahrwege(context)
        }
    }

    // linien.csv: LineID;LineText;SortingHelp;Realtime;MeansOfTransport
    private suspend fun loadLinien(context: Context): Map<String, String> =
        withContext(Dispatchers.IO) {
            val m = HashMap<String, String>()
            TransitData.open(context, "linien.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val c = line.split(';')
                    if (c.size < 2) return@forEach
                    val id = c[0].trim()
                    val text = c[1].trim()
                    if (id.isNotEmpty() && text.isNotEmpty()) m[text] = id
                }
            }
            m
        }

    // fahrwegverlaeufe.csv: LineID;PatternID;StopSeqCount;StopID;Direction
    private suspend fun loadFahrwege(context: Context): Map<String, List<Seq>> =
        withContext(Dispatchers.IO) {
            val m = HashMap<String, MutableList<Seq>>()
            TransitData.open(context, "fahrwegverlaeufe.csv").bufferedReader().useLines { lines ->
                lines.drop(1).forEach { line ->
                    val c = line.split(';')
                    if (c.size < 5) return@forEach
                    val lineId = c[0].trim()
                    val pattern = c[1].trim()
                    val order = c[2].trim().toIntOrNull() ?: return@forEach
                    val rbl = c[3].trim().toIntOrNull() ?: return@forEach
                    val dir = c[4].trim()
                    m.getOrPut(lineId) { ArrayList() }.add(Seq(pattern, order, rbl, dir))
                }
            }
            m
        }
}
