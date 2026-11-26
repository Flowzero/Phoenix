package com.example.scratched.transport

/** Single-use connection. write returns whether the asynchronous send was accepted. */

internal interface Transport {
    fun open()
    fun write(bytes: ByteArray): Boolean
    fun close()
}