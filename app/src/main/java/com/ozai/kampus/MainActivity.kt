package com.ozai.kampus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ozai.kampus.ui.KampusApp
import com.ozai.kampus.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KampusApp(vm) }

        // Foreground-only: clock tick every second, EGO refresh on start and every 30 s.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    while (true) {
                        vm.tick()
                        delay(1_000L)
                    }
                }
                vm.refresh()
                while (true) {
                    delay(30_000L)
                    vm.refresh()
                }
            }
        }
    }
}
