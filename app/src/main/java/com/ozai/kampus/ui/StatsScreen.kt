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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ozai.kampus.engine.Segment

@Composable
fun StatsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val stats = vm.stats
    var addFor by remember { mutableStateOf<Segment?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "İstatistikler",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(Segment.values().toList()) { seg ->
            val samples = stats.samples[seg].orEmpty()
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(seg.title, fontWeight = FontWeight.Bold)
                    val avg = if (samples.isEmpty()) null else samples.average()
                    Text(
                        "${stats.count(seg)} ölçüm" +
                            (if (avg != null) " · ortalama ${formatMin(avg)} dk" else ""),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Kullanılan tahmin: ${formatMin(stats.estimate(seg))} dk (" +
                            (if (stats.isCalibrated(seg)) "öğrenildi" else "varsayılan / az ölçüm") + ")",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (samples.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    }
                    samples.forEachIndexed { i, v ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${i + 1}. ${formatMin(v)} dk")
                            IconButton(onClick = { vm.deleteSample(seg, i) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Sil",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { addFor = seg }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Ölçüm ekle")
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tüm ölçümleri sıfırla")
            }
        }
    }

    val target = addFor
    if (target != null) {
        AddSampleDialog(
            segment = target,
            onDismiss = { addFor = null },
            onAdd = { minutes ->
                vm.addSample(target, minutes)
                addFor = null
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Tüm ölçümler silinsin mi?") },
            text = { Text("Öğrenilmiş süreler silinir ve varsayılanlara dönülür.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetStats()
                }) { Text("Sıfırla") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun AddSampleDialog(
    segment: Segment,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val value = text.trim().replace(',', '.').toDoubleOrNull()
    val valid = value != null && value >= 0.1 && value <= 180.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(segment.title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Süre (dakika)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { if (value != null && valid) onAdd(value) }
            ) { Text("Ekle") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        }
    )
}
