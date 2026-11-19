package com.ozai.kampus.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozai.kampus.engine.Leg
import com.ozai.kampus.engine.Plan
import com.ozai.kampus.engine.Planner
import com.ozai.kampus.engine.Segment
import com.ozai.kampus.engine.Source
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

private class HomeData(val plan: Plan, val options: List<Plan>)

@Composable
fun HomeScreen(
    vm: MainViewModel,
    modifier: Modifier = Modifier,
    onOpenTrip: () -> Unit = {}
) {
    val minute = vm.minuteKey
    val stats = vm.stats
    val live = vm.live
    val data: HomeData? = remember(minute, stats, live) {
        try {
            val now = LocalDateTime.now()
            val plan = Planner.plan(now, stats, live)
            val opts = Planner.options(now, stats, live, 5).drop(1)
            HomeData(plan, opts)
        } catch (e: Exception) {
            null
        }
    }
    val walkCount = stats.count(Segment.WALK_HOME_KURTULUS)
    val trip = vm.trip

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            Text(
                "Kampüse Kaç Dk",
                modifier = softItem(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (trip != null) {
            item(key = "trip") {
                TripPill(vm.nowMs - trip.startMs, onOpenTrip, softItem())
            }
        }

        if (data == null) {
            item(key = "error") {
                InfoCard(
                    "Plan hesaplanamadı. Yenilemeyi dene.",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    softItem()
                )
            }
            item(key = "refresh") { RefreshRow(vm, softItem()) }
        } else {
            val plan = data.plan
            val notes = buildList {
                if (vm.liveFailed) add("Canlı veri alınamadı, tarife kullanılıyor.")
                plan.warnings.forEach { if (it !in this) add(it) }
            }

            // The answer comes first; everything else supports it.
            item(key = "hero") { ArrivalCard(plan, notes, softItem()) }

            if (walkCount < 3) {
                item(key = "calibration") { CalibrationBanner(walkCount, softItem()) }
            }

            item(key = "legs-title") {
                Text(
                    "Yolculuk adımları",
                    modifier = softItem(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item(key = "legs") { Timeline(plan.legs, softItem()) }

            item(key = "refresh") { RefreshRow(vm, softItem()) }

            if (data.options.isNotEmpty()) {
                item(key = "options-title") {
                    Text(
                        "Sonraki seçenekler",
                        modifier = softItem(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                itemsIndexed(data.options, key = { i, _ -> "option_$i" }) { _, opt ->
                    OptionRow(opt, softItem())
                }
            }
        }
    }
}

@Composable
private fun TripPill(elapsedMs: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = source,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(source),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text("Kayıt sürüyor", fontWeight = FontWeight.Medium)
            }
            Text(
                formatElapsed(elapsedMs),
                style = MaterialTheme.typography.bodyMedium.merge(TabularNums),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CalibrationBanner(count: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Yürüme süresi ölçülüyor ($count/3)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Yolculuk sekmesinde kaydı başlat; 3 ölçümden sonra süre öğrenilir.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun InfoCard(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ArrivalCard(plan: Plan, notes: List<String>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 22.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Şimdi çıkarsan kampüstesin", style = MaterialTheme.typography.titleMedium)
            Text(
                plan.arriveCampus.hhmm(),
                style = TabularNums,
                fontSize = 76.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Toplam ${plan.totalMin} dk",
                style = MaterialTheme.typography.titleMedium.merge(TabularNums)
            )
            val leaveBy = plan.leaveBy
            if (leaveBy != null) {
                Spacer(Modifier.size(10.dp))
                val minsLeft = Duration.between(LocalDateTime.now(), leaveBy).toMinutes()
                val countdown = if (minsLeft >= 1) "$minsLeft dk sonra" else "şimdi"
                Text(
                    "En geç çıkış: ${leaveBy.hhmmFloor()} · $countdown",
                    style = MaterialTheme.typography.titleMedium.merge(TabularNums),
                    fontWeight = FontWeight.SemiBold
                )
            }
            // Status lines live inside the card so they never push the list around.
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(200, easing = UiEaseOut)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (notes.isNotEmpty()) Spacer(Modifier.size(12.dp))
                notes.forEach { note ->
                    Text(
                        note,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

private enum class LegKind { WALK, RAIL, BUS }

private fun legKind(title: String): LegKind {
    val t = title.trim()
    return when {
        t.startsWith("Ankaray", ignoreCase = true) || t.startsWith("Metro", ignoreCase = true) -> LegKind.RAIL
        t.startsWith("130") -> LegKind.BUS
        else -> LegKind.WALK
    }
}

/** One card, one vertical rail: the whole trip reads at a glance. */
@Composable
private fun Timeline(legs: List<Leg>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            legs.forEachIndexed { i, leg ->
                TimelineRow(leg, first = i == 0, last = i == legs.lastIndex)
            }
        }
    }
}

@Composable
private fun TimelineRow(leg: Leg, first: Boolean, last: Boolean) {
    val kind = legKind(leg.title)
    val mins = (Duration.between(leg.start, leg.end).seconds / 60.0).roundToInt()
    val railColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    val dotColor = when (kind) {
        LegKind.RAIL -> MaterialTheme.colorScheme.primary
        LegKind.BUS -> Color(0xFFB45F06)
        LegKind.WALK -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Canvas(
            Modifier
                .width(28.dp)
                .fillMaxHeight()
        ) {
            val cx = 10.dp.toPx()
            val cy = 22.dp.toPx()
            val top = if (first) cy else 0f
            val bottom = if (last) cy else size.height
            drawLine(railColor, Offset(cx, top), Offset(cx, bottom), strokeWidth = 2.dp.toPx())
            if (kind == LegKind.WALK) {
                // Hollow dot for walking, solid dot for rides.
                drawCircle(cardColor, 5.dp.toPx(), Offset(cx, cy))
                drawCircle(dotColor, 5.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
            } else {
                drawCircle(dotColor, 6.dp.toPx(), Offset(cx, cy))
            }
        }
        Column(
            Modifier
                .weight(1f)
                .padding(vertical = 10.dp)
        ) {
            Text(leg.title, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${leg.start.hhmm()} → ${leg.end.hhmm()} · $mins dk",
                    style = MaterialTheme.typography.bodyMedium.merge(TabularNums)
                )
                // The timetable is the norm, so it gets no chip; only live/guessed/learned values do.
                if (leg.source != Source.TIMETABLE) SourceChip(leg.source)
            }
            val detail = listOfNotNull(
                if (leg.waitMin >= 0.5) "Bekleme ~${leg.waitMin.roundToInt()} dk" else null,
                leg.note.takeIf { it.isNotBlank() }
            )
            if (detail.isNotEmpty()) {
                Text(
                    detail.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RefreshRow(vm: MainViewModel, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val last = vm.lastRefresh
        Text(
            if (last != null) "Son güncelleme: ${last.hhmmss()}" else "Henüz güncellenmedi",
            style = MaterialTheme.typography.bodySmall.merge(TabularNums),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Background polls never touch this button; only a tap makes the icon spin.
        AppButton(onClick = { vm.refresh(manual = true) }) {
            RefreshIcon(spinning = vm.manualRefreshing)
            Spacer(Modifier.width(8.dp))
            Text("Yenile")
        }
    }
}

@Composable
private fun RefreshIcon(spinning: Boolean) {
    val angle = if (spinning) {
        val transition = rememberInfiniteTransition(label = "refreshSpin")
        val a by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
            label = "refreshAngle"
        )
        a
    } else {
        0f
    }
    Icon(
        Icons.Default.Refresh,
        contentDescription = null,
        modifier = Modifier
            .size(18.dp)
            .rotate(angle)
    )
}

@Composable
private fun OptionRow(plan: Plan, modifier: Modifier = Modifier) {
    val mins = plan.totalMin
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Çıkış ${plan.departHome.hhmm()}", style = TabularNums, fontWeight = FontWeight.Medium)
            Text("Kampüs ${plan.arriveCampus.hhmm()}", style = TabularNums, fontWeight = FontWeight.Bold)
            Text("$mins dk", style = MaterialTheme.typography.bodySmall.merge(TabularNums))
        }
    }
}
