package com.duanlab.npsclient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AutoStartBroadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_BOOT && intent.action != ACTION_ROOT_BOOT) return

        val preferences = context.getSharedPreferences(PreferencesKey.PREF_NAME, Context.MODE_PRIVATE)
        val autoStart = preferences.getBoolean(PreferencesKey.AUTO_START, false)
        val connectOnStart = preferences.getBoolean(PreferencesKey.CONNECT_ON_START, true)
        val config = NpsConfig.fromPreferences(preferences)
        if (!autoStart || !connectOnStart || config == null) return

        val mainIntent = Intent(context, ShellService::class.java).apply {
            action = ShellServiceAction.START
            putParcelableArrayListExtra(IntentExtraKey.NpsConfig, arrayListOf(config))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(mainIntent)
        } else {
            context.startService(mainIntent)
        }
    }

    companion object {
        private const val ACTION_BOOT = "android.intent.action.BOOT_COMPLETED"
        private const val ACTION_ROOT_BOOT = "com.k90pm.npsclient.ROOT_BOOT"
    }
}
