package com.duanlab.npsclient

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.duanlab.npsclient.ui.theme.NPSClientTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KeepAliveActivity : ComponentActivity() {
    private var autoStartValue by mutableStateOf(false)
    private var rootAvailable by mutableStateOf<Boolean?>(null)
    private var rootGuardInstalled by mutableStateOf(false)
    private var batteryOptimizationIgnored by mutableStateOf(false)
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        autoStartValue = getSharedPreferences(PreferencesKey.PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(PreferencesKey.AUTO_START, false)
        enableEdgeToEdge()
        setContent {
            NPSClientTheme {
                KeepAliveContent()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        batteryOptimizationIgnored = isBatteryOptimizationIgnored()
        refreshRootStatus()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun KeepAliveContent() {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(R.string.keep_alive_title)) })
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.auto_start_switch))
                    Switch(
                        checked = autoStartValue,
                        enabled = !busy,
                        onCheckedChange = ::setAutoStart
                    )
                }

                HorizontalDivider()

                StatusRow(
                    label = stringResource(R.string.root_status_label),
                    value = when (rootAvailable) {
                        null -> stringResource(R.string.root_checking)
                        true -> stringResource(R.string.root_authorized)
                        false -> stringResource(R.string.root_unavailable)
                    },
                    color = statusColor(rootAvailable == true)
                )
                StatusRow(
                    label = stringResource(R.string.root_guard_label),
                    value = if (rootGuardInstalled) {
                        stringResource(R.string.root_guard_installed)
                    } else {
                        stringResource(R.string.root_guard_missing)
                    },
                    color = statusColor(rootGuardInstalled)
                )
                StatusRow(
                    label = stringResource(R.string.battery_optimization_label),
                    value = if (batteryOptimizationIgnored) {
                        stringResource(R.string.battery_optimization_ignored)
                    } else {
                        stringResource(R.string.battery_optimization_restricted)
                    },
                    color = statusColor(batteryOptimizationIgnored)
                )

                HorizontalDivider()

                OutlinedButton(
                    onClick = ::openBatteryOptimizationSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.open_battery_optimization))
                }
                OutlinedButton(
                    onClick = ::openHyperOsAutoStartSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.open_hyperos_autostart))
                }
                OutlinedButton(
                    onClick = ::openHyperOsPowerSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.open_hyperos_power))
                }
                OutlinedButton(
                    onClick = ::openAppDetails,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.open_app_details))
                }
                OutlinedButton(
                    onClick = ::openRootManager,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.open_root_manager))
                }

                Button(
                    onClick = ::applyRootKeepAlive,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.apply_root_keepalive))
                }
                OutlinedButton(
                    onClick = { refreshRootStatus() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.refresh_status))
                }
            }
        }
    }

    @Composable
    private fun StatusRow(label: String, value: String, color: Color) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label)
            Spacer(modifier = Modifier.width(12.dp))
            Text(value, color = color)
        }
    }

    @Composable
    private fun statusColor(ok: Boolean): Color =
        if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    private fun setAutoStart(enabled: Boolean) {
        autoStartValue = enabled
        getSharedPreferences(PreferencesKey.PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PreferencesKey.AUTO_START, enabled).apply()
        lifecycleScope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                BootGuard.setEnabled(applicationContext, enabled)
            }
            busy = false
            Toast.makeText(
                this@KeepAliveActivity,
                if (result.ok) {
                    getString(
                        if (enabled) R.string.auto_start_root_enabled
                        else R.string.auto_start_disabled
                    )
                } else {
                    getString(
                        R.string.auto_start_root_failed,
                        result.combined().ifBlank { result.exitCode.toString() }
                    )
                },
                Toast.LENGTH_LONG
            ).show()
            refreshRootStatus()
        }
    }

    private fun applyRootKeepAlive() {
        lifecycleScope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) {
                if (!RootShell.isAvailable()) {
                    RootShell.Result(false, -1, "", "root unavailable")
                } else {
                    if (autoStartValue) {
                        BootGuard.setEnabled(applicationContext, true)
                    }
                    BootGuard.applyBackgroundPermissions(packageName)
                }
            }
            busy = false
            Toast.makeText(
                this@KeepAliveActivity,
                if (result.ok) {
                    getString(R.string.root_keepalive_applied)
                } else {
                    getString(
                        R.string.root_keepalive_failed,
                        result.combined().ifBlank { result.exitCode.toString() }
                    )
                },
                Toast.LENGTH_LONG
            ).show()
            refreshRootStatus()
        }
    }

    private fun refreshRootStatus() {
        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                val available = RootShell.isAvailable()
                Pair(available, if (available) BootGuard.isInstalled() else false)
            }
            rootAvailable = status.first
            rootGuardInstalled = status.second
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val manager = getSystemService(PowerManager::class.java)
        return manager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun openBatteryOptimizationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            openAppDetails()
        }
    }

    private fun openHyperOsAutoStartSettings() {
        val intent = Intent("miui.intent.action.OP_AUTO_START").setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        )
        if (!tryStart(intent)) openAppDetails()
    }

    private fun openHyperOsPowerSettings() {
        val extras = arrayOf(
            "package_name" to packageName,
            "package_label" to getString(R.string.app_name),
            "pkg" to packageName
        )
        val candidates = listOf(
            Intent().setComponent(
                ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.powercenter.legacypowerrank.PowerDetailActivity"
                )
            ),
            Intent("miui.intent.action.POWER_BATTERY")
        )
        for (intent in candidates) {
            extras.forEach { (name, value) -> intent.putExtra(name, value) }
            if (tryStart(intent)) return
        }
        openAppDetails()
    }

    private fun openAppDetails() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (_: Exception) {
            Toast.makeText(
                this,
                getString(R.string.system_settings_unavailable),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openRootManager() {
        val managers = listOf(
            "com.sukisu.ultra",
            "me.weishu.kernelsu",
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk"
        )
        for (packageName in managers) {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null && tryStart(launchIntent)) return
        }
        Toast.makeText(
            this,
            getString(R.string.root_manager_unavailable),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun tryStart(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
