package com.ozai.kampus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ozai.kampus.ui.KampusApp
import com.ozai.kampus.ui.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    private var askedNotif = false
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KampusApp(vm) }

        // Ask for notification permission once, the first time a trip is running (Android 13+).
        lifecycleScope.launch {
            snapshotFlow { vm.trip != null }.collect { running ->
                if (running && Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    !askedNotif
                ) {
                    askedNotif = true
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

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
