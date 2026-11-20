package com.ozai.kampus.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozai.kampus.data.TripNotifier
import com.ozai.kampus.data.TripState
import com.ozai.kampus.data.TripSteps
import com.ozai.kampus.engine.Planner
import com.ozai.kampus.engine.TripPlan
import com.ozai.kampus.engine.TripStep
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private fun toLdt(ms: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

private fun toMs(t: LocalDateTime): Long =
    t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** Live companion shown on the home screen while a trip is running. */
@Composable
fun ActiveTrip(vm: MainViewModel, modifier: Modifier = Modifier) {
    val trip = vm.trip ?: return
    val minute = vm.minuteKey
    val stats = vm.stats
    val live = vm.live
    var confirmCancel by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val ctx = LocalContext.current.applicationContext

    // Anchored to the start of the trip: the planned train only changes if you really miss it.
    val plan: TripPlan? = remember(trip, minute, stats, live) {
        try {
            Planner.planTrip(
                toLdt(trip.startMs),
                trip.marks.map { it?.let(::toLdt) },
                LocalDateTime.now(), stats, live
            )
        } catch (e: Exception) {
            null
        }
    }
    val next: TripStep? = plan?.steps?.getOrNull(plan.nextIndex)

    LaunchedEffect(plan?.nextIndex, next?.expected, minute) {
        if (plan != null && next != null) {
            TripNotifier.update(
                ctx,
                "Sıradaki: ${TripSteps.targets[next.index]}",
                "${next.time.hhmm()} · kampüs ${plan.arriveCampus.hhmm()}",
                toMs(next.expected)
            )
        }
    }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "title") {
                Text(
                    "Yolculuk sürüyor",
                    modifier = softItem(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (plan == null) {
                item(key = "error") {
                    Text("Plan hesaplanamadı.", modifier = softItem())
                }
            } else {
                val notes = buildList {
                    if (vm.liveFailed) add("Canlı veri alınamadı, tarife kullanılıyor.")
                    plan.warnings.forEach { if (it !in this) add(it) }
                }
                item(key = "hero") { TripHero(plan, trip, vm.nowMs, notes, softItem()) }
                if (next != null) {
                    item(key = "next") { NextCard(next, vm.nowMs, softItem()) }
                }
                item(key = "steps-title") {
                    Text(
                        "Adımlar",
                        modifier = softItem(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                for (st in plan.steps) {
                    item(key = "step_${st.index}") {
                        StepRow(st, isCurrent = st.index == plan.nextIndex, modifier = softItem())
                    }
                }
            }

            item(key = "refresh") { RefreshRow(vm, softItem()) }

            item(key = "finish") {
                Row(
                    softItem().fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AppButton(onClick = { vm.finishTrip() }, modifier = Modifier.weight(1f)) {
                        Text("Bitir ve kaydet")
                    }
                    AppOutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.weight(1f)) {
                        Text("İptal et")
                    }
                }
            }
        }

        // The main action never moves: same spot, same size, whatever step you are on.
        val label = TripSteps.labels.getOrNull(trip.marks.size).orEmpty()
        ActionBar {
            Text(
                "Sıradaki adım",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedContent(
                targetState = label,
                transitionSpec = {
                    fadeIn(tween(200, easing = UiEaseOut)) togetherWith
                        fadeOut(tween(120, easing = UiEaseOut))
                },
                label = "stepLabel"
            ) { text ->
                Text(
                    text,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.markStep()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                ) {
                    Text("Şimdi", fontSize = 18.sp)
                }
                AppOutlinedButton(
                    onClick = { vm.skipStep() },
                    modifier = Modifier.height(60.dp)
                ) {
                    Text("Atla")
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Yolculuk iptal edilsin mi?") },
            text = { Text("Bu yolculuktaki ölçümler kaydedilmeden silinir.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    vm.cancelTrip()
                }) { Text("İptal et") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
internal fun ActionBar(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            content()
        }
    }
}

@Composable
internal fun MessageCard(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(text)
            TextButton(onClick = onDismiss) { Text("Tamam") }
        }
    }
}

@Composable
private fun TripHero(
    plan: TripPlan,
    trip: TripState,
    nowMs: Long,
    notes: List<String>,
    modifier: Modifier = Modifier
) {
    val arriveMs = toMs(plan.arriveCampus)
    val remainMin = ((arriveMs - nowMs) / 60000.0).let { if (it < 0) 0L else Math.round(it) }
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
            Text("Kampüste olacağın saat", style = MaterialTheme.typography.titleMedium)
            Text(
                plan.arriveCampus.hhmm(),
                style = TabularNums,
                fontSize = 76.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Kalan ~$remainMin dk",
                style = MaterialTheme.typography.titleMedium.merge(TabularNums)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "Çıkış ${epochToHhmm(trip.startMs)} · geçen ${formatElapsed(nowMs - trip.startMs)}",
                style = MaterialTheme.typography.bodyMedium.merge(TabularNums)
            )
            val pred = trip.predictedArriveMs
            if (pred != null) {
                Text(
                    "Çıkışta tahmin ${epochToHhmm(pred)} (${formatDiff((arriveMs - pred) / 60000.0)})",
                    style = MaterialTheme.typography.bodySmall.merge(TabularNums),
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(200, easing = UiEaseOut)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (notes.isNotEmpty()) Spacer(Modifier.size(10.dp))
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

@Composable
private fun NextCard(step: TripStep, nowMs: Long, modifier: Modifier = Modifier) {
    val secs = (toMs(step.expected) - nowMs) / 1000L
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Sıradaki", style = MaterialTheme.typography.labelMedium)
            Text(
                TripSteps.targets[step.index],
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.size(6.dp))
            Text(
                if (secs <= 0) "şimdi" else formatDelta(secs),
                style = TabularNums,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            val detail = listOf(step.expected.hhmm(), step.info).filter { it.isNotBlank() }.joinToString(" · ")
            Text(detail, style = MaterialTheme.typography.bodyMedium.merge(TabularNums))
        }
    }
}

@Composable
private fun StepRow(step: TripStep, isCurrent: Boolean, modifier: Modifier = Modifier) {
    val container by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(150, easing = UiEaseOut),
        label = "stepColor"
    )
    val done = step.actual != null || step.skipped
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(200, easing = UiEaseOut)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${step.index + 1}.",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    TripSteps.labels[step.index],
                    modifier = Modifier.weight(1f),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                )
                if (!done) {
                    Text(
                        "~${step.expected.hhmm()}",
                        style = MaterialTheme.typography.bodyMedium.merge(TabularNums),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            val sub: String? = when {
                step.skipped -> "Atlandı"
                step.actual != null -> {
                    val diff = (toMs(step.actual) - toMs(step.expected)) / 60000.0
                    "${step.actual.hhmmss()} · beklenen ${step.expected.hhmm()} (${formatDiff(diff)})"
                }
                step.info.isNotBlank() -> step.info
                else -> null
            }
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall.merge(TabularNums),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
