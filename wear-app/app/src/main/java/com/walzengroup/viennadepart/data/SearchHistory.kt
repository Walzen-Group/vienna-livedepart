package com.walzengroup.viennadepart.data

import android.content.Context
import com.walzengroup.viennadepart.data.stops.PhysicalStop
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A stop the user reached via search, kept for the Search tab's recent list. */
@Serializable
data class HistoryStop(val diva: String, val name: String)

/**
 * The last stops opened from search (SharedPreferences, JSON). Most-recent-first,
 * deduped by stop, capped at [MAX].
 */
object SearchHistoryStore {
    private const val PREF = "vienna_search_history"
    private const val KEY = "stops"
    private const val MAX = 10
    private val serializer = ListSerializer(HistoryStop.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    fun add(context: Context, stop: PhysicalStop) {
        val current = load(context).toMutableList()
        current.removeAll { it.diva == stop.diva }
        current.add(0, HistoryStop(stop.diva, stop.name))
        prefs(context).edit()
            .putString(KEY, json.encodeToString(serializer, current.take(MAX)))
            .apply()
    }

    fun load(context: Context): List<HistoryStop> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    /** Drop one stop (by DIVA) from the history. No-op if it isn't present. */
    fun remove(context: Context, diva: String) {
        val current = load(context).toMutableList()
        if (current.removeAll { it.diva == diva }) {
            prefs(context).edit()
                .putString(KEY, json.encodeToString(serializer, current))
                .apply()
        }
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
