package com.walzengroup.viennadepart.data

import android.content.Context
import com.walzengroup.viennadepart.data.stops.PhysicalStop
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A stop + line the user opened, enough to reopen it without GPS. */
@Serializable
data class LastConnection(
    val diva: String,
    val stopName: String,
    val line: String,
    val type: String?, // vehicle.type, for the line badge color
)

/**
 * Persists the most recent connections across app restarts (SharedPreferences,
 * JSON). Home's "recent" side reads these so the user can jump straight back in
 * without re-running location. Most-recent-first, deduped by stop+line, capped
 * at [MAX].
 */
object LastConnectionStore {
    private const val PREF = "vienna_last_connection"
    private const val KEY = "recents"
    private const val MAX = 2
    private val serializer = ListSerializer(LastConnection.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, stop: PhysicalStop, line: String, type: String?) {
        val current = load(context).toMutableList()
        current.removeAll { it.diva == stop.diva && it.line == line }
        current.add(0, LastConnection(stop.diva, stop.name, line, type))
        val trimmed = current.take(MAX)
        context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, json.encodeToString(serializer, trimmed))
            .apply()
    }

    fun load(context: Context): List<LastConnection> {
        val raw = context.applicationContext
            .getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }
}
