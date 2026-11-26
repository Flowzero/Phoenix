package com.example.scratched.transport

/** All callbacks run inside dispatch; onReady means transport readiness, not session readiness. */

internal interface LinkListener {
    fun dispatch(source: Transport, action: () -> Unit)
    fun onListening(source: Transport)
    fun onConnected(source: Transport)
    fun onReady(source: Transport)
    fun onDataReceived(source: Transport, bytes: ByteArray)
    fun onDataSent(source: Transport)
    fun onDisconnected(source: Transport)
    fun onError(source: Transport, reason: String)
}