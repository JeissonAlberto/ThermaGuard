package com.jeissonalberto.thermaguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jeissonalberto.thermaguard.domain.AndroidSettingsTarget
import com.jeissonalberto.thermaguard.domain.ThermalViewModel
import com.jeissonalberto.thermaguard.service.ThermalMonitorWorker
import com.jeissonalberto.thermaguard.service.UpdateWorker
import com.jeissonalberto.thermaguard.ui.AlertsScreen
import com.jeissonalberto.thermaguard.ui.DashboardScreen
import com.jeissonalberto.thermaguard.ui.DiagnosisScreen
import com.jeissonalberto.thermaguard.ui.theme.ThermaGuardTheme

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Alerts remain visible in-app if permission is declined. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        // WorkManager keeps the six-hour update check alive after the UI closes.
        UpdateWorker.schedule(applicationContext)
        ThermalMonitorWorker.schedule(applicationContext)
        setContent {
            ThermaGuardTheme {
                val viewModel: ThermalViewModel = viewModel()
                ThermaGuardApp(viewModel, ::openAndroidSettings)
            }
        }
    }

    /** Opens only user-controlled Android screens; no protected setting is changed by the app. */
    private fun openAndroidSettings(target: AndroidSettingsTarget) {
        val intent = Intent(target.action)
        when (target) {
            AndroidSettingsTarget.APP_DETAILS -> {
                intent.data = Uri.fromParts("package", packageName, null)
            }
            AndroidSettingsTarget.NOTIFICATIONS -> {
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
            AndroidSettingsTarget.BATTERY_SAVER -> Unit
        }
        val fallback = Intent(Settings.ACTION_SETTINGS)
        runCatching {
            when {
                intent.resolveActivity(packageManager) != null -> startActivity(intent)
                fallback.resolveActivity(packageManager) != null -> startActivity(fallback)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun ThermaGuardApp(
    viewModel: ThermalViewModel,
    onOpenSettings: (AndroidSettingsTarget) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = listOf(
        Triple("dashboard", "⌂", "INICIO"),
        Triple("alerts", "!", "ALERTAS"),
        Triple("diagnosis", "✓", "DIAGNÓSTICO")
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { (route, icon, label) ->
                    NavigationBarItem(
                        selected = currentRoute == route,
                        onClick = {
                            navController.navigate(route) {
                                launchSingleTop = true
                            }
                        },
                        icon = { Text(icon) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(contentPadding)
        ) {
            composable("dashboard") { DashboardScreen(viewModel, onOpenSettings) }
            composable("alerts") { AlertsScreen(viewModel) }
            composable("diagnosis") { DiagnosisScreen(viewModel) }
        }
    }
}
