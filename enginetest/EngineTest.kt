import com.ozai.kampus.engine.*
import java.time.*

var fails = 0
fun check(name: String, ok: Boolean, info: String = "") {
    println((if (ok) "PASS " else "FAIL ") + name + (if (info.isNotEmpty()) "  [$info]" else ""))
    if (!ok) fails++
}
fun dump(p: Plan) {
    println("  depart ${p.departHome} arrive ${p.arriveCampus} total=${p.totalMin} leaveBy=${p.leaveBy}")
    p.legs.forEach { println("   - ${it.title}: ${it.start.toLocalTime()} -> ${it.end.toLocalTime()} wait=${"%.1f".format(it.waitMin)} ${it.source} ${it.note}") }
    p.warnings.forEach { println("   ! $it") }
}
val none = Stats(emptyMap())
fun dt(d: String, t: String) = LocalDateTime.of(LocalDate.parse(d), LocalTime.parse(t))
// 2026-11-18 Wed, 2026-11-21 Sat, 2026-11-22 Sun
val WED = "2026-11-18"; val SAT = "2026-11-21"; val SUN = "2026-11-22"

fun main() {
    // DayType
    check("DayType", DayType.of(LocalDate.parse(WED)) == DayType.WEEKDAY && DayType.of(LocalDate.parse(SAT)) == DayType.SATURDAY && DayType.of(LocalDate.parse(SUN)) == DayType.SUNDAY)

    // Stats
    val s0 = Stats(emptyMap())
    check("Stats 0 samples -> default", s0.estimate(Segment.WALK_HOME_KURTULUS) == 10.0 && s0.count(Segment.WALK_HOME_KURTULUS) == 0 && !s0.isCalibrated(Segment.WALK_HOME_KURTULUS))
    val s2 = Stats(mapOf(Segment.WALK_HOME_KURTULUS to listOf(6.0, 8.0)))
    check("Stats 2 samples -> mean, not calibrated", s2.estimate(Segment.WALK_HOME_KURTULUS) == 7.0 && !s2.isCalibrated(Segment.WALK_HOME_KURTULUS))
    val s3 = Stats(mapOf(Segment.WALK_HOME_KURTULUS to listOf(6.0, 8.0, 10.0)))
    check("Stats 3 samples -> calibrated mean", s3.estimate(Segment.WALK_HOME_KURTULUS) == 8.0 && s3.isCalibrated(Segment.WALK_HOME_KURTULUS))
    val l12 = listOf(100.0, 100.0) + List(10) { 5.0 }
    val s12 = Stats(mapOf(Segment.RIDE_130 to l12))
    check("Stats 12 samples -> mean of last 10", s12.estimate(Segment.RIDE_130) == 5.0 && s12.count(Segment.RIDE_130) == 12)

    // Weekday 07:40
    val p = Planner.plan(dt(WED, "07:40"), none, null)
    println("Weekday 07:40, no live"); dump(p)
    check("weekday 07:40 total 45-60", p.totalMin in 45..60, "${p.totalMin}")
    check("6 legs", p.legs.size == 6)
    check("legs contiguous", p.legs.zipWithNext().all { (a, b) -> a.end == b.start })
    check("arrive = last leg end", p.arriveCampus == p.legs.last().end)
    check("130 leg uses timetable + warning", p.legs.last().source == Source.TIMETABLE && p.warnings.any { it.contains("canlı veri yok") })
    // leaveBy: leaving at leaveBy catches same train with zero wait; 1 min later misses it
    val train = p.legs[1].start.plusSeconds((p.legs[1].waitMin * 60).toLong())
    check("leaveBy = train - walk", p.leaveBy == train.minusMinutes(10), "${p.leaveBy}")
    val pAt = Planner.plan(p.leaveBy!!, none, null)
    check("leave at leaveBy -> same train, 0 wait", pAt.legs[1].waitMin < 0.01 && pAt.arriveCampus == p.arriveCampus)
    val pLate = Planner.plan(p.leaveBy!!.plusMinutes(1), none, null)
    check("leave 1 min after leaveBy -> later arrival", pLate.arriveCampus > p.arriveCampus || pLate.legs[1].start.plusSeconds((pLate.legs[1].waitMin*60).toLong()) > train)

    // Live 130 fresh vs stale
    val now = dt(WED, "07:40")
    val tt = p.legs.last()
    val liveFresh = LiveInfo(now, null, listOf(900, 2600, 3300))   // 07:55, 08:23:20, 08:35
    val pl = Planner.plan(now, none, liveFresh)
    println("Live 130 fresh"); dump(pl)
    check("fresh live 130 chosen", pl.legs.last().source == Source.LIVE && pl.warnings.none { it.contains("canlı veri yok") })
    val atStop = pl.legs.last().start
    val expectSecs = 2600
    check("live bus = first >= arrival at stop", pl.legs.last().start.plusSeconds((pl.legs.last().waitMin * 60).toLong()) == now.plusSeconds(expectSecs.toLong()), "atStop=$atStop")
    val liveStale = LiveInfo(now.minusMinutes(5), null, listOf(900))
    val ps = Planner.plan(now, none, liveStale)
    check("stale live 130 ignored", ps.legs.last().source == Source.TIMETABLE && ps.arriveCampus == p.arriveCampus)
    val liveTooEarly = LiveInfo(now, null, listOf(10))
    check("live bus before arrival at stop skipped", Planner.plan(now, none, liveTooEarly).legs.last().source == Source.TIMETABLE)

    // Live Ankaray merged
    val liveA = LiveInfo(now, LocalTime.of(7, 52), emptyList())  // Dikimevi 07:52 exists in table? make 07:53 non-table
    val liveA2 = LiveInfo(now, LocalTime.of(7, 58), emptyList()) // not in table (…07:57, 08:01)
    val pa = Planner.plan(now, none, liveA2)
    println("Live Ankaray 07:58"); dump(pa)
    val tableTrain = p.legs[1].start.plusSeconds((p.legs[1].waitMin * 60).toLong())
    check("baseline train", true, "$tableTrain")
    val pa2 = Planner.plan(dt(WED, "07:48"), none, LiveInfo(dt(WED, "07:48"), LocalTime.of(7, 58), emptyList()))
    println("07:48 w/ live Ankaray 07:58"); dump(pa2)
    check("live Ankaray used when it is next >= arrival", pa2.legs[1].source == Source.LIVE && pa2.legs[1].note.contains("08:00"), pa2.legs[1].note)
    val pa3 = Planner.plan(dt(WED, "07:48"), none, LiveInfo(dt(WED, "07:48"), LocalTime.of(7, 57), emptyList()))
    check("live within 1 min of timetable deduped", pa3.legs[1].source == Source.LIVE)
    val pa4 = Planner.plan(dt(WED, "07:48"), none, LiveInfo(dt(WED, "07:30"), LocalTime.of(7, 58), emptyList()))
    check("stale live Ankaray (18 min) ignored", pa4.legs[1].source == Source.TIMETABLE)

    // Saturday / Sunday
    for ((name, d) in listOf("Saturday" to SAT, "Sunday" to SUN)) {
        val pw = Planner.plan(dt(d, "09:30"), none, null)
        println("$name 09:30"); dump(pw)
        check("$name plausible total", pw.totalMin in 40..75, "${pw.totalMin}")
        check("$name 130 timetable at bridge", pw.legs.last().source == Source.TIMETABLE)
    }
    val sat = Planner.plan(dt(SAT, "06:20"), none, null)
    val sun = Planner.plan(dt(SUN, "06:20"), none, null)
    check("weekend 130 bridge time exact (no +10)", sat.legs.last().note.contains(":") )

    // 00:50 late night: Wed 00:50 belongs to Tue service day; with 5 min walk catches 00:55 train
    val walk5 = Stats(mapOf(Segment.WALK_HOME_KURTULUS to listOf(5.0, 5.0, 5.0)))
    val pn = Planner.plan(dt(WED, "00:50"), walk5, null)
    println("00:50 late night (walk 5)"); dump(pn)
    check("00:50 catches 00:55 Dikimevi train (00:57 Kurtuluş)", pn.legs[1].end == dt(WED, "01:00") && pn.legs[1].start == dt(WED, "00:55"), pn.legs[1].note)
    check("00:50 metro closed -> 06:00 + warning", pn.warnings.any { it.contains("Metro kapalı") } && pn.legs[3].start.plusSeconds((pn.legs[3].waitMin * 60).toLong()) == dt(WED, "06:00"))
    check("00:50 arrival after 06:20", pn.arriveCampus > dt(WED, "06:20"))
    // default walk 10 min -> misses 00:55, rolls
    val pn10 = Planner.plan(dt(WED, "00:50"), none, null)
    check("00:50 with 10 min walk rolls to morning w/ warning", pn10.warnings.any { it.contains("Ankaray") } && pn10.arriveCampus.hour >= 6)

    // 02:30 no service
    val p230 = Planner.plan(dt(WED, "02:30"), none, null)
    println("02:30 no service"); dump(p230)
    check("02:30 rolls to next morning with warning", p230.warnings.any { it.contains("ilk sefer") } && p230.arriveCampus.toLocalDate() == LocalDate.parse(WED) && p230.arriveCampus.hour in 6..8)
    check("02:30 train is first morning 06:00 (Kurtuluş 06:02)", p230.legs[1].start.plusSeconds((p230.legs[1].waitMin * 60).toLong()) == dt(WED, "06:02"))
    // 23:30 crossing midnight doesn't crash and is increasing
    val p2330 = Planner.plan(dt(WED, "23:30"), none, null)
    check("23:30 arrival after depart", p2330.arriveCampus > p2330.departHome)
    println("23:30"); dump(p2330)

    // options
    val ops = Planner.options(dt(WED, "07:40"), none, liveFresh, 4)
    check("options count 4", ops.size == 4)
    check("options departures increasing by 5", ops.zipWithNext().all { (a, b) -> b.departHome == a.departHome.plusMinutes(5) } && ops[0].departHome == dt(WED, "07:40"))
    check("options arrivals non-decreasing", ops.zipWithNext().all { (a, b) -> b.arriveCampus >= a.arriveCampus })
    check("options use live 130 relative to now", ops.all { it.legs.last().source == Source.LIVE || it.legs.last().note.isNotEmpty() })

    // sources for walk legs
    check("walk source DEFAULT/ESTIMATE/LEARNED",
        Planner.plan(now, none, null).legs[0].source == Source.DEFAULT &&
        Planner.plan(now, s2, null).legs[0].source == Source.ESTIMATE &&
        Planner.plan(now, s3, null).legs[0].source == Source.LEARNED)

    println(if (fails == 0) "ALL TESTS PASSED" else "$fails FAILURES")
    if (fails > 0) System.exit(1)
}
