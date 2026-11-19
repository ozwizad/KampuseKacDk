package com.ozai.kampus.data

import com.ozai.kampus.engine.Segment

/** Ordered checkpoint labels of the trip recorder. */
object TripSteps {
    val labels: List<String> = listOf(
        "Kurtuluş peronundayım",
        "Kızılay'da Ankaray'dan indim",
        "M2 peronundayım",
        "Beytepe'de metrodan indim",
        "130 durağındayım",
        "130'a bindim",
        "Kampüsteyim"
    )
}

/**
 * In-progress trip. [marks] has one entry per handled step (in order);
 * null means the step was skipped. All times are wall-clock epoch millis.
 */
data class TripState(val startMs: Long, val marks: List<Long?>) {
    val isComplete: Boolean get() = marks.size >= TripSteps.labels.size

    fun withMark(ms: Long?): TripState {
        val m = marks.toMutableList()
        m.add(ms)
        return copy(marks = m)
    }

    /** Segment samples (minutes) derivable from the checkpoints recorded so far. */
    fun samples(): List<Pair<Segment, Double>> {
        val out = ArrayList<Pair<Segment, Double>>()
        fun m(i: Int): Long? = marks.getOrNull(i)
        fun add(seg: Segment, from: Long?, to: Long?) {
            if (from == null || to == null) return
            val min = (to - from) / 60000.0
            if (min > 0.05 && min < 180.0) out.add(Pair(seg, min))
        }
        add(Segment.WALK_HOME_KURTULUS, startMs, m(0))
        add(Segment.TRANSFER_KIZILAY, m(1), m(2))
        add(Segment.EXIT_BEYTEPE_TO_STOP, m(3), m(4))
        add(Segment.RIDE_130, m(5), m(6))
        return out
    }
}
