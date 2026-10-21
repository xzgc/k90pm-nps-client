package com.duanlab.npsclient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NpcCommandTest {
    @Test
    fun buildsTcpArgumentsFromStructuredSettings() {
        val config = NpsConfig(
            serverHost = "nps.example.com",
            serverPort = 28320,
            vkey = "demo_vkey",
            connectionType = "tcp"
        )

        assertEquals(
            listOf(
                "-server=nps.example.com:28320",
                "-vkey=demo_vkey",
                "-type=tcp",
                "-debug=false",
                "-log=stdout",
                "-log_color=false"
            ),
            NpcCommand.build(config)
        )
    }

    @Test
    fun normalizesTypeAndSupportsIpv6() {
        val config = NpsConfig(
            serverHost = "2408:8000::1",
            serverPort = 8024,
            vkey = "secret",
            connectionType = " WSS "
        )

        val args = NpcCommand.build(config)
        assertEquals("-server=[2408:8000::1]:8024", args[0])
        assertEquals("-type=wss", args[2])
    }

    @Test
    fun redactsTheConnectionPassword() {
        val args = listOf("-server=host:8024", "-vkey=secret", "-type=tcp")
        assertEquals(
            listOf("-server=host:8024", "-vkey=***", "-type=tcp"),
            NpcCommand.redact(args)
        )
    }

    @Test
    fun rejectsInvalidSettings() {
        val valid = NpsConfig("host", 8024, "secret", "tcp")
        assertThrows(IllegalArgumentException::class.java) {
            NpcCommand.build(valid.copy(serverHost = ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NpcCommand.build(valid.copy(serverHost = "https://host"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NpcCommand.build(valid.copy(serverPort = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NpcCommand.build(valid.copy(vkey = ""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NpcCommand.build(valid.copy(connectionType = "http"))
        }
    }
}
