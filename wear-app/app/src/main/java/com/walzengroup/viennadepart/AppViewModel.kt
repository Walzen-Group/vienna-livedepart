package com.walzengroup.viennadepart

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.walzengroup.viennadepart.data.Favorite
import com.walzengroup.viennadepart.data.FavoritesStore
import com.walzengroup.viennadepart.data.HistoryStop
import com.walzengroup.viennadepart.data.RecentStop
import com.walzengroup.viennadepart.data.RecentStopsStore
import com.walzengroup.viennadepart.data.SearchHistoryStore
import com.walzengroup.viennadepart.data.stops.PhysicalStop
import com.walzengroup.viennadepart.data.stops.StopDistance

/** Holds the selection as the user moves nearby → line → departures. */
class AppViewModel : ViewModel() {
    var selectedStop by mutableStateOf<PhysicalStop?>(null)
    var selectedLine by mutableStateOf<String?>(null)

    // Cached nearby result so returning from the line list is instant and doesn't
    // re-run GPS. Cleared when the user asks for Nearby afresh from Home.
    var nearbyStops by mutableStateOf<List<StopDistance>?>(null)

    // Search history and favorites live here (activity-scoped) so they survive home
    // page swipes and navigation; the stores are the on-disk backing.
    var searchHistory by mutableStateOf<List<HistoryStop>>(emptyList())
        private set
    var favorites by mutableStateOf<List<Favorite>>(emptyList())
        private set
    var recentStops by mutableStateOf<List<RecentStop>>(emptyList())
        private set

    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        searchHistory = SearchHistoryStore.load(context)
        favorites = FavoritesStore.list(context)
        recentStops = RecentStopsStore.load(context)
        loaded = true
    }

    /** Record a station the user opened departures for (dedup by station, most-recent-first). */
    fun addRecent(context: Context, stop: PhysicalStop, line: String, type: String?) {
        RecentStopsStore.add(context, stop, line, type)
        recentStops = RecentStopsStore.load(context)
    }

    fun clearRecents(context: Context) {
        RecentStopsStore.clear(context)
        recentStops = emptyList()
    }

    fun addSearch(context: Context, stop: PhysicalStop) {
        SearchHistoryStore.add(context, stop)
        searchHistory = SearchHistoryStore.load(context)
    }

    fun clearSearch(context: Context) {
        SearchHistoryStore.clear(context)
        searchHistory = emptyList()
    }

    fun isFavorite(line: String): Boolean = favorites.any { it.line == line }

    /** Toggle the line; returns whether it's a favorite afterwards. */
    fun toggleFavorite(context: Context, line: String, type: String?): Boolean {
        val now = FavoritesStore.toggle(context, line, type)
        favorites = FavoritesStore.list(context)
        return now
    }
}
