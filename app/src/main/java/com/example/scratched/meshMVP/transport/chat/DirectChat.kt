package com.example.scratched.meshMVP.transport.chat

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.scratched.meshMVP.transport.BluetoothGattServerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

enum class ChatPhase { CLOSED, SETUP, WAITING, READY, ENDED }
data class ChatMessage(val text: String, val outgoing: Boolean)
data class ChatState(
    val phase: ChatPhase = ChatPhase.CLOSED,
    val peer: String? = null,
    val status: String = "",
    val sending: Boolean = false,
    val messages: List<ChatMessage> = emptyList()
)

/** All session changes run on the UI thread. Each callback belongs to exactly one link. */
@SuppressLint("MissingPermission")
class DirectChat(context: Context, private val advertiser: BluetoothGattServerManager) {
    private val app = context.applicationContext
    private val manager get() = app.getSystemService(BluetoothManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(ChatState())
    val state = mutable.asStateFlow()
    private var link: ChatLink? = null
    private var incoming = ChatFrames()
    private val frames = ArrayDeque<ByteArray>()
    private var outgoing = ""
    private val timeout = Runnable { end("Телефон не ответил вовремя") }

    fun host() = begin(null)
    fun connect(address: String) = begin(address)

    private fun begin(address: String?) {
        close()
        mutable.value = ChatState(ChatPhase.SETUP, address, "Подготовка соединения…")
        guarded {
            val bluetooth = checkNotNull(manager)
            val next = if (address == null) HostLink(this, app, bluetooth)
                else ClientLink(this, app, bluetooth.adapter.getRemoteDevice(address))
            link = next
            armTimeout()
            next.open()
        }
    }

    internal fun event(source: ChatLink, action: () -> Unit) {
        handler.post { if (source === link) guarded(action) }
    }

    internal fun listen() {
        handler.removeCallbacks(timeout)
        mutable.value = mutable.value.copy(phase = ChatPhase.WAITING, status = "Ожидание собеседника · ${advertiser.advertisedDeviceName}")
        advertiser.startAdvertising()
    }

    fun advertisingFailed(reason: String) {
        if (link is HostLink && mutable.value.phase == ChatPhase.WAITING) end(reason)
    }

    internal fun accepting(address: String) {
        mutable.value = mutable.value.copy(phase = ChatPhase.SETUP, peer = address, status = "Подключение…")
        advertiser.stopAdvertising()
        armTimeout()
    }

    internal fun ready() {
        handler.removeCallbacks(timeout)
        mutable.value = mutable.value.copy(phase = ChatPhase.READY, status = "Соединение установлено")
    }

    fun send(text: String) {
        if (mutable.value.phase != ChatPhase.READY || mutable.value.sending || text.isBlank()) return
        if (text.toByteArray().size > ChatFrames.MAX_BYTES) return
        val encoded = ChatFrames.encode(text)
        outgoing = text
        frames.addAll(encoded)
        mutable.value = mutable.value.copy(sending = true)
        guarded { writeNext() }
    }

    private fun writeNext() {
        armTimeout()
        if (link?.write(frames.removeFirst()) != true) end("Не удалось отправить сообщение")
    }

    internal fun acknowledged() {
        if (!mutable.value.sending) return
        if (frames.isNotEmpty()) return writeNext()
        handler.removeCallbacks(timeout)
        append(ChatMessage(outgoing, true))
        mutable.value = mutable.value.copy(sending = false)
    }

    internal fun receive(bytes: ByteArray) {
        check(mutable.value.phase == ChatPhase.READY)
        incoming.accept(bytes)?.let { append(ChatMessage(it, false)) }
    }

    private fun append(message: ChatMessage) {
        mutable.value = mutable.value.copy(messages = (mutable.value.messages + message).takeLast(100))
    }

    private fun armTimeout() {
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, 20_000L)
    }

    fun interrupt(reason: String) {
        if (link != null) end(reason)
    }

    internal fun end(reason: String) {
        release()
        mutable.value = mutable.value.copy(phase = ChatPhase.ENDED, status = reason, sending = false)
    }

    fun close() {
        release()
        mutable.value = ChatState()
    }

    private fun release() {
        handler.removeCallbacks(timeout)
        val old = link
        link = null
        if (old is HostLink) advertiser.stopAdvertising()
        old?.close()
        incoming = ChatFrames()
        frames.clear()
        outgoing = ""
    }

    private inline fun guarded(action: () -> Unit) {
        try { action() }
        catch (e: SecurityException) { end("Нет разрешения Bluetooth") }
        catch (e: RuntimeException) { end("Ошибка обмена по Bluetooth") }
    }
}

internal interface ChatLink {
    fun open()
    fun write(bytes: ByteArray): Boolean
    fun close()
}
