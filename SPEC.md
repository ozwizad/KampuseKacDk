# Kampüse Kaç Dk — Android app spec (shared contract)

Personal Android app for one user (Ozai). He lives in Kurtuluş, Ankara and commutes to
Hacettepe Beytepe campus. Before leaving home he opens the app and sees:
**"Şimdi çıkarsan HH:MM'de kampüstesin"** plus the leg breakdown.
All UI text is Turkish.

## Route (legs, in order)
1. Walk: home → Kurtuluş Ankaray platform (LEARNED from user's stopwatch)
2. Ankaray A1-G (DİKİMEVİ→AŞTİ direction). Train passes Kurtuluş = Dikimevi departure + 2 min.
   Kurtuluş → Kızılay ride = 3 min.
3. Transfer walk at Kızılay Ankaray → M2 platform (LEARNED, default 4 min)
4. Metro toward Koru (EGO code M1-G "SİNCAN-ÇAYYOLU"). No exact Kızılay timetable;
   use headway table: expected wait = headway/2. Kızılay → Beytepe ride = 20 min.
5. Walk: Beytepe metro exit → 130 stop on bridge (EGO stop 10620) (LEARNED, default 4 min)
6. Bus 130: wait (LIVE if possible) + ride bridge → campus (LEARNED, default 10 min)

## Data sources
- `transit_data.json` (repo root): Ankaray Dikimevi→AŞTİ departures (hafta_ici/cumartesi/pazar,
  "HH:MM", times after midnight like "00:15" belong to the same service day),
  M2 headway table, 130 weekday timetable with tags. Tags K and B = starts at bridge
  (i.e. departs from stop 10620 at that time). Untagged/D/Ü/ÜK = uncertain; for untagged
  assume bus passes bridge at listed time + 10 min. Ignore Ü/ÜK (free special runs) — treat like untagged.
  Weekend 130: 06:35,06:50,07:05,07:20,07:35,07:50,08:05,08:20,08:30,08:40,08:50 then every 10 min 09:00–23:30, 23:40, 00:10, 00:40 — all at bridge.
- Live EGO endpoint (unofficial, used by EGO Cep'te app), GET, returns JSON:
  `https://egocptsrvand.ego.gov.tr/mblSrv14/service.asp?FNC=Otobusler&VER=3.1.0&LAN=tr&DURAK=<stop>`
  Header `User-Agent: EGO Cepte/8 CFNetwork/1568.300.101 Darwin/24.0.0`.
  Response: `{"table":[ ... ],"status":"TRUE"}`.
  - DURAK=10620 (Beytepe metro / 130): live rows have `arac_no` != "-", `hat_no`=="130",
    `saniye` (string int, seconds until arrival at this stop), `sure` ("3 dk"), `plaka_no`.
    A row with arac_no "-" has `sure` like "Sonraki Hareket Saati İlk Duraktan\n14:11 / 1 dk Sonra" (first stop is campus, ignore for 130).
  - DURAK=A5 (Kurtuluş): row hat_no "A1-G" sure "Sonraki Hareket Saati İlk Duraktan\n14:12 / 2 dk Sonra"
    → next Dikimevi departure 14:12 (tariff-based, not truly live). Parse the HH:MM after the newline.
  Poll only on screen open, on manual refresh, and every 30 s while foreground. Never faster.

## Package layout (package root `com.ozai.kampus`)
- `engine/` — PURE Kotlin (java.time only, NO android.*, NO org.json). Owned by ENGINE agent.
- `data/` — EGO API client (HttpURLConnection + org.json), SharedPreferences store. Owned by APP agent.
- `ui/` + `MainActivity.kt` — Jetpack Compose Material3. Owned by APP agent.

## Engine API (exact signatures — both agents code against this)
```kotlin
package com.ozai.kampus.engine
import java.time.*

enum class DayType { WEEKDAY, SATURDAY, SUNDAY; companion object { fun of(d: LocalDate): DayType } }

enum class Segment(val title: String, val defaultMin: Double) {
    WALK_HOME_KURTULUS("Ev → Kurtuluş peronu", 10.0),
    TRANSFER_KIZILAY("Kızılay aktarma yürüyüşü", 4.0),
    EXIT_BEYTEPE_TO_STOP("Beytepe çıkış → 130 durağı", 4.0),
    RIDE_130("130 ile kampüse", 10.0)
}

/** Measurements in minutes per segment, oldest first. */
data class Stats(val samples: Map<Segment, List<Double>>) {
    /** <3 samples: if any samples use their mean else defaultMin; >=3: mean of last 10. */
    fun estimate(s: Segment): Double
    fun count(s: Segment): Int
    fun isCalibrated(s: Segment): Boolean  // count >= 3
}

data class LiveInfo(
    val fetchedAt: LocalDateTime,
    val ankarayNextDikimevi: LocalTime?,        // from A5 / A1-G
    val bus130ArrivalSecs: List<Int>            // from 10620 live rows, sorted
)

enum class Source { LIVE, TIMETABLE, ESTIMATE, LEARNED, DEFAULT }

data class Leg(
    val title: String,          // Turkish, e.g. "Ankaray (Kurtuluş → Kızılay)"
    val start: LocalDateTime,   // when the leg begins (incl. waiting)
    val end: LocalDateTime,
    val waitMin: Double,        // waiting part (0 for walks)
    val source: Source,
    val note: String = ""       // e.g. "14:23 treni", "canlı: 06 DT 9914"
)

data class Plan(
    val departHome: LocalDateTime,
    val arriveCampus: LocalDateTime,
    val legs: List<Leg>,
    val totalMin: Long,
    /** Latest time you can leave home and still catch the same Ankaray train. */
    val leaveBy: LocalDateTime?,
    val warnings: List<String>  // e.g. "Metro kapalı", "130 için canlı veri yok, tarife kullanıldı"
)

object Planner {
    fun plan(departHome: LocalDateTime, stats: Stats, live: LiveInfo?): Plan
    /** Plans for departHome = now, now+5, ... count items (for a small "sonraki seçenekler" list). */
    fun options(now: LocalDateTime, stats: Stats, live: LiveInfo?, count: Int = 4): List<Plan>
}
```
Planner rules:
- Ankaray: candidate Kurtuluş times = timetable Dikimevi deps (for service day) + 2 min. If live.ankarayNextDikimevi
  is not null and fetchedAt within 10 min, add it too (dedupe within 1 min). Pick first >= arrival at Kurtuluş.
  Service day: times 00:00–02:59 belong to previous day's list. Handle crossing midnight correctly.
  If none (night), roll to next service day first train and add warning.
- Metro: if arrival at Kızılay M2 platform outside 06:00–01:00 → wait until 06:00 + warning. Else wait = headway/2.
- 130: arrival at stop = tBridge. If live data present and fetchedAt within 3 min: absolute live times =
  fetchedAt + secs; pick first >= tBridge → Source.LIVE, note with seconds. Else timetable bridge times
  (rules above), first >= tBridge → Source.TIMETABLE. If nothing, ESTIMATE wait 5 min + warning.
- leaveBy = chosen Kurtuluş train time − walk estimate.
- Round displayed times to minute in UI only; keep precision in engine.

## Timetable code
ENGINE agent generates `engine/Timetables.kt` from `transit_data.json` with a script
(`tools/gen_timetables.py`) — data as Kotlin lists of "HH:MM" strings / (time, tag) pairs.

## App features (APP agent)
1. Home screen: big arrival time "Kampüste ~08:47", total minutes, "En geç 08:12'de çık" (leaveBy),
   leg list with icons + source chip (Canlı / Tarife / Tahmin / Öğrenildi), warnings, last refresh time,
   refresh button, "Sonraki seçenekler" (Planner.options).
2. Calibration banner while WALK_HOME_KURTULUS has <3 samples: "Yürüme süresi ölçülüyor (1/3)".
3. Trip recorder ("Yolculuğu kaydet"): start when leaving home; buttons in order to mark checkpoints:
   Kurtuluş peronundayım → Kızılay'da Ankaray'dan indim → M2 peronundayım → Beytepe'de metrodan indim →
   130 durağındayım → 130'a bindim → Kampüsteyim. Each checkpoint optional (Atla button).
   Segment samples recorded: WALK_HOME_KURTULUS = home→Kurtuluş peron; TRANSFER_KIZILAY = indim→M2 peron;
   EXIT_BEYTEPE_TO_STOP = Beytepe indim→130 durağı; RIDE_130 = 130'a bindim→kampüs.
   Trip state must survive app being closed (persist start timestamp + checkpoint times in SharedPreferences);
   elapsed timer shown live. Use wall clock timestamps, not a running service.
4. Stats screen: per segment count, average, list of samples with delete; reset button;
   manual add sample (minutes).
5. Settings: none required beyond stats. Keep it minimal.
- minSdk 26, targetSdk/compileSdk 35, Kotlin 2.1.0, AGP 8.7.3, Compose BOM 2024.12.01, Material3.
  Dependencies: ONLY androidx core-ktx, activity-compose, compose bom (ui, material3, ui-tooling-preview,
  material-icons-extended optional → avoid; use material-icons-core), lifecycle-runtime-ktx,
  lifecycle-viewmodel-compose, kotlinx-coroutines-android. Nothing else.
- Permissions: INTERNET only.
- Build happens ONLY on GitHub Actions (no Android SDK locally). Workflow `.github/workflows/build.yml`:
  ubuntu-latest, actions/setup-java temurin 17, gradle/actions/setup-gradle, `./gradlew assembleDebug`,
  upload artifact, and create GitHub Release tag `v1.0.${{ github.run_number }}` with the APK
  (softprops/action-gh-release@v2, permissions contents: write). APK name `KampuseKacDk.apk`.
- Gradle wrapper: generate locally with `/opt/gradle/bin/gradle wrapper --gradle-version 8.11.1`
  (works offline? if it fails, write gradle/wrapper/gradle-wrapper.properties manually and let the
  workflow use setup-gradle `gradle-version: 8.11.1` and run `gradle assembleDebug` instead of ./gradlew).
