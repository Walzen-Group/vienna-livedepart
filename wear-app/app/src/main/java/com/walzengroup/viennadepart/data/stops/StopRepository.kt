package com.walzengroup.viennadepart.data.stops

import android.content.Context
import android.location.Location
import com.walzengroup.viennadepart.data.TransitData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Loads the bundled stop reference (assets/haltepunkte.csv) once and answers
 * nearest / search / lookup queries. Mirrors the Phase 0 model: group platforms
 * by DIVA into physical stops, pick a canonical name, and derive each platform's
 * compass label from its position relative to the stop centroid.
 */
object StopRepository {

    private val cardinals = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    private val mutex = Mutex()
    @Volatile private var cache: List<PhysicalStop>? = null
    @Volatile private var rblLookup: Map<Int, PhysicalStop>? = null

    suspend fun stops(context: Context): List<PhysicalStop> {
        cache?.let { return it }
        return mutex.withLock {
            cache ?: load(context.applicationContext).also { cache = it }
        }
    }

    /** The physical stop that owns the given platform RBL, or null. */
    suspend fun stopForRbl(context: Context, rbl: Int): PhysicalStop? {
        val index = rblLookup ?: run {
            val all = stops(context)
            mutex.withLock {
                rblLookup ?: HashMap<Int, PhysicalStop>().apply {
                    all.forEach { s -> s.platforms.forEach { put(it.rbl, s) } }
                }.also { rblLookup = it }
            }
        }
        return index[rbl]
    }

    /** Drop parsed data so the next read reloads from the (possibly refreshed) file. */
    fun invalidate() {
        cache = null
        rblLookup = null
    }

    suspend fun nearest(context: Context, lat: Double, lon: Double, limit: Int = 12): List<StopDistance> {
        val out = FloatArray(1)
        return stops(context)
            .map { s ->
                Location.distanceBetween(lat, lon, s.centerLat, s.centerLon, out)
                StopDistance(s, out[0])
            }
            .sortedBy { it.meters }
            .take(limit)
    }

    suspend fun search(context: Context, query: String, limit: Int = 12): List<PhysicalStop> {
        val q = normalize(query)
        if (q.isBlank()) return emptyList()
        val all = stops(context)
        // Scoring scans every stop; keep it off the main thread so search never janks.
        return withContext(Dispatchers.Default) {
            all.map { it to scoreStop(q, it) }
                .filter { it.second >= 0.5 }
                .sortedByDescending { it.second }
                .take(limit)
                .map { it.first }
        }
    }

    private suspend fun load(context: Context): List<PhysicalStop> = withContext(Dispatchers.IO) {
        class Group {
            val platforms = mutableListOf<Platform>()
            val names = LinkedHashMap<String, Int>()
        }
        val groups = LinkedHashMap<String, Group>()

        TransitData.open(context, "haltepunkte.csv").bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                val c = line.split(';')
                if (c.size < 7) return@forEach
                val rbl = c[0].trim().toIntOrNull() ?: return@forEach
                val diva = c[1].trim()
                val name = c[2].trim()
                if (name.isEmpty()) return@forEach
                val lon = c[5].trim().toDoubleOrNull()
                val lat = c[6].trim().toDoubleOrNull()

                // A real DIVA is digits with at least one non-zero; otherwise key by name.
                val key = if (diva.all { it.isDigit() } && diva.any { it != '0' } && diva.isNotEmpty()) {
                    diva
                } else {
                    "name:$name"
                }
                val group = groups.getOrPut(key) { Group() }
                group.names[name] = (group.names[name] ?: 0) + 1
                if (lat != null && lon != null && lat in 46.0..49.5 && lon in 15.0..17.5) {
                    group.platforms.add(Platform(rbl, lat, lon, ""))
                }
            }
        }

        groups.entries.mapNotNull { (key, group) ->
            if (group.platforms.isEmpty()) return@mapNotNull null
            val centerLat = group.platforms.sumOf { it.lat } / group.platforms.size
            val centerLon = group.platforms.sumOf { it.lon } / group.platforms.size
            val platforms = group.platforms.map {
                it.copy(compass = compass(centerLat, centerLon, it.lat, it.lon, group.platforms.size))
            }
            PhysicalStop(
                diva = key,
                name = canonicalName(group.names),
                aliases = group.names.keys.toList(),
                platforms = platforms,
                centerLat = centerLat,
                centerLon = centerLon,
            )
        }
    }

    private fun canonicalName(names: Map<String, Int>): String =
        names.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key.contains('(') }
                .thenBy { it.key.length }
        ).first().key

    private fun compass(cLat: Double, cLon: Double, lat: Double, lon: Double, count: Int): String {
        if (count < 2) return ""
        val north = (lat - cLat) * 111_000
        val east = (lon - cLon) * 111_000 * cos(Math.toRadians(cLat))
        if (hypot(north, east) < 15) return ""
        val bearing = (Math.toDegrees(atan2(east, north)) + 360) % 360
        return cardinals[(((bearing + 22.5) / 45).toInt()) % 8]
    }

    private fun scoreStop(q: String, stop: PhysicalStop): Double =
        stop.aliases.maxOf { nameScore(q, normalize(it)) }

    /**
     * Typo-tolerant name match. Exact / prefix / substring / word-prefix win
     * outright; otherwise fall back to edit-distance, both against the whole name
     * and against the best-matching substring window (so "jonstrasse" still finds
     * "Johnstraße" and a typo'd fragment still finds a long name).
     */
    private fun nameScore(q: String, n: String): Double {
        if (q.isEmpty() || n.isEmpty()) return 0.0
        if (q == n) return 1.0
        if (n.startsWith(q)) return 0.95
        if (n.contains(q)) return 0.85
        if (n.split(' ', '-', '/', '(', ')').any { it.isNotEmpty() && it.startsWith(q) }) return 0.8
        val full = 1.0 - levenshtein(q, n).toDouble() / maxOf(q.length, n.length)
        return maxOf(full, bestWindowRatio(q, n))
    }

    // Best edit-distance ratio of q against any same-ish-length substring of n.
    private fun bestWindowRatio(q: String, n: String): Double {
        val qlen = q.length
        if (qlen < 2 || n.length < 2) return 0.0
        var best = 0.0
        for (w in (qlen - 1).coerceAtLeast(2)..(qlen + 1)) {
            if (w > n.length) break
            var start = 0
            while (start + w <= n.length) {
                val dist = levenshtein(q, n.substring(start, start + w))
                val ratio = 1.0 - dist.toDouble() / maxOf(qlen, w)
                if (ratio > best) best = ratio
                start++
            }
        }
        return best
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = curr; curr = t
        }
        return prev[b.length]
    }

    private fun normalize(s: String): String {
        var t = s.trim().lowercase()
        for ((a, b) in listOf("ä" to "ae", "ö" to "oe", "ü" to "ue", "ß" to "ss")) {
            t = t.replace(a, b)
        }
        return t
    }
}
