package com.ozai.kampus.data

import android.content.Context
import com.ozai.kampus.engine.Segment
import com.ozai.kampus.engine.Stats
import org.json.JSONArray
import org.json.JSONObject

/** SharedPreferences persistence for segment samples and the in-progress trip. */
class StatsStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences("kampus_store", Context.MODE_PRIVATE)

    fun loadStats(): Stats {
        val map = LinkedHashMap<Segment, List<Double>>()
        val root: JSONObject? = try {
            prefs.getString(KEY_STATS, null)?.let { JSONObject(it) }
        } catch (e: Exception) {
            null
        }
        for (seg in Segment.values()) {
            val list = ArrayList<Double>()
            val arr = root?.optJSONArray(seg.name)
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val v = arr.optDouble(i, Double.NaN)
                    if (!v.isNaN() && v > 0.0) list.add(v)
                }
            }
            map[seg] = list
        }
        return Stats(map)
    }

    fun saveStats(stats: Stats) {
        val root = JSONObject()
        for (seg in Segment.values()) {
            val arr = JSONArray()
            for (v in stats.samples[seg].orEmpty()) arr.put(v)
            root.put(seg.name, arr)
        }
        prefs.edit().putString(KEY_STATS, root.toString()).apply()
    }

    fun loadTrip(): TripState? {
        return try {
            val s = prefs.getString(KEY_TRIP, null) ?: return null
            val o = JSONObject(s)
            val arr = o.getJSONArray("marks")
            val marks = ArrayList<Long?>()
            for (i in 0 until arr.length()) {
                val v = arr.getLong(i)
                marks.add(if (v < 0) null else v)
            }
            val pred = o.optLong("pred", -1L)
            TripState(o.getLong("start"), marks, if (pred > 0) pred else null)
        } catch (e: Exception) {
            null
        }
    }

    fun saveTrip(trip: TripState?) {
        if (trip == null) {
            prefs.edit().remove(KEY_TRIP).apply()
            return
        }
        val arr = JSONArray()
        for (m in trip.marks) arr.put(m ?: -1L)
        val o = JSONObject()
        o.put("start", trip.startMs)
        o.put("pred", trip.predictedArriveMs ?: -1L)
        o.put("marks", arr)
        prefs.edit().putString(KEY_TRIP, o.toString()).apply()
    }

    fun loadHistory(): List<TripRecord> {
        return try {
            val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
            val arr = JSONArray(raw)
            val out = ArrayList<TripRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ma = o.getJSONArray("marks")
                val marks = ArrayList<Long?>()
                for (j in 0 until ma.length()) {
                    val v = ma.getLong(j)
                    marks.add(if (v < 0) null else v)
                }
                val pred = o.optLong("pred", -1L)
                out.add(TripRecord(o.getLong("start"), if (pred > 0) pred else null, marks))
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHistory(list: List<TripRecord>) {
        val arr = JSONArray()
        for (r in list) {
            val ma = JSONArray()
            for (m in r.marks) ma.put(m ?: -1L)
            val o = JSONObject()
            o.put("start", r.startMs)
            o.put("pred", r.predictedArriveMs ?: -1L)
            o.put("marks", ma)
            arr.put(o)
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private companion object {
        const val KEY_STATS = "stats_json"
        const val KEY_TRIP = "trip_json"
        const val KEY_HISTORY = "history_json"
    }
}
