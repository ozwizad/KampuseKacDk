package com.ozai.kampus.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozai.kampus.engine.Source
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val HhMm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val HhMmSs: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val TrLocale: Locale = Locale.forLanguageTag("tr-TR")

/** Rounded to the nearest minute, shown as HH:mm. */
fun LocalDateTime.hhmm(): String =
    this.plusSeconds(30).truncatedTo(ChronoUnit.MINUTES).format(HhMm)

/** Floored to the minute (for "latest leave time"). */
fun LocalDateTime.hhmmFloor(): String =
    this.truncatedTo(ChronoUnit.MINUTES).format(HhMm)

fun LocalDateTime.hhmmss(): String = this.format(HhMmSs)

fun epochToHhmmss(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().format(HhMmSs)

fun formatMin(v: Double): String = String.format(TrLocale, "%.1f", v)

/** 75_000 -> "01:15", 3_725_000 -> "1:02:05". */
fun formatElapsed(ms: Long): String {
    val total = if (ms < 0) 0L else ms / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}

/** 200 secs -> "3 dk 20 sn". */
fun formatDelta(secs: Long): String {
    val s = if (secs < 0) 0L else secs
    val m = s / 60
    val r = s % 60
    return if (m > 0) "$m dk $r sn" else "$r sn"
}

@Composable
fun SourceChip(source: Source, modifier: Modifier = Modifier) {
    val (label, bg, fg) = when (source) {
        Source.LIVE -> Triple("Canlı", Color(0xFFC8F0D2), Color(0xFF0A5A1E))
        Source.TIMETABLE -> Triple("Tarife", Color(0xFFD4E4FA), Color(0xFF123A6B))
        Source.ESTIMATE -> Triple("Tahmin", Color(0xFFFFE6B3), Color(0xFF6B4300))
        Source.LEARNED -> Triple("Öğrenildi", Color(0xFFE5D7F7), Color(0xFF3E1F73))
        Source.DEFAULT -> Triple("Varsayılan", Color(0xFFE3E3E3), Color(0xFF444444))
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg,
        contentColor = fg
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
