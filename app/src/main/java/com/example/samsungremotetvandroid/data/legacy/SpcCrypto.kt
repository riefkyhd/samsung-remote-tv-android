package com.example.samsungremotetvandroid.data.legacy

import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject

internal object SpcCrypto {
    data class ServerHelloResult(
        val serverHelloHex: String,
        val aesKey: ByteArray
    )

    data class ParsedClientHello(
        val skPrime: ByteArray,
        val ctxUpperHex: String
    )

    fun generateServerHello(
        userId: String,
        pin: String
    ): ServerHelloResult {
        val aesKey = sha1(pin.toByteArray(Charsets.UTF_8)).copyOfRange(0, 16)

        val pubKeyData = hexToBytes(SpcKeys.PUBLIC_KEY)
        val encrypted = aesCbcEncryptNoPadding(
            key = aesKey,
            iv = ByteArray(16),
            data = pubKeyData
        )
        val swapped = wbKeyEncrypt(encrypted)

        val userIdData = userId.toByteArray(Charsets.UTF_8)
        val payload = ByteArrayBuilder()
            .append(uint32BE(userIdData.size))
            .append(userIdData)
            .append(swapped)
            .toByteArray()

        val serverHelloBytes = ByteArrayBuilder()
            .append(byteArrayOf(0x01, 0x02))
            .append(ByteArray(5))
            .append(uint32BE(userIdData.size + 132))
            .append(payload)
            .append(ByteArray(5))
            .toByteArray()

        return ServerHelloResult(
            serverHelloHex = bytesToHex(serverHelloBytes).uppercase(),
            aesKey = aesKey
        )
    }

    fun parseClientHello(
        clientHelloHex: String,
        aesKey: ByteArray,
        userId: String
    ): ParsedClientHello? {
        val data = hexToBytesOrNull(clientHelloHex) ?: return null
        if (data.size < 15) {
            throw IllegalStateException("SPC client hello is too short")
        }

        val userIdLen = readUInt32BE(data, offset = 11)
        val userIdPos = 15
        val gxSize = 128

        val gxStart = userIdPos + userIdLen
        val gxEnd = gxStart + gxSize
        if (data.size < gxEnd + 20) {
            throw IllegalStateException("SPC client hello payload is truncated")
        }

        val pEncWbGx = data.copyOfRange(gxStart, gxEnd)
        val pEncGx = wbKeyDecrypt(pEncWbGx)
        val pGx = aesCbcDecryptNoPadding(
            key = aesKey,
            iv = ByteArray(16),
            data = pEncGx
        )

        val bnPGx = BigInteger(1, pGx)
        val bnPrivate = BigInteger(SpcKeys.PRIVATE_KEY, 16)
        val bnPrime = BigInteger(SpcKeys.PRIME, 16)

        val secretInt = bnPGx.modPow(bnPrivate, bnPrime)
        val secret = toFixedSize(secretInt.toByteArray(), 128)

        val dataHash2 = data.copyOfRange(gxEnd, gxEnd + 20)
        val pinCheck = ByteArrayBuilder()
            .append(userId.toByteArray(Charsets.UTF_8))
            .append(secret)
            .toByteArray()
        if (!sha1(pinCheck).contentEquals(dataHash2)) {
            return null
        }

        val finalBuf = ByteArrayBuilder()
            .append(userId.toByteArray(Charsets.UTF_8))
            .append(userId.toByteArray(Charsets.UTF_8))
            .append(pGx)
            .append(hexToBytes(SpcKeys.PUBLIC_KEY))
            .append(secret)
            .toByteArray()

        val skPrime = sha1(finalBuf)
        val skPrimeInput = ByteArrayBuilder()
            .append(skPrime)
            .append(byteArrayOf(0x00))
            .toByteArray()
        val skPrimeHash = sha1(skPrimeInput).copyOfRange(0, 16)
        val ctx = applySamyGoKeyTransform(skPrimeHash)

        return ParsedClientHello(
            skPrime = skPrime,
            ctxUpperHex = bytesToHex(ctx).uppercase()
        )
    }

    fun generateServerAcknowledge(skPrime: ByteArray): String {
        val hashInput = ByteArrayBuilder()
            .append(skPrime)
            .append(byteArrayOf(0x01))
            .toByteArray()
        val hash = sha1(hashInput)
        return "0103000000000000000014${bytesToHex(hash).uppercase()}0000000000"
    }

    fun generateCommand(
        ctxUpperHex: String,
        sessionId: Int,
        keyCode: String
    ): String {
        val keyData = hexToBytesOrNull(ctxUpperHex)
            ?: throw IllegalStateException("Invalid SPC context key")
        if (keyData.size != 16) {
            throw IllegalStateException(
                "Invalid SPC context key length (${keyData.size} bytes). Reconnect and pair again."
            )
        }

        val innerBody = JSONObject()
            .put("plugin", "RemoteControl")
            .put("param1", "uuid:12345")
            .put("param2", "Click")
            .put("param3", keyCode)
            .put("param4", false)
            .put("api", "SendRemoteKey")
            .put("version", "1.000")

        val inner = JSONObject()
            .put("method", "POST")
            .put("body", innerBody)

        val encrypted = aesEcbEncryptNoPadding(
            key = keyData,
            data = pkcs7Pad(inner.toString().toByteArray(Charsets.UTF_8))
        )
        val bodyInts = encrypted.map { value -> value.toInt() and 0xFF }

        val args = JSONObject()
            .put("Session_Id", sessionId)
            .put("body", bodyInts)

        val payload = JSONObject()
            .put("name", "callCommon")
            .put("args", listOf(args))

        return "5::/com.samsung.companion:${payload}"
    }

    private fun wbKeyEncrypt(input: ByteArray): ByteArray {
        val key = hexToBytes(SpcKeys.WB_KEY)
        return input.asSequence()
            .chunked(16)
            .flatMap { block ->
                aesCbcEncryptNoPadding(
                    key = key,
                    iv = ByteArray(16),
                    data = block.toByteArray()
                ).asIterable()
            }
            .toList()
            .toByteArray()
    }

    private fun wbKeyDecrypt(input: ByteArray): ByteArray {
        val key = hexToBytes(SpcKeys.WB_KEY)
        return input.asSequence()
            .chunked(16)
            .flatMap { block ->
                aesCbcDecryptNoPadding(
                    key = key,
                    iv = ByteArray(16),
                    data = block.toByteArray()
                ).asIterable()
            }
            .toList()
            .toByteArray()
    }

    private fun applySamyGoKeyTransform(input: ByteArray): ByteArray {
        if (input.size != 16) {
            throw IllegalStateException("Invalid SPC transform input length.")
        }
        val transKey = hexToBytes(SpcKeys.TRANS_KEY)
        val cipher = ReducedRoundRijndael(transKey)
        return cipher.encryptBlock(input)
    }

    private fun aesCbcEncryptNoPadding(
        key: ByteArray,
        iv: ByteArray,
        data: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(iv)
        )
        return cipher.doFinal(data)
    }

    private fun aesCbcDecryptNoPadding(
        key: ByteArray,
        iv: ByteArray,
        data: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(iv)
        )
        return cipher.doFinal(data)
    }

    private fun aesEcbEncryptNoPadding(
        key: ByteArray,
        data: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(data)
    }

    private fun pkcs7Pad(data: ByteArray): ByteArray {
        val pad = 16 - (data.size % 16)
        return ByteArrayBuilder()
            .append(data)
            .append(ByteArray(pad) { pad.toByte() })
            .toByteArray()
    }

    private fun sha1(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-1").digest(data)
    }

    private fun toFixedSize(raw: ByteArray, size: Int): ByteArray {
        val unsigned = if (raw.size > size && raw.first() == 0.toByte()) {
            raw.copyOfRange(1, raw.size)
        } else {
            raw
        }

        return when {
            unsigned.size == size -> unsigned
            unsigned.size > size -> unsigned.copyOfRange(unsigned.size - size, unsigned.size)
            else -> ByteArray(size - unsigned.size) + unsigned
        }
    }

    private fun uint32BE(value: Int): ByteArray {
        return byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun readUInt32BE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }

    private fun hexToBytes(value: String): ByteArray {
        return hexToBytesOrNull(value)
            ?: throw IllegalStateException("Invalid hex value")
    }

    private fun hexToBytesOrNull(value: String): ByteArray? {
        val cleaned = value.replace(" ", "").trim()
        if (cleaned.length % 2 != 0) {
            return null
        }
        return runCatching {
            ByteArray(cleaned.length / 2) { index ->
                cleaned.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    private fun bytesToHex(data: ByteArray): String {
        return buildString(data.size * 2) {
            data.forEach { byte ->
                append(((byte.toInt() and 0xFF) ushr 4).toString(16))
                append((byte.toInt() and 0x0F).toString(16))
            }
        }
    }

    private object SpcKeys {
        const val PUBLIC_KEY = "2cb12bb2cbf7cec713c0fff7b59ae68a96784ae517f41d259a45d20556177c0ffe951ca60ec03a990c9412619d1bee30adc7773088c5721664cffcedacf6d251cb4b76e2fd7aef09b3ae9f9496ac8d94ed2b262eee37291c8b237e880cc7c021fb1be0881f3d0bffa4234d3b8e6a61530c00473ce169c025f47fcc001d9b8051"
        const val PRIVATE_KEY = "2fd6334713816fae018cdee4656c5033a8d6b00e8eaea07b3624999242e96247112dcd019c4191f4643c3ce1605002b2e506e7f1d1ef8d9b8044e46d37c0d5263216a87cd783aa185490436c4a0cb2c524e15bc1bfeae703bcbc4b74a0540202e8d79cadaae85c6f9c218bc1107d1f5b4b9bd87160e782f4e436eeb17485ab4d"
        const val PRIME = "b361eb0ab01c3439f2c16ffda7b05e3e320701ebee3e249123c3586765fd5bf6c1dfa88bb6bb5da3fde74737cd88b6a26c5ca31d81d18e3515533d08df619317063224cf0943a2f29a5fe60c1c31ddf28334ed76a6478a1122fb24c4a94c8711617ddfe90cf02e643cd82d4748d6d4a7ca2f47d88563aa2baf6482e124acd7dd"
        const val WB_KEY = "abbb120c09e7114243d1fa0102163b27"
        const val TRANS_KEY = "6c9474469ddf7578f3e5ad8a4c703d99"
    }

    private class ReducedRoundRijndael(key: ByteArray) {
        private val roundKeys: IntArray = expandKey(key)

        fun encryptBlock(input: ByteArray): ByteArray {
            if (input.size != 16) {
                throw IllegalStateException("Invalid block length for SPC transform.")
            }

            val state = input.copyOf()
            addRoundKey(state, round = 0)

            for (round in 1 until NUM_ROUNDS) {
                subBytes(state)
                shiftRows(state)
                mixColumns(state)
                addRoundKey(state, round = round)
            }

            subBytes(state)
            shiftRows(state)
            addRoundKey(state, round = NUM_ROUNDS)
            return state
        }

        private fun addRoundKey(state: ByteArray, round: Int) {
            val base = round * 4
            for (column in 0 until 4) {
                val word = roundKeys[base + column]
                val index = column * 4
                state[index] = (state[index].toInt() xor ((word ushr 24) and 0xFF)).toByte()
                state[index + 1] = (state[index + 1].toInt() xor ((word ushr 16) and 0xFF)).toByte()
                state[index + 2] = (state[index + 2].toInt() xor ((word ushr 8) and 0xFF)).toByte()
                state[index + 3] = (state[index + 3].toInt() xor (word and 0xFF)).toByte()
            }
        }

        private fun subBytes(state: ByteArray) {
            for (i in state.indices) {
                state[i] = S_BOX[state[i].toInt() and 0xFF].toByte()
            }
        }

        private fun shiftRows(state: ByteArray) {
            val source = state.copyOf()
            for (row in 0..3) {
                for (column in 0..3) {
                    state[column * 4 + row] = source[((column + row) % 4) * 4 + row]
                }
            }
        }

        private fun mixColumns(state: ByteArray) {
            for (column in 0..3) {
                val idx = column * 4
                val a0 = state[idx].toInt() and 0xFF
                val a1 = state[idx + 1].toInt() and 0xFF
                val a2 = state[idx + 2].toInt() and 0xFF
                val a3 = state[idx + 3].toInt() and 0xFF

                state[idx] = (mul2(a0) xor mul3(a1) xor a2 xor a3).toByte()
                state[idx + 1] = (a0 xor mul2(a1) xor mul3(a2) xor a3).toByte()
                state[idx + 2] = (a0 xor a1 xor mul2(a2) xor mul3(a3)).toByte()
                state[idx + 3] = (mul3(a0) xor a1 xor a2 xor mul2(a3)).toByte()
            }
        }

        private fun expandKey(key: ByteArray): IntArray {
            if (key.size != 16) {
                throw IllegalStateException("Invalid SPC transform key length.")
            }

            val totalWords = 4 * (NUM_ROUNDS + 1)
            val words = IntArray(totalWords)
            for (i in 0 until 4) {
                val offset = i * 4
                words[i] = ((key[offset].toInt() and 0xFF) shl 24) or
                    ((key[offset + 1].toInt() and 0xFF) shl 16) or
                    ((key[offset + 2].toInt() and 0xFF) shl 8) or
                    (key[offset + 3].toInt() and 0xFF)
            }

            var rcon = 0x01
            for (i in 4 until totalWords) {
                var temp = words[i - 1]
                if (i % 4 == 0) {
                    temp = subWord(rotWord(temp)) xor (rcon shl 24)
                    rcon = xtime(rcon)
                }
                words[i] = words[i - 4] xor temp
            }
            return words
        }

        private fun rotWord(value: Int): Int {
            return ((value shl 8) or ((value ushr 24) and 0xFF))
        }

        private fun subWord(value: Int): Int {
            val b0 = S_BOX[(value ushr 24) and 0xFF]
            val b1 = S_BOX[(value ushr 16) and 0xFF]
            val b2 = S_BOX[(value ushr 8) and 0xFF]
            val b3 = S_BOX[value and 0xFF]
            return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }

        private fun xtime(value: Int): Int {
            return if ((value and 0x80) != 0) {
                ((value shl 1) xor 0x11B) and 0xFF
            } else {
                (value shl 1) and 0xFF
            }
        }

        private fun mul2(value: Int): Int {
            return if ((value and 0x80) != 0) {
                ((value shl 1) xor 0x1B) and 0xFF
            } else {
                (value shl 1) and 0xFF
            }
        }

        private fun mul3(value: Int): Int = mul2(value) xor value

        private companion object {
            private const val NUM_ROUNDS = 3
            private val S_BOX = intArrayOf(
                99, 124, 119, 123, 242, 107, 111, 197, 48, 1, 103, 43, 254, 215, 171, 118,
                202, 130, 201, 125, 250, 89, 71, 240, 173, 212, 162, 175, 156, 164, 114, 192,
                183, 253, 147, 38, 54, 63, 247, 204, 52, 165, 229, 241, 113, 216, 49, 21,
                4, 199, 35, 195, 24, 150, 5, 154, 7, 18, 128, 226, 235, 39, 178, 117,
                9, 131, 44, 26, 27, 110, 90, 160, 82, 59, 214, 179, 41, 227, 47, 132,
                83, 209, 0, 237, 32, 252, 177, 91, 106, 203, 190, 57, 74, 76, 88, 207,
                208, 239, 170, 251, 67, 77, 51, 133, 69, 249, 2, 127, 80, 60, 159, 168,
                81, 163, 64, 143, 146, 157, 56, 245, 188, 182, 218, 33, 16, 255, 243, 210,
                205, 12, 19, 236, 95, 151, 68, 23, 196, 167, 126, 61, 100, 93, 25, 115,
                96, 129, 79, 220, 34, 42, 144, 136, 70, 238, 184, 20, 222, 94, 11, 219,
                224, 50, 58, 10, 73, 6, 36, 92, 194, 211, 172, 98, 145, 149, 228, 121,
                231, 200, 55, 109, 141, 213, 78, 169, 108, 86, 244, 234, 101, 122, 174, 8,
                186, 120, 37, 46, 28, 166, 180, 198, 232, 221, 116, 31, 75, 189, 139, 138,
                112, 62, 181, 102, 72, 3, 246, 14, 97, 53, 87, 185, 134, 193, 29, 158,
                225, 248, 152, 17, 105, 217, 142, 148, 155, 30, 135, 233, 206, 85, 40, 223,
                140, 161, 137, 13, 191, 230, 66, 104, 65, 153, 45, 15, 176, 84, 187, 22
            )
        }
    }

    private class ByteArrayBuilder {
        private val chunks = mutableListOf<ByteArray>()

        fun append(data: ByteArray): ByteArrayBuilder {
            chunks += data
            return this
        }

        fun toByteArray(): ByteArray {
            val total = chunks.sumOf { it.size }
            val output = ByteArray(total)
            var offset = 0
            chunks.forEach { chunk ->
                chunk.copyInto(
                    destination = output,
                    destinationOffset = offset,
                    startIndex = 0,
                    endIndex = chunk.size
                )
                offset += chunk.size
            }
            return output
        }
    }
}
