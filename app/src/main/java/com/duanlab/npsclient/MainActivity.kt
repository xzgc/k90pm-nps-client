package com.duanlab.npsclient

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duanlab.npsclient.ui.theme.NPSClientTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var preferences: SharedPreferences

    private var serverHostState = mutableStateOf("")
    private var serverPortState = mutableStateOf("")
    private var vkeyState = mutableStateOf("")
    private var connectionTypeState = mutableStateOf("tcp")
    private var autoStartState = mutableStateOf(false)
    private var shellRunningState = mutableStateOf(false)
    private var logOutputEnabledState = mutableStateOf(true)
    private var validationError by mutableStateOf<String?>(null)
    private val logText = MutableStateFlow("")

    private lateinit var mService: ShellService
    private var mBound by mutableStateOf(false)
    private var bindingRegistered = false
    private var serviceCollection: Job? = null
    private var lastError by mutableStateOf<String?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            mService = (service as ShellService.LocalBinder).getService()
            mBound = true
            serviceCollection?.cancel()
            serviceCollection = lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch { mService.logText.collect { logText.value = it } }
                    launch { mService.activeConfigs.collect { shellRunningState.value = it.isNotEmpty() } }
                    launch { mService.lastError.collect { lastError = it } }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceCollection?.cancel()
            mBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences(PreferencesKey.PREF_NAME, Context.MODE_PRIVATE)
        serverHostState.value = preferences.getString(PreferencesKey.SERVER_HOST, "") ?: ""
        serverPortState.value = preferences.getInt(PreferencesKey.SERVER_PORT, 0)
            .takeIf { it in 1..65535 }?.toString() ?: ""
        vkeyState.value = preferences.getString(PreferencesKey.VKEY, "") ?: ""
        connectionTypeState.value = preferences.getString(PreferencesKey.CONNECTION_TYPE, "tcp")
            ?.takeIf { it in NpcCommand.SUPPORTED_TYPES } ?: "tcp"
        autoStartState.value = preferences.getBoolean(PreferencesKey.AUTO_START, false)
        shellRunningState.value = preferences.getBoolean(PreferencesKey.SHELL_RUNNING, false)
        logOutputEnabledState.value = preferences.getBoolean(PreferencesKey.LOG_OUTPUT_ENABLED, true)

        NpsConfig.fromPreferences(preferences)?.let { savedConfig ->
            if (shellRunningState.value) {
                startShell(savedConfig)
            }
        }

        checkNotificationPermission()
        enableEdgeToEdge()
        setContent {
            NPSClientTheme {
                MainContent()
            }
        }
        Intent(this, ShellService::class.java).also {
            bindingRegistered = bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainContent() {
        val clipboardManager = LocalClipboardManager.current
        val log by logText.collectAsState()
        val logScrollState = rememberScrollState()
        var typeMenuExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(log) {
            logScrollState.animateScrollTo(logScrollState.maxValue)
        }

        val statusText = when {
            lastError != null -> stringResource(R.string.status_error)
            shellRunningState.value && log.contains("Successful connection with server") ->
                stringResource(R.string.status_connected)
            shellRunningState.value -> stringResource(R.string.status_connecting)
            else -> stringResource(R.string.status_stopped)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.app_name))
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} / NPC ${BuildConfig.NpsVersion}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        Text(
                            text = stringResource(R.string.about),
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable {
                                    startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                                }
                        )
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.connection_status),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (lastError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                    Switch(
                        checked = shellRunningState.value,
                        enabled = mBound,
                        onCheckedChange = { shouldRun ->
                            if (shouldRun) saveAndConnect() else stopShell()
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.auto_start_switch))
                    Switch(
                        checked = autoStartState.value,
                        onCheckedChange = ::setAutoStart
                    )
                }

                OutlinedButton(
                    onClick = {
                        startActivity(Intent(this@MainActivity, KeepAliveActivity::class.java))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.keep_alive_settings))
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = serverHostState.value,
                        onValueChange = { serverHostState.value = it },
                        label = { Text(stringResource(R.string.server_host_label)) },
                        modifier = Modifier.weight(1.7f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    OutlinedTextField(
                        value = serverPortState.value,
                        onValueChange = { serverPortState.value = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.server_port_label)) },
                        modifier = Modifier.weight(0.8f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                OutlinedTextField(
                    value = vkeyState.value,
                    onValueChange = { vkeyState.value = it },
                    label = { Text(stringResource(R.string.vkey_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { typeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.connection_type_label))
                        Spacer(modifier = Modifier.width(12.dp))
                        Spacer(modifier = Modifier.weight(1f))
                        Text(connectionTypeState.value.uppercase())
                        Text("  ▾")
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        NpcCommand.SUPPORTED_TYPES.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.uppercase()) },
                                onClick = {
                                    connectionTypeState.value = type
                                    typeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                (validationError ?: lastError)?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = ::saveAndConnect,
                    enabled = mBound,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (shellRunningState.value) R.string.save_and_reconnect
                            else R.string.save_and_connect
                        )
                    )
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.log),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = logOutputEnabledState.value,
                        onCheckedChange = { enabled ->
                            logOutputEnabledState.value = enabled
                            preferences.edit()
                                .putBoolean(PreferencesKey.LOG_OUTPUT_ENABLED, enabled)
                                .apply()
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(enabled = mBound, onClick = { mService.clearLog() }) {
                        Text(stringResource(R.string.clear))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { clipboardManager.setText(AnnotatedString(log)) }) {
                        Text(stringResource(R.string.copy))
                    }
                }

                SelectionContainer {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(logScrollState)
                    ) {
                        Text(
                            text = if (logOutputEnabledState.value) {
                                log.ifEmpty { getString(R.string.no_log) }
                            } else {
                                getString(R.string.no_log)
                            },
                            textAlign = TextAlign.Start,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    private fun saveAndConnect() {
        val host = serverHostState.value.trim()
        val port = serverPortState.value.toIntOrNull()
        val vkey = vkeyState.value.trim()
        val type = connectionTypeState.value

        validationError = when {
            host.isEmpty() -> getString(R.string.error_server_required)
            host.contains("://") || host.any(Char::isWhitespace) ->
                getString(R.string.error_server_invalid)
            port == null || port !in 1..65535 -> getString(R.string.error_port_invalid)
            vkey.isEmpty() -> getString(R.string.error_vkey_required)
            type !in NpcCommand.SUPPORTED_TYPES -> getString(R.string.error_type_invalid)
            else -> null
        }
        if (validationError != null) return

        val config = NpsConfig(host, requireNotNull(port), vkey, type)
        NpsConfig.save(preferences, config)
        preferences.edit().putBoolean(PreferencesKey.CONNECT_ON_START, true).apply()
        if (mBound && shellRunningState.value) {
            mService.clearLog()
        }
        startShell(config)
    }

    private fun setAutoStart(enabled: Boolean) {
        autoStartState.value = enabled
        preferences.edit().putBoolean(PreferencesKey.AUTO_START, enabled).apply()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                BootGuard.setEnabled(applicationContext, enabled)
            }
            val message = if (result.ok) {
                getString(
                    if (enabled) R.string.auto_start_root_enabled
                    else R.string.auto_start_disabled
                )
            } else {
                getString(
                    R.string.auto_start_root_failed,
                    result.combined().ifBlank { result.exitCode.toString() }
                )
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun startShell(config: NpsConfig) {
        val intent = Intent(this, ShellService::class.java).apply {
            action = ShellServiceAction.START
            putParcelableArrayListExtra(IntentExtraKey.NpsConfig, arrayListOf(config))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun stopShell() {
        if (!mBound) return
        preferences.edit().putBoolean(PreferencesKey.CONNECT_ON_START, false).apply()
        val configs = ArrayList(mService.activeConfigs.value)
        if (configs.isEmpty()) return
        val intent = Intent(this, ShellService::class.java).apply {
            action = ShellServiceAction.STOP
            putParcelableArrayListExtra(IntentExtraKey.NpsConfig, configs)
        }
        startService(intent)
    }

    private fun checkNotificationPermission() {
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        serviceCollection?.cancel()
        super.onDestroy()
        if (bindingRegistered) {
            unbindService(connection)
            bindingRegistered = false
        }
        mBound = false
    }
}
