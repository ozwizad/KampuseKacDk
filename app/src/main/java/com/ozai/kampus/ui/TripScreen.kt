package com.ozai.kampus.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozai.kampus.data.TripState
import com.ozai.kampus.data.TripSteps

@Composable
fun TripScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val trip = vm.trip
    var confirmCancel by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Yolculuğu kaydet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        val msg = vm.message
        if (msg != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Evden çıkarken kaydı başlat, yol boyunca kontrol noktalarına bas. " +
                                "Ölçümler tahminleri kişiselleştirir. Uygulamayı kapatabilirsin; " +
                                "süre gerçek saatle devam eder.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { vm.startTrip() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Evden çıktım, başlat")
                        }
                    }
                }
            }
        } else {
            item { ElapsedCard(trip, vm.nowMs) }

            val steps = TripSteps.labels
            val current = trip.marks.size
            for (i in steps.indices) {
                item(key = "step_$i") {
                    StepRow(
                        index = i,
                        label = steps[i],
                        trip = trip,
                        isCurrent = i == current,
                        onMark = { vm.markStep() },
                        onSkip = { vm.skipStep() }
                    )
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { vm.finishTrip() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bitir ve kaydet")
                    }
                    OutlinedButton(
                        onClick = { confirmCancel = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("İptal et")
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
private fun ElapsedCard(trip: TripState, nowMs: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Başlangıç: ${epochToHhmmss(trip.startMs)}",
                style = MaterialTheme.typography.bodyMedium
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
    onMark: () -> Unit,
    onSkip: () -> Unit
) {
    val done = index < trip.marks.size
    val container = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isCurrent) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onMark, modifier = Modifier.weight(1f)) {
                        Text("Şimdi")
                    }
                    OutlinedButton(onClick = onSkip) {
                        Text("Atla")
                    }
                }
            }
        }
    }
}
