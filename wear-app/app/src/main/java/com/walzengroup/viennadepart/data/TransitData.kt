package com.walzengroup.viennadepart.data

import android.content.Context
import com.walzengroup.viennadepart.data.stops.StopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * The bundled OGD reference CSVs, with an optional refreshed copy in internal
 * storage. [open] prefers a downloaded file and falls back to the bundled asset,
 * so the app always has data; [refresh] pulls fresh files from the OGD server,
 * validates them, swaps them in atomically, and clears the parsed caches.
 */
object TransitData {

    private data class Spec(val name: String, val url: String, val headerPrefix: String)

    private const val BASE = "https://www.wienerlinien.at/ogd_realtime/doku/ogd/"
    // line_routes.csv is derived from GTFS by tools/build_routes.py and regenerated
    // weekly in CI; it's served from the repo's raw content.
    private const val ROUTES_URL =
        "https://raw.githubusercontent.com/Walzen-Group/vienna-livedepart/master/wear-app/app/src/main/assets/line_routes.csv"
    private val SPECS = listOf(
        Spec("haltepunkte.csv", BASE + "wienerlinien-ogd-haltepunkte.csv", "StopID;DIVA"),
        Spec("line_routes.csv", ROUTES_URL, "line;headsign"),
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

    /** Source date (YYYY-MM-DD) of the stop table, from the OGD Last-Modified; null if bundled. */
    fun stopsDate(context: Context): String? =
        prefs(context).getString("date_haltepunkte.csv", null)?.takeIf { it.isNotBlank() }

    /** Generation date embedded in line_routes.csv (#generated=YYYY-MM-DD); null if absent. */
    fun routesDate(context: Context): String? = runCatching {
        open(context, "line_routes.csv").bufferedReader().use { br ->
            val first = br.readLine().orEmpty()
            if (first.startsWith("#generated=")) first.removePrefix("#generated=").trim().ifBlank { null } else null
        }
    }.getOrNull()

    /** Download, validate, and swap in fresh CSVs; clears parsed caches on success. */
    suspend fun refresh(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.applicationContext.filesDir, DIR).apply { mkdirs() }
            for (spec in SPECS) {
                val tmp = File(dir, spec.name + ".tmp")
                val lastModified = download(spec.url, tmp)
                // First non-comment line: line_routes.csv leads with a #generated= line.
                val firstLine = tmp.bufferedReader(Charsets.UTF_8).useLines { seq ->
                    seq.firstOrNull { !it.startsWith("#") }.orEmpty()
                }
                require(tmp.length() > 100 && firstLine.startsWith(spec.headerPrefix)) {
                    "Unexpected data for ${spec.name}"
                }
                val dest = File(dir, spec.name)
                if (!tmp.renameTo(dest)) {
                    tmp.copyTo(dest, overwrite = true)
                    tmp.delete()
                }
                prefs(context).edit().putString("date_${spec.name}", httpDateToIso(lastModified).orEmpty()).apply()
            }
            prefs(context).edit().putLong(K_UPDATED, System.currentTimeMillis()).apply()
            // Next reads re-parse from the new files.
            StopRepository.invalidate()
            RouteRepository.invalidate()
        }
    }

    /** Downloads [url] to [dest]; returns the server's Last-Modified header if present. */
    private fun download(url: String, dest: File): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "text/csv")
        }
        try {
            conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
            return conn.getHeaderField("Last-Modified")
        } finally {
            conn.disconnect()
        }
    }

    // HTTP date ("Wed, 09 Jul 2026 12:00:00 GMT") -> "2026-07-09", or null if unparseable.
    private fun httpDateToIso(s: String?): String? = s?.let {
        runCatching {
            ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toLocalDate().toString()
        }.getOrNull()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
