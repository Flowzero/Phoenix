package com.example.scratched.user

import java.nio.ByteBuffer
import java.util.Locale

data class UserProfile(
    val id: Int,
    val name: String
) {
    companion object {
        private const val FORMAT_VERSION: Byte = 2
        private const val HEADER_SIZE = 5 // 1 byte: version + 4 bytes: ID
        private const val MAX_NAME_LENGTH = 8

        private val NAME_CHARSET = charset("windows-1251")
        private val ALLOWED_NAME = Regex("[A-Za-zА-Яа-яЁё0-9 _-]{1,8}")

        private val DEFAULT_NAMES = listOf(
            "Лиса", "Филин", "Рысь", "Енот",
            "Сокол", "Кит", "Олень", "Барс"
        )

        fun initial(id: Int): UserProfile {
            val defaultName = DEFAULT_NAMES[id and 7]
            return UserProfile(id = id, name = defaultName)
        }

        fun validName(name: String): Boolean {
            return name == name.trim() && ALLOWED_NAME.matches(name)
        }

        fun decode(bytes: ByteArray?): UserProfile? {
            if (bytes == null) return null

            val nameSize = bytes.size - HEADER_SIZE
            if (nameSize !in 1..MAX_NAME_LENGTH) return null

            val buffer = ByteBuffer.wrap(bytes)
            if (buffer.get() != FORMAT_VERSION) return null

            val id = buffer.getInt()
            val name = String(bytes, HEADER_SIZE, nameSize, NAME_CHARSET)

            if (!validName(name)) return null

            return UserProfile(id = id, name = name)
        }
    }

    // Short code (number) right next to the name
    // Purpose: distinguish people with the same name
    val tag: String
        get() = String.format(Locale.ROOT, "%04X", id and 0xFFFF)

    fun encode(): ByteArray {
        require(validName(name)) { "This name is unavailable: $name" }

        val nameBytes = name.toByteArray(NAME_CHARSET)

        return ByteBuffer.allocate(HEADER_SIZE + nameBytes.size)
            .put(FORMAT_VERSION)
            .putInt(id)
            .put(nameBytes)
            .array()
    }
}