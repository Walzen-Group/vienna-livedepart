package com.walzengroup.viennadepart.data

import android.content.Context
import com.walzengroup.viennadepart.data.stops.PhysicalStop
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A station the user opened, plus the last line ridden there (badge + one-tap resume). */
@Serializable
data class RecentStop(
    val diva: String,
    val name: String,
    val line: String,
    val type: String?, // vehicle.type, for the line badge color
)

/**
 * Stations opened for departures, most-recent-first, deduped by station (diva), capped
 * at [MAX]. Home's Nearby page shows the top two as pills and the rest in a scroll list;
 * revisiting a station moves it to the front and updates its last line.
 */
object RecentStopsStore {
    const val MAX = 10
    private const val PREF = "vienna_recent_stops"
    private const val KEY = "recents"
    private val serializer = ListSerializer(RecentStop.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    fun add(context: Context, stop: PhysicalStop, line: String, type: String?) {
        val current = load(context).toMutableList()
        current.removeAll { it.diva == stop.diva }
        current.add(0, RecentStop(stop.diva, stop.name, line, type))
        prefs(context).edit()
            .putString(KEY, json.encodeToString(serializer, current.take(MAX)))
            .apply()
    }

    fun load(context: Context): List<RecentStop> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
