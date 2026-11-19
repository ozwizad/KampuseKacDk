package com.ozai.kampus.ui

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ozai.kampus.data.EgoApi
import com.ozai.kampus.data.StatsStore
import com.ozai.kampus.data.TripState
import com.ozai.kampus.engine.LiveInfo
import com.ozai.kampus.engine.Segment
import com.ozai.kampus.engine.Stats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = StatsStore(app)

    var stats by mutableStateOf(store.loadStats())
        private set
    var live by mutableStateOf<LiveInfo?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var liveFailed by mutableStateOf(false)
        private set
    /** True only while a refresh the user asked for runs; background polls stay silent. */
    var manualRefreshing by mutableStateOf(false)
        private set
    var lastRefresh by mutableStateOf<LocalDateTime?>(null)
        private set
    var trip by mutableStateOf<TripState?>(store.loadTrip())
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var nowMs by mutableLongStateOf(System.currentTimeMillis())
        private set

    /** Changes once per minute; use it to key plan recomputation. */
    val minuteKey: Long by derivedStateOf { nowMs / 60_000L }

    fun tick() {
        nowMs = System.currentTimeMillis()
    }

    fun refresh(manual: Boolean = false) {
        if (loading) return
        loading = true
        manualRefreshing = manual
        viewModelScope.launch {
            try {
                val l = try {
                    EgoApi.fetchLive()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (l != null) {
                    live = l
                    liveFailed = false
                } else {
                    liveFailed = true
                }
                lastRefresh = LocalDateTime.now()
            } finally {
                loading = false
                manualRefreshing = false
            }
        }
    }

    // ---- trip recorder ----

    fun startTrip() {
        message = null
        updateTrip(TripState(System.currentTimeMillis(), emptyList()))
    }

    fun markStep() {
        val t = trip ?: return
        advance(t.withMark(System.currentTimeMillis()))
    }

    fun skipStep() {
        val t = trip ?: return
        advance(t.withMark(null))
    }

    private fun advance(t: TripState) {
        updateTrip(t)
        if (t.isComplete) finishTrip()
    }

    fun finishTrip() {
        val t = trip ?: return
        val samples = t.samples()
        if (samples.isNotEmpty()) {
            updateStats { m -> samples.forEach { (seg, v) -> m[seg]!!.add(v) } }
        }
        message = if (samples.isEmpty()) {
            "Kaydedilecek ölçüm yok."
        } else {
            "Yolculuk kaydedildi: " + samples.joinToString(", ") { (seg, v) ->
                "${seg.title} ${formatMin(v)} dk"
            }
        }
        updateTrip(null)
    }

    fun cancelTrip() {
        message = null
        updateTrip(null)
    }

    fun dismissMessage() {
        message = null
    }

    private fun updateTrip(t: TripState?) {
        trip = t
        store.saveTrip(t)
    }

    // ---- stats ----

    fun addSample(seg: Segment, minutes: Double) {
        updateStats { m -> m[seg]!!.add(minutes) }
    }

    fun deleteSample(seg: Segment, index: Int) {
        updateStats { m ->
            val list = m[seg]!!
            if (index in list.indices) list.removeAt(index)
        }
    }

    fun resetStats() {
        updateStats { m -> m.values.forEach { it.clear() } }
    }

    private fun updateStats(block: (MutableMap<Segment, MutableList<Double>>) -> Unit) {
        val m = LinkedHashMap<Segment, MutableList<Double>>()
        for (seg in Segment.values()) {
            m[seg] = stats.samples[seg].orEmpty().toMutableList()
        }
        block(m)
        val next = Stats(m.mapValues { it.value.toList() })
        stats = next
        store.saveStats(next)
    }
}
