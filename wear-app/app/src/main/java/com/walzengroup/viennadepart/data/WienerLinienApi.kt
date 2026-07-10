package com.walzengroup.viennadepart.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin client for the Wiener Linien real-time monitor endpoint. No API key.
 *
 * Uses HttpURLConnection to keep the dependency surface small for Phase 1 -
 * one GET, parsed with kotlinx.serialization. A later phase can swap in a
 * proper HTTP stack if we need caching or retries.
 */
object WienerLinienApi {

    private const val BASE = "https://www.wienerlinien.at/ogd_realtime/monitor"

    private val json = Json { ignoreUnknownKeys = true }

    /** Fetch live departures for the given platform IDs (RBLs). */
    suspend fun monitor(rbls: List<Int>): MonitorResponse = withContext(Dispatchers.IO) {
        val query = rbls.joinToString("&") { "rbl=$it" }
        val connection = (URL("$BASE?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IOException("Endpoint returned HTTP $code")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            json.decodeFromString<MonitorResponse>(body)
        } finally {
            connection.disconnect()
        }
    }
}
