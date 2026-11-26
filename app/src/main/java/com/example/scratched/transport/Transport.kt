package com.example.scratched.transport

/**
 *
 */

internal interface Transport {
    fun open()
    fun write(bytes: ByteArray): Boolean
    fun close()
}