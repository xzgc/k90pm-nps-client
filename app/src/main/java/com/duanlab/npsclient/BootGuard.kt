package com.duanlab.npsclient

import android.content.Context
import java.io.File

internal object BootGuard {
    private const val ROOT_DIR = "/data/adb/k90pm_nps"
    private const val SCRIPT_PATH = "/data/adb/service.d/k90pm_nps_autostart.sh"
    private const val FLAG_PATH = "$ROOT_DIR/autostart.enabled"

    fun setEnabled(context: Context, enabled: Boolean): RootShell.Result {
        return if (enabled) install(context) else remove()
    }

    fun isInstalled(): Boolean {
        val result = RootShell.run(
            "test -f $SCRIPT_PATH && test -f $FLAG_PATH && echo installed || echo missing",
            8
        )
        return result.ok && result.stdout.contains("installed")
    }

    fun applyBackgroundPermissions(packageName: String): RootShell.Result {
        val command = listOf(
            "cmd deviceidle whitelist +$packageName",
            "cmd appops set $packageName RUN_IN_BACKGROUND allow",
            "cmd appops set $packageName RUN_ANY_IN_BACKGROUND allow",
            "am set-standby-bucket $packageName active",
            "cmd deviceidle whitelist | grep -q $packageName"
        ).joinToString("; ")
        return RootShell.run(command, 20)
    }

    private fun install(context: Context): RootShell.Result {
        val source = File(context.cacheDir, "k90pm_nps_autostart.sh")
        context.resources.openRawResource(R.raw.root_autostart).use { input ->
            source.outputStream().use { output -> input.copyTo(output) }
        }
        source.setReadable(true, true)
        val command = listOf(
            "mkdir -p $ROOT_DIR /data/adb/service.d",
            "cp ${RootShell.quote(source.absolutePath)} $SCRIPT_PATH",
            "chmod 0755 $SCRIPT_PATH",
            "touch $FLAG_PATH",
            "chmod 0600 $FLAG_PATH"
        ).joinToString(" && ")
        return RootShell.run(command, 30)
    }

    private fun remove(): RootShell.Result =
        RootShell.run(
            "rm -f $SCRIPT_PATH $FLAG_PATH; rmdir $ROOT_DIR 2>/dev/null; true",
            12
        )
}
