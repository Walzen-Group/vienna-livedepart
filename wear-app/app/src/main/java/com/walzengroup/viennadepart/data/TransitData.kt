package com.walzengroup.viennadepart.data

import android.content.Context
import com.walzengroup.viennadepart.data.stops.StopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The bundled OGD reference CSVs, with an optional refreshed copy in internal
 * storage. [open] prefers a downloaded file and falls back to the bundled asset,
 * so the app always has data; [refresh] pulls fresh files from the OGD server,
 * validates them, swaps them in atomically, and clears the parsed caches.
 */
object TransitData {

    private data class Spec(val name: String, val url: String, val headerPrefix: String)

    private const val BASE = "https://www.wienerlinien.at/ogd_realtime/doku/ogd/"
    private val SPECS = listOf(
        Spec("haltepunkte.csv", BASE + "wienerlinien-ogd-haltepunkte.csv", "StopID;DIVA"),
        Spec("linien.csv", BASE + "wienerlinien-ogd-linien.csv", "LineID;LineText"),
        Spec("fahrwegverlaeufe.csv", BASE + "wienerlinien-ogd-fahrwegverlaeufe.csv", "LineID;PatternID"),
    )

    private const val PREF = "vienna_transit_data"
    private const val K_UPDATED = "updated_at"
    private const val DIR = "transit"

    /** Downloaded copy if present and non-empty, otherwise the bundled asset. */
    fun open(context: Context, name: String): InputStream {
        val f = File(File(context.applicationContext.filesDir, DIR), name)
        return if (f.exists() && f.length() > 0) f.inputStream()
        else context.applicationContext.assets.open(name)
    }

    /** Epoch millis of the last successful refresh, or null if never refreshed. */
    fun lastUpdated(context: Context): Long? =
        prefs(context).getLong(K_UPDATED, 0L).takeIf { it > 0 }

    /** Download, validate, and swap in fresh CSVs; clears parsed caches on success. */
    suspend fun refresh(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }
            for (spec in SPECS) {
                val tmp = File(dir, spec.name + ".tmp")
                download(spec.url, tmp)
                val firstLine = tmp.bufferedReader(Charsets.UTF_8).useLines { it.firstOrNull().orEmpty() }
                require(tmp.length() > 100 && firstLine.startsWith(spec.headerPrefix)) {
                    "Unexpected data for ${spec.name}"
                }
                val dest = File(dir, spec.name)
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
            }
            prefs(context).edit().putLong(K_UPDATED, System.currentTimeMillis()).apply()
            // Next reads re-parse from the new files.
            StopRepository.invalidate()
            RouteRepository.invalidate()
        }
    }

    private fun download(url: String, dest: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "text/csv")
        }
        try {
            conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
