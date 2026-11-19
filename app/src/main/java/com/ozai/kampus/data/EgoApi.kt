package com.ozai.kampus.data

import com.ozai.kampus.engine.LiveInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.LocalTime

/** Unofficial EGO Cep'te endpoint client. Never throws; failures yield null pieces. */
object EgoApi {
    private const val BASE =
        "https://egocptsrvand.ego.gov.tr/mblSrv14/service.asp?FNC=Otobusler&VER=3.1.0&LAN=tr&DURAK="
    private const val USER_AGENT = "EGO Cepte/8 CFNetwork/1568.300.101 Darwin/24.0.0"
    private const val TIMEOUT_MS = 10_000
    private const val MAX_LIVE_SECS = 7200
    private val TIME_RE = Regex("(\\d{1,2}):(\\d{2})")

    /** Returns null only when both requests failed. */
    suspend fun fetchLive(): LiveInfo? = coroutineScope {
        val ankarayDeferred = async { fetchTable("A5") }
        val busDeferred = async { fetchTable("10620") }
        val ankarayTable = ankarayDeferred.await()
        val busTable = busDeferred.await()
        if (ankarayTable == null && busTable == null) {
            return@coroutineScope null
        }
        LiveInfo(
            fetchedAt = LocalDateTime.now(),
            ankarayNextDikimevi = ankarayTable?.let { parseAnkaray(it) },
            bus130ArrivalSecs = busTable?.let { parseBus(it) } ?: emptyList()
        )
    }

    private suspend fun fetchTable(stop: String): JSONArray? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val c = URL(BASE + stop).openConnection() as HttpURLConnection
            conn = c
            c.requestMethod = "GET"
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) {
                null
            } else {
                val body = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                JSONObject(body.trim().removePrefix("﻿")).optJSONArray("table")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun parseAnkaray(table: JSONArray): LocalTime? {
        for (i in 0 until table.length()) {
            val row = table.optJSONObject(i) ?: continue
            if (!row.optString("hat_no").trim().equals("A1-G", ignoreCase = true)) continue
            val sure = row.optString("sure")
            val nl = sure.indexOf('\n')
            val tail = if (nl >= 0) sure.substring(nl + 1) else sure
            val m = TIME_RE.find(tail) ?: continue
            val h = m.groupValues[1].toIntOrNull() ?: continue
            val min = m.groupValues[2].toIntOrNull() ?: continue
            if (h in 0..23 && min in 0..59) return LocalTime.of(h, min)
        }
        return null
    }

    private fun parseBus(table: JSONArray): List<Int> {
        val out = ArrayList<Int>()
        for (i in 0 until table.length()) {
            val row = table.optJSONObject(i) ?: continue
            if (row.optString("hat_no").trim() != "130") continue
            val arac = row.optString("arac_no").trim()
            if (arac.isEmpty() || arac == "-") continue
            val secs = row.optString("saniye").trim().toIntOrNull() ?: continue
            // API belirsiz/yolda olmayan araçlar için çok büyük (ör. 1.000.000) değer döndürebiliyor.
            if (secs in 0..MAX_LIVE_SECS) out.add(secs)
        }
        out.sort()
        return out
    }
}
