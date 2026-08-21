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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
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
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        UpdateWorker.schedule(applicationContext)
        ThermalMonitorWorker.schedule(applicationContext)
        val thermalViewModel = ViewModelProvider(this)[ThermalViewModel::class.java]
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { thermalViewModel.setForegroundMonitoringActive(true) }
            override fun onStop(owner: LifecycleOwner) { thermalViewModel.setForegroundMonitoringActive(false) }
        })
        setContent { ThermaGuardTheme { ThermaGuardApp(thermalViewModel, ::openAndroidSettings) } }
    }

    private fun openAndroidSettings(target: AndroidSettingsTarget) {
        val intent = Intent(target.action)
        when (target) {
            AndroidSettingsTarget.APP_DETAILS -> intent.data = Uri.fromParts("package", packageName, null)
            AndroidSettingsTarget.NOTIFICATIONS -> intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private data class AppTab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
private fun ThermaGuardApp(viewModel: ThermalViewModel, onOpenSettings: (AndroidSettingsTarget) -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = listOf(
        AppTab("dashboard", "Inicio") { Icon(Icons.Outlined.Home, contentDescription = null) },
        AppTab("alerts", "Alertas") { Icon(Icons.Outlined.NotificationsNone, contentDescription = null) },
        AppTab("diagnosis", "Diagnóstico") { Icon(Icons.Outlined.Assessment, contentDescription = null) }
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = { navController.navigate(tab.route) { launchSingleTop = true; restoreState = true } },
                        icon = tab.icon,
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { contentPadding ->
        NavHost(navController = navController, startDestination = "dashboard", modifier = Modifier.padding(contentPadding)) {
            composable("dashboard") { DashboardScreen(viewModel, onOpenSettings) }
            composable("alerts") { AlertsScreen(viewModel) }
            composable("diagnosis") { DiagnosisScreen(viewModel) }
        }
    }
}
