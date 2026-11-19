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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozai.kampus.data.TripState
import com.ozai.kampus.data.TripSteps

@Composable
fun TripScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val trip = vm.trip
    var confirmCancel by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

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
                    "Yolculuğu kaydet",
                    modifier = Modifier.softItem(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val msg = vm.message
            if (msg != null) {
                item(key = "message") {
                    Card(
                        modifier = Modifier
                            .softItem()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(msg)
                            TextButton(onClick = { vm.dismissMessage() }) { Text("Tamam") }
                        }
                    }
                }
            }

            if (trip == null) {
                item(key = "intro") {
                    Card(
                        modifier = Modifier
                            .softItem()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            "Evden çıkarken kaydı başlat, yol boyunca her noktaya gelince " +
                                "\"Şimdi\"ye bas. Ölçümler tahminleri kişiselleştirir. " +
                                "Uygulamayı kapatabilirsin; süre gerçek saatle devam eder.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                item(key = "elapsed") { ElapsedCard(trip, vm.nowMs, Modifier.softItem()) }

                val steps = TripSteps.labels
                val current = trip.marks.size
                for (i in steps.indices) {
                    item(key = "step_$i") {
                        StepRow(
                            index = i,
                            label = steps[i],
                            trip = trip,
                            isCurrent = i == current,
                            modifier = Modifier.softItem()
                        )
                    }
                }

                item(key = "finish") {
                    Row(
                        Modifier
                            .softItem()
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppButton(
                            onClick = { vm.finishTrip() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Bitir ve kaydet")
                        }
                        AppOutlinedButton(
                            onClick = { confirmCancel = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("İptal et")
                        }
                    }
                }
            }
        }

        // The main action never moves: same spot, same size, whatever step you are on.
        if (trip == null) {
            ActionBar {
                AppButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.startTrip()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text("Evden çıktım, başlat", fontSize = 18.sp)
                }
            }
        } else {
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
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Kayıt iptal edilsin mi?") },
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
private fun ActionBar(content: @Composable () -> Unit) {
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
private fun ElapsedCard(trip: TripState, nowMs: Long, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Geçen süre", style = MaterialTheme.typography.titleMedium)
            Text(
                formatElapsed(nowMs - trip.startMs),
                style = TabularNums,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Başlangıç: ${epochToHhmmss(trip.startMs)}",
                style = MaterialTheme.typography.bodyMedium.merge(TabularNums)
            )
        }
    }
}

@Composable
private fun StepRow(
    index: Int,
    label: String,
    trip: TripState,
    isCurrent: Boolean,
    modifier: Modifier = Modifier
) {
    val done = index < trip.marks.size
    val container by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(150, easing = UiEaseOut),
        label = "stepColor"
    )
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
                    "${index + 1}.",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (done) {
                val mark = trip.marks[index]
                if (mark == null) {
                    Text(
                        "Atlandı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    var prev = trip.startMs
                    for (j in 0 until index) {
                        val p = trip.marks[j]
                        if (p != null) prev = p
                    }
                    Text(
                        "${epochToHhmmss(mark)} (+${formatDelta((mark - prev) / 1000L)})",
                        style = MaterialTheme.typography.bodySmall.merge(TabularNums),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
