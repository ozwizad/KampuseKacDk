package com.ozai.kampus.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Ana Sayfa", Icons.Default.Home),
    TRIP("Yolculuk", Icons.Default.PlayArrow),
    STATS("İstatistik", Icons.Default.Info)
}

@Composable
fun KampusApp(vm: MainViewModel) {
    KampusTheme {
        var tabIndex by rememberSaveable { mutableStateOf(0) }
        val tabs = Tab.values()
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tabIndex == i,
                            onClick = { tabIndex = i },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        ) { padding ->
            val mod = Modifier.padding(padding)
            when (tabs[tabIndex]) {
                Tab.HOME -> HomeScreen(vm, mod)
                Tab.TRIP -> TripScreen(vm, mod)
                Tab.STATS -> StatsScreen(vm, mod)
            }
        }
    }
}
