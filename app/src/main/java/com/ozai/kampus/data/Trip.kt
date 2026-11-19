package com.ozai.kampus.data

import com.ozai.kampus.engine.Segment

/** Ordered checkpoint labels of the trip recorder. */
object TripSteps {
    val labels: List<String> = listOf(
        "Kurtuluş peronundayım",
        "Ankaray'a bindim",
        "Kızılay'da Ankaray'dan indim",
        "M2 peronundayım",
        "Metroya bindim",
        "Beytepe'de metrodan indim",
        "130 durağındayım",
        "130'a bindim",
        "Kampüsteyim"
    )
    const val LAST = 8
}

private fun marksSamples(
    startMs: Long,
    marks: List<Long?>
): List<Pair<Segment, Double>> {
    val out = ArrayList<Pair<Segment, Double>>()
    fun m(i: Int): Long? = marks.getOrNull(i)
    fun add(seg: Segment, from: Long?, to: Long?) {
        if (from == null || to == null) return
        val min = (to - from) / 60000.0
        if (min > 0.05 && min < 180.0) out.add(Pair(seg, min))
    }
    add(Segment.WALK_HOME_KURTULUS, startMs, m(0))
    add(Segment.ANKARAY_RIDE, m(1), m(2))
    add(Segment.TRANSFER_KIZILAY, m(2), m(3))
    add(Segment.METRO_RIDE, m(4), m(5))
    add(Segment.EXIT_BEYTEPE_TO_STOP, m(5), m(6))
    add(Segment.RIDE_130, m(7), m(8))
    return out
}

/**
 * In-progress trip. [marks] has one entry per handled step (in order);
 * null means the step was skipped. All times are wall-clock epoch millis.
 * [predictedArriveMs] is the campus arrival the app predicted when recording started.
 */
data class TripState(
    val startMs: Long,
    val marks: List<Long?>,
    val predictedArriveMs: Long? = null
) {
    val isComplete: Boolean get() = marks.size >= TripSteps.labels.size

    fun withMark(ms: Long?): TripState {
        val m = marks.toMutableList()
        m.add(ms)
        return copy(marks = m)
    }

    /** Segment samples (minutes) derivable from the checkpoints recorded so far. */
    fun samples(): List<Pair<Segment, Double>> = marksSamples(startMs, marks)

    fun toRecord(): TripRecord = TripRecord(startMs, predictedArriveMs, marks)
}

/** A finished trip kept in the history list. */
data class TripRecord(
    val startMs: Long,
    val predictedArriveMs: Long?,
    val marks: List<Long?>
) {
    val actualArriveMs: Long? get() = marks.getOrNull(TripSteps.LAST)

    val totalMin: Double?
        get() {
            val a = actualArriveMs ?: return null
            return (a - startMs) / 60000.0
        }

    /** actual - predicted, in minutes (positive = later than predicted). */
    val diffMin: Double?
        get() {
            val a = actualArriveMs ?: return null
            val p = predictedArriveMs ?: return null
            return (a - p) / 60000.0
        }

    /** Waiting times seen on the trip: label to minutes. Only steps that were both recorded. */
    fun waits(): List<Pair<String, Double>> {
        val out = ArrayList<Pair<String, Double>>()
        fun m(i: Int): Long? = marks.getOrNull(i)
        fun add(label: String, from: Long?, to: Long?) {
            if (from == null || to == null) return
            val min = (to - from) / 60000.0
            if (min >= 0.0 && min < 180.0) out.add(Pair(label, min))
        }
        add("Ankaray bekleme", m(0), m(1))
        add("Metro bekleme", m(3), m(4))
        add("130 bekleme", m(6), m(7))
        return out
    }
}
