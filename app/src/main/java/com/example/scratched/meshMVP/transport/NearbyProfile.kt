package com.example.scratched.meshMVP.transport

import java.nio.ByteBuffer
import java.util.Locale

/** Thirteen bytes fit alongside a 128-bit service UUID in a legacy scan response. */
data class NearbyProfile(val id: Int, val name: String) {
    val tag: String get() = String.format(Locale.ROOT, "%04X", id and 0xffff)
    fun encode(): ByteArray {
        require(validName(name))
        val bytes = name.toByteArray(alphabet)
        return ByteBuffer.allocate(5 + bytes.size).put(2.toByte()).putInt(id).put(bytes).array()
    }

    companion object {
        private val alphabet = charset("windows-1251")
        private val allowed = Regex("[A-Za-zА-Яа-яЁё0-9 _-]{1,8}")
        fun initial(id: Int): NearbyProfile {
            val names = listOf("Лиса", "Филин", "Рысь", "Енот", "Сокол", "Кит", "Олень", "Барс")
            return NearbyProfile(id, names[id and 7])
        }

        fun validName(name: String) = allowed.matches(name) && name == name.trim() && name.isNotEmpty()
        fun decode(bytes: ByteArray?): NearbyProfile? {
            if (bytes == null || bytes.size !in 6..13 || bytes[0] != 2.toByte()) return null
            val name = String(bytes, 5, bytes.size - 5, alphabet)
            return if (validName(name)) NearbyProfile(ByteBuffer.wrap(bytes, 1, 4).int, name) else null
        }
    }
}
