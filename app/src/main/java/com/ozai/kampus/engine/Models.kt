package com.ozai.kampus.engine

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.DayOfWeek

enum class DayType {
    WEEKDAY, SATURDAY, SUNDAY;

    companion object {
        fun of(d: LocalDate): DayType = when (d.dayOfWeek) {
            DayOfWeek.SATURDAY -> SATURDAY
            DayOfWeek.SUNDAY -> SUNDAY
            else -> WEEKDAY
        }
    }
}

enum class Segment(val title: String, val defaultMin: Double) {
    WALK_HOME_KURTULUS("Ev → Kurtuluş peronu", 10.0),
    TRANSFER_KIZILAY("Kızılay aktarma yürüyüşü", 4.0),
    EXIT_BEYTEPE_TO_STOP("Beytepe çıkış → 130 durağı", 4.0),
    RIDE_130("130 ile kampüse", 10.0)
}

/** Measurements in minutes per segment, oldest first. */
data class Stats(val samples: Map<Segment, List<Double>>) {
    /** <3 samples: mean of them if any, else defaultMin; >=3: mean of last 10. */
    fun estimate(s: Segment): Double {
        val l = samples[s].orEmpty()
        return if (l.isEmpty()) s.defaultMin else l.takeLast(10).average()
    }

    fun count(s: Segment): Int = samples[s]?.size ?: 0
    fun isCalibrated(s: Segment): Boolean = count(s) >= 3
}

data class LiveInfo(
    val fetchedAt: LocalDateTime,
    val ankarayNextDikimevi: LocalTime?,
    val bus130ArrivalSecs: List<Int>
)

enum class Source { LIVE, TIMETABLE, ESTIMATE, LEARNED, DEFAULT }

data class Leg(
    val title: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val waitMin: Double,
    val source: Source,
    val note: String = ""
)

data class Plan(
    val departHome: LocalDateTime,
    val arriveCampus: LocalDateTime,
    val legs: List<Leg>,
    val totalMin: Long,
    /** Latest time you can leave home and still catch the same Ankaray train. */
    val leaveBy: LocalDateTime?,
    val warnings: List<String>
)
