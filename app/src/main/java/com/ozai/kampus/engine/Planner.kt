package com.ozai.kampus.engine

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

object Planner {
    private const val DIKIMEVI_TO_KURTULUS = 2L
    private const val KURTULUS_TO_KIZILAY = 3L
    private const val KIZILAY_TO_BEYTEPE = 20L
    private const val BUS_UNCERTAIN_SHIFT = 10L
    private const val LIVE_ANKARAY_MAX_AGE_MIN = 10L
    private const val LIVE_BUS_MAX_AGE_MIN = 3L
    private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

    fun plan(departHome: LocalDateTime, stats: Stats, live: LiveInfo?): Plan =
        planAt(departHome, departHome, stats, live)

    fun options(now: LocalDateTime, stats: Stats, live: LiveInfo?, count: Int = 4): List<Plan> =
        (0 until count).map { planAt(now.plusMinutes(5L * it), now, stats, live) }

    // ---- helpers -------------------------------------------------------------------------

    /** Times 00:00-02:59 belong to the previous service day. */
    private fun serviceDay(t: LocalDateTime): LocalDate = if (t.hour < 3) t.toLocalDate().minusDays(1) else t.toLocalDate()

    /** Minutes since service-day start for "HH:MM" (after-midnight times get +24h). */
    private fun offset(hm: String): Int {
        val h = hm.substring(0, 2).toInt()
        return h * 60 + hm.substring(3, 5).toInt() + if (h < 3) 1440 else 0
    }

    private fun at(sd: LocalDate, hm: String, shiftMin: Long = 0): LocalDateTime =
        sd.atStartOfDay().plusMinutes(offset(hm) + shiftMin)

    private fun minutesBetween(a: LocalDateTime, b: LocalDateTime): Double = Duration.between(a, b).toMillis() / 60000.0

    private fun age(fetched: LocalDateTime, now: LocalDateTime): Double = minutesBetween(fetched, now)

    private fun fmt(t: LocalDateTime) = t.format(HHMM)

    private fun walkLeg(title: String, start: LocalDateTime, seg: Segment, stats: Stats): Pair<Leg, LocalDateTime> {
        val end = start.plusSeconds((stats.estimate(seg) * 60).roundToLong())
        val src = when {
            stats.isCalibrated(seg) -> Source.LEARNED
            stats.count(seg) > 0 -> Source.ESTIMATE
            else -> Source.DEFAULT
        }
        val note = if (stats.count(seg) > 0) "${stats.count(seg)} ölçüm" else "varsayılan süre"
        return Leg(title, start, end, 0.0, src, note) to end
    }

    private fun isNearby(a: LocalDateTime, b: LocalDateTime) = abs(Duration.between(a, b).seconds) <= 60

    // ---- core ----------------------------------------------------------------------------

    private fun planAt(depart: LocalDateTime, now: LocalDateTime, stats: Stats, live: LiveInfo?): Plan {
        val warnings = mutableListOf<String>()
        val legs = mutableListOf<Leg>()

        // 1. Walk home -> Kurtuluş platform
        val (walk, atKurtulus) = walkLeg(Segment.WALK_HOME_KURTULUS.title, depart, Segment.WALK_HOME_KURTULUS, stats)
        legs += walk

        // 2. Ankaray
        val (trainKurtulus, trainIsLive, rolled) = pickAnkaray(atKurtulus, now, live)
        if (rolled) warnings += "Gece Ankaray seferi yok, ertesi sabahki ilk sefer kullanıldı"
        val atKizilay = trainKurtulus.plusMinutes(KURTULUS_TO_KIZILAY)
        legs += Leg(
            "Ankaray (Kurtuluş → Kızılay)", atKurtulus, atKizilay,
            minutesBetween(atKurtulus, trainKurtulus),
            if (trainIsLive) Source.LIVE else Source.TIMETABLE,
            "${fmt(trainKurtulus)} treni (Kurtuluş)"
        )

        // 3. Transfer at Kızılay
        val (transfer, atM2) = walkLeg(Segment.TRANSFER_KIZILAY.title, atKizilay, Segment.TRANSFER_KIZILAY, stats)
        legs += transfer

        // 4. Metro
        val (metroBoard, headway, closed) = metroWait(atM2)
        if (closed) warnings += "Metro kapalı, ilk sefer 06:00'da"
        val atBeytepe = metroBoard.plusMinutes(KIZILAY_TO_BEYTEPE)
        legs += Leg(
            "Metro (Kızılay → Beytepe)", atM2, atBeytepe, minutesBetween(atM2, metroBoard), Source.ESTIMATE,
            if (closed) "ilk sefer 06:00" else "~${headway.toString().removeSuffix(".0")} dk aralıkla, ortalama bekleme"
        )

        // 5. Exit walk to the 130 stop
        val (exit, atStop) = walkLeg(Segment.EXIT_BEYTEPE_TO_STOP.title, atBeytepe, Segment.EXIT_BEYTEPE_TO_STOP, stats)
        legs += exit

        // 6. Bus 130
        val ride = Duration.ofSeconds((stats.estimate(Segment.RIDE_130) * 60).roundToLong())
        val liveBus = liveBus(atStop, now, live)
        val (busAt, src, note) = when {
            liveBus != null -> Triple(liveBus.first, Source.LIVE, "canlı: ${liveBus.second} sn sonra gelir")
            else -> {
                if (live == null || age(live.fetchedAt, now) > LIVE_BUS_MAX_AGE_MIN || live.bus130ArrivalSecs.isEmpty())
                    warnings += "130 için canlı veri yok, tarife kullanıldı"
                val tt = timetableBus(atStop)
                if (tt != null) Triple(tt, Source.TIMETABLE, "${fmt(tt)} seferi (tarife)")
                else {
                    warnings += "130 için tarife bulunamadı, 5 dk bekleme varsayıldı"
                    Triple(atStop.plusMinutes(5), Source.ESTIMATE, "tahmini bekleme")
                }
            }
        }
        val arrive = busAt.plus(ride)
        legs += Leg("130 ile kampüse", atStop, arrive, minutesBetween(atStop, busAt), src, note)

        val leaveBy = trainKurtulus.minusSeconds((stats.estimate(Segment.WALK_HOME_KURTULUS) * 60).roundToLong())
        return Plan(depart, arrive, legs, Duration.between(depart, arrive).toMillis().let { (it / 60000.0).roundToLong() }, leaveBy, warnings)
    }

    /** Returns (time train passes Kurtuluş, isLive, rolledToNextServiceDay). */
    private fun pickAnkaray(arrival: LocalDateTime, now: LocalDateTime, live: LiveInfo?): Triple<LocalDateTime, Boolean, Boolean> {
        val sd = serviceDay(arrival)
        fun deps(d: LocalDate) = Timetables.ankaray.getValue(DayType.of(d)).map { at(d, it, DIKIMEVI_TO_KURTULUS) }
        val today = deps(sd)
        val all = (today + deps(sd.plusDays(1))).map { it to false }.toMutableList()

        val liveT = live?.ankarayNextDikimevi
        if (liveT != null && age(live.fetchedAt, now) in -1.0..LIVE_ANKARAY_MAX_AGE_MIN.toDouble()) {
            var dep = live.fetchedAt.toLocalDate().atTime(liveT)
            if (Duration.between(live.fetchedAt, dep).toHours() < -12) dep = dep.plusDays(1)
            val k = dep.plusMinutes(DIKIMEVI_TO_KURTULUS)
            all.removeAll { isNearby(it.first, k) }
            all += k to true
        }
        val pick = all.filter { it.first >= arrival }.minByOrNull { it.first } ?: error("no Ankaray timetable")
        val rolled = pick.first !in today && !pick.second && today.none { it >= arrival }
        return Triple(pick.first, pick.second, rolled)
    }

    /** Returns (boarding time, headway, closed). */
    private fun metroWait(arr: LocalDateTime): Triple<LocalDateTime, Double, Boolean> {
        val sd = serviceDay(arr)
        val off = minutesBetween(sd.atStartOfDay(), arr)
        val first = offset(Timetables.M2_FIRST)
        val last = offset(Timetables.M2_LAST)  // 01:00 -> 1500
        if (off < first || off > last) {
            val open = if (off < first) sd.atStartOfDay().plusMinutes(first.toLong()) else sd.plusDays(1).atStartOfDay().plusMinutes(first.toLong())
            return Triple(open, 0.0, true)
        }
        val table = Timetables.m2Headway.getValue(DayType.of(sd))
        val hw = table.firstOrNull { offset(it.first) <= off && off < offset(it.second) }?.third ?: table.last().third
        return Triple(arr.plusSeconds((hw / 2 * 60).roundToLong()), hw, false)
    }

    /** Returns (absolute arrival, secsFromFetch). */
    private fun liveBus(atStop: LocalDateTime, now: LocalDateTime, live: LiveInfo?): Pair<LocalDateTime, Int>? {
        if (live == null || age(live.fetchedAt, now) > LIVE_BUS_MAX_AGE_MIN) return null
        return live.bus130ArrivalSecs.sorted()
            .map { live.fetchedAt.plusSeconds(it.toLong()) to it }
            .firstOrNull { it.first >= atStop }
    }

    /** Next 130 passing time at the bridge stop from the timetable. */
    private fun timetableBus(atStop: LocalDateTime): LocalDateTime? {
        val sd = serviceDay(atStop)
        fun times(d: LocalDate): List<LocalDateTime> =
            if (DayType.of(d) == DayType.WEEKDAY) Timetables.bus130Weekday.map {
                val tag = it.substring(5)
                at(d, it.substring(0, 5), if (tag == "K" || tag == "B") 0L else BUS_UNCERTAIN_SHIFT)
            } else Timetables.bus130WeekendBridge.map { at(d, it) }
        return (times(sd) + times(sd.plusDays(1))).filter { it >= atStop }.minOrNull()
    }
}
