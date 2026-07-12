package com.walzengroup.viennadepart.data

import android.content.Context

/** Small user preferences (SharedPreferences). */
object AppSettings {
    private const val PREF = "vienna_settings"
    private const val K_OPEN_FAVORITES = "open_to_favorites"
    private const val K_FUTURE_DEPARTURES = "future_departures"

    /** Min/default and max number of upcoming departures shown per platform. */
    const val MIN_FUTURE_DEPARTURES = 2
    const val MAX_FUTURE_DEPARTURES = 5

    /** When true, the app opens on the Favorites page instead of Nearby. */
    fun openToFavorites(context: Context): Boolean =
        prefs(context).getBoolean(K_OPEN_FAVORITES, false)

    fun setOpenToFavorites(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_OPEN_FAVORITES, value).apply()
    }

    /** How many upcoming departures to show per platform (clamped to 2..5, default 2). */
    fun futureDepartures(context: Context): Int =
        prefs(context).getInt(K_FUTURE_DEPARTURES, MIN_FUTURE_DEPARTURES)
            .coerceIn(MIN_FUTURE_DEPARTURES, MAX_FUTURE_DEPARTURES)

    fun setFutureDepartures(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(K_FUTURE_DEPARTURES, value.coerceIn(MIN_FUTURE_DEPARTURES, MAX_FUTURE_DEPARTURES))
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
