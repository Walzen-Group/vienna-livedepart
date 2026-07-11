package com.walzengroup.viennadepart.data

import android.content.Context

/** Small user preferences (SharedPreferences). */
object AppSettings {
    private const val PREF = "vienna_settings"
    private const val K_OPEN_FAVORITES = "open_to_favorites"

    /** When true, the app opens on the Favorites page instead of Nearby. */
    fun openToFavorites(context: Context): Boolean =
        prefs(context).getBoolean(K_OPEN_FAVORITES, false)

    fun setOpenToFavorites(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(K_OPEN_FAVORITES, value).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
