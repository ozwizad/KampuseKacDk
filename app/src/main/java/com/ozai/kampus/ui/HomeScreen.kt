package com.ozai.kampus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozai.kampus.engine.Leg
import com.ozai.kampus.engine.Planner
import com.ozai.kampus.engine.Plan
import com.ozai.kampus.engine.Segment
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.roundToInt

private class HomeData(val plan: Plan, val options: List<Plan>)

@Composable
fun HomeScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Kampüse Kaç Dk",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (walkCount < 3) {
            item { CalibrationBanner(walkCount) }
        }

        if (data == null) {
            item {
                InfoCard(
                    "Plan hesaplanamadı. Yenilemeyi dene.",
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
            }
        } else {
            val plan = data.plan
            item { ArrivalCard(plan) }

            if (vm.liveFailed) {
                item {
                    InfoCard(
                        "Canlı veri alınamadı, tarife kullanılıyor.",
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            items(plan.warnings) { w ->
                InfoCard(
                    w,
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer
                )
            }

            item {
                Text(
                    "Yolculuk adımları",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(plan.legs) { leg -> LegCard(leg) }

            item { RefreshRow(vm) }

            if (data.options.isNotEmpty()) {
                item {
                    Text(
                        "Sonraki seçenekler",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(data.options) { opt -> OptionRow(opt) }
            }
        }

        if (data == null) {
            item { RefreshRow(vm) }
        }
    }
}

@Composable
private fun CalibrationBanner(count: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Yürüme süresi ölçülüyor ($count/3)",
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.size(2.dp))
            Text(
                "Yolculuk sekmesinden eve çıkarken kaydı başlat; ilk 3 ölçümden sonra ev yürüyüşü öğrenilmiş süreyle hesaplanır.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun InfoCard(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
private fun ArrivalCard(plan: Plan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                fontSize = 76.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Toplam ${plan.totalMin} dk",
                style = MaterialTheme.typography.titleMedium
            )
            val leaveBy = plan.leaveBy
            if (leaveBy != null) {
                Spacer(Modifier.size(10.dp))
                Text(
                    "En geç çıkış: ${leaveBy.hhmmFloor()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun legEmoji(title: String): String {
    val t = title.trim()
    return when {
        t.startsWith("Ankaray", ignoreCase = true) -> "🚇"
        t.startsWith("Metro", ignoreCase = true) -> "🚇"
        t.startsWith("130") -> "🚌"
        else -> "🚶"
    }
}

@Composable
private fun LegCard(leg: Leg) {
    val mins = (Duration.between(leg.start, leg.end).seconds / 60.0).roundToInt()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(legEmoji(leg.title), fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(leg.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "${leg.start.hhmm()} → ${leg.end.hhmm()} · $mins dk",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (leg.waitMin >= 0.5) {
                    Text(
                        "Bekleme ~${leg.waitMin.roundToInt()} dk",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (leg.note.isNotBlank()) {
                    Text(
                        leg.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            SourceChip(leg.source)
        }
    }
}

@Composable
private fun RefreshRow(vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val last = vm.lastRefresh
        Text(
            if (last != null) "Son güncelleme: ${last.hhmmss()}" else "Henüz güncellenmedi",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = { vm.refresh() }, enabled = !vm.loading) {
            if (vm.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Yenile")
        }
    }
}

@Composable
private fun OptionRow(plan: Plan) {
    val mins = plan.totalMin
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Text("Çıkış ${plan.departHome.hhmm()}", fontWeight = FontWeight.Medium)
            Text("Kampüs ${plan.arriveCampus.hhmm()}", fontWeight = FontWeight.Bold)
            Text("$mins dk", style = MaterialTheme.typography.bodySmall)
        }
    }
}
