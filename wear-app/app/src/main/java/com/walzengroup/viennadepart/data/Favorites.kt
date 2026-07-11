package com.walzengroup.viennadepart.data

import android.content.Context
import androidx.wear.tiles.TileService
import com.walzengroup.viennadepart.tile.FavoritesTileService
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** A pinned line (favorites are lines, not stops). [type] drives the badge + mode label. */
@Serializable
data class Favorite(val line: String, val type: String?)

/**
 * Pinned lines, persisted (SharedPreferences, JSON). Hard cap of [MAX] — the tile
 * can't scroll, so beyond six there's nowhere to show them.
 */
object FavoritesStore {
    const val MAX = 6
    private const val PREF = "vienna_favorites"
    private const val KEY = "lines"
    private val serializer = ListSerializer(Favorite.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    fun list(context: Context): List<Favorite> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    /** Toggle [line]; returns whether it is a favorite afterwards. A full list won't add. */
    fun toggle(context: Context, line: String, type: String?): Boolean {
        val current = list(context).toMutableList()
        val existing = current.indexOfFirst { it.line == line }
        return when {
            existing >= 0 -> { current.removeAt(existing); save(context, current); false }
            current.size >= MAX -> false // full: leave unchanged, not pinned
            else -> { current.add(Favorite(line, type)); save(context, current); true }
        }
    }

    fun remove(context: Context, line: String) {
        save(context, list(context).filterNot { it.line == line })
    }

    private fun save(context: Context, favs: List<Favorite>) {
        prefs(context).edit().putString(KEY, json.encodeToString(serializer, favs)).apply()
        // Pinned set changed → ask the framework to rebuild the honeycomb tile now.
        runCatching {
            TileService.getUpdater(context.applicationContext)
                .requestUpdate(FavoritesTileService::class.java)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
