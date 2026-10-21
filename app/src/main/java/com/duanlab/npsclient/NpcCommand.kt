package com.duanlab.npsclient

/** Builds argv for the bundled NPC executable without invoking a shell. */
internal object NpcCommand {
    val SUPPORTED_TYPES = listOf("tcp", "kcp", "tls", "wss", "ws", "quic")

    fun build(config: NpsConfig): List<String> {
        val host = config.serverHost.trim()
        val vkey = config.vkey.trim()
        val type = config.connectionType.trim().lowercase()
        require(host.isNotEmpty() && !host.contains("://")) { "Invalid server host" }
        require(config.serverPort in 1..65535) { "Invalid server port" }
        require(vkey.isNotEmpty()) { "Missing vkey" }
        require(type in SUPPORTED_TYPES) { "Unsupported connection type" }

        return listOf(
            "-server=${config.copy(serverHost = host).serverAddress}",
            "-vkey=$vkey",
            "-type=$type",
            "-debug=false",
            "-log=stdout",
            "-log_color=false"
        )
    }

    fun redact(args: List<String>): List<String> = args.map { arg ->
        if (arg.startsWith("-vkey=")) "-vkey=***" else arg
    }
}
