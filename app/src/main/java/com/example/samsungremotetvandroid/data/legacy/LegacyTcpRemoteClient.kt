package com.example.samsungremotetvandroid.data.legacy

import android.util.Base64
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

internal class LegacyTcpRemoteClient {
    private var socket: Socket? = null
    private var activePort: Int? = null

    fun connect(ipAddress: String, remoteName: String): Int {
        disconnect()

        var lastError: Throwable? = null
        for (port in LEGACY_PORTS) {
            try {
                openOnPort(ipAddress = ipAddress, remoteName = remoteName, port = port)
                return port
            } catch (error: Throwable) {
                lastError = error
                disconnect()
            }
        }

        throw IllegalStateException(
            "Legacy remote connection failed on ports ${LEGACY_PORTS.joinToString()}",
            lastError
        )
    }

    fun sendKey(keyCode: String) {
        val socketRef = socket ?: throw IllegalStateException("Legacy remote socket is not connected.")
        writePacket(
            socket = socketRef,
            packet = makeKeyPacket(keyCode)
        )
    }

    fun disconnect() {
        runCatching {
            socket?.close()
        }
        socket = null
        activePort = null
    }

    fun activePortOrNull(): Int? = activePort

    private fun openOnPort(
        ipAddress: String,
        remoteName: String,
        port: Int
    ) {
        val newSocket = Socket()
        newSocket.tcpNoDelay = true
        newSocket.soTimeout = HANDSHAKE_READ_TIMEOUT_MS
        newSocket.connect(
            InetSocketAddress(ipAddress, port),
            CONNECT_TIMEOUT_MS
        )

        writePacket(
            socket = newSocket,
            packet = makeHandshakePacket(remoteName)
        )
        writePacket(
            socket = newSocket,
            packet = makeAuthPacket()
        )

        val response = readOnce(newSocket)
        if (response != null && isAccessDenied(response)) {
            newSocket.close()
            throw IllegalStateException("TV denied legacy remote authorization.")
        }

        socket = newSocket
        activePort = port
    }

    private fun writePacket(
        socket: Socket,
        packet: ByteArray
    ) {
        socket.getOutputStream().write(packet)
        socket.getOutputStream().flush()
    }

    private fun readOnce(socket: Socket): ByteArray? {
        return try {
            val buffer = ByteArray(4096)
            val count = socket.getInputStream().read(buffer)
            if (count <= 0) {
                null
            } else {
                buffer.copyOf(count)
            }
        } catch (_: SocketTimeoutException) {
            null
        }
    }

    private fun makeHandshakePacket(remoteName: String): ByteArray {
        val description = "Android Samsung Remote"
        val identifier = "Android"
        val payload = mutableListOf<Byte>().apply {
            add(0x64)
            add(0x00)
            addAll(serializeString(description).toList())
            addAll(serializeString(identifier).toList())
            addAll(serializeString(remoteName).toList())
        }
        return makePacket(CONTROL_APP_STRING, payload.toByteArray())
    }

    private fun makeAuthPacket(): ByteArray {
        return makePacket(
            CONTROL_APP_STRING,
            byteArrayOf(0xC8.toByte(), 0x00)
        )
    }

    private fun makeKeyPacket(keyCode: String): ByteArray {
        val payload = mutableListOf<Byte>().apply {
            add(0x00)
            add(0x00)
            add(0x00)
            addAll(serializeString(keyCode).toList())
        }
        return makePacket(CONTROL_APP_STRING, payload.toByteArray())
    }

    private fun makePacket(
        appString: String,
        payload: ByteArray
    ): ByteArray {
        val packet = mutableListOf<Byte>()
        packet += 0x00
        packet += serializeData(
            value = appString.toByteArray(StandardCharsets.UTF_8),
            raw = true
        ).toList()
        packet += serializeData(value = payload, raw = true).toList()
        return packet.toByteArray()
    }

    private fun serializeString(value: String): ByteArray {
        return serializeData(
            value = value.toByteArray(StandardCharsets.UTF_8),
            raw = false
        )
    }

    private fun serializeData(
        value: ByteArray,
        raw: Boolean
    ): ByteArray {
        val payload = if (raw) {
            value
        } else {
            Base64.encode(value, Base64.NO_WRAP)
        }
        val length = payload.size.coerceAtMost(255).toByte()
        return byteArrayOf(length, 0x00) + payload.copyOf(payload.size.coerceAtMost(255))
    }

    private fun isAccessDenied(response: ByteArray): Boolean {
        if (response.isEmpty()) {
            return false
        }
        if (response[0] == 0x65.toByte()) {
            return true
        }
        return response.indexOfSlice(byteArrayOf(0x64, 0x00, 0x00, 0x00)) >= 0
    }

    private fun ByteArray.indexOfSlice(slice: ByteArray): Int {
        if (slice.isEmpty() || slice.size > size) {
            return -1
        }
        for (index in 0..(size - slice.size)) {
            var matched = true
            for (offset in slice.indices) {
                if (this[index + offset] != slice[offset]) {
                    matched = false
                    break
                }
            }
            if (matched) {
                return index
            }
        }
        return -1
    }

    private companion object {
        private const val CONTROL_APP_STRING = "iphone.iapp.samsung"
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val HANDSHAKE_READ_TIMEOUT_MS = 2_000
        private val LEGACY_PORTS = listOf(55_000, 55_001, 52_235)
    }
}
