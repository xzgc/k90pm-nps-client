package com.duanlab.npsclient

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

internal object RootShell {
    data class Result(
        val ok: Boolean,
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        fun combined(): String = listOf(stdout, stderr)
            .filter(String::isNotBlank)
            .joinToString("\n")
    }

    fun run(command: String, timeoutSeconds: Long = 12): Result {
        var process: Process? = null
        return try {
            val running = ProcessBuilder("/system/bin/su", "-c", command)
                .redirectErrorStream(false)
                .start()
            process = running
            val stdout = Collector(running.inputStream).apply { start() }
            val stderr = Collector(running.errorStream).apply { start() }
            val finished = running.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                running.destroyForcibly()
                Result(false, -1, stdout.value(), "root command timed out")
            } else {
                val code = running.exitValue()
                Result(code == 0, code, stdout.value(), stderr.value())
            }
        } catch (error: Exception) {
            process?.destroyForcibly()
            Log.e("K90PM-Root", "Root command failed", error)
            Result(false, -1, "", "${error.javaClass.simpleName}: ${error.message}")
        }
    }

    fun isAvailable(): Boolean =
        run("id", 8).let { it.ok && it.stdout.contains("uid=0") }

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private class Collector(private val input: InputStream) : Thread() {
        private val output = StringBuilder()

        init {
            isDaemon = true
        }

        override fun run() {
            try {
                BufferedReader(InputStreamReader(input)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append('\n')
                    }
                }
            } catch (_: Exception) {
            }
        }

        @Synchronized
        fun value(): String {
            join(1500)
            return output.toString().trim()
        }
    }
}
