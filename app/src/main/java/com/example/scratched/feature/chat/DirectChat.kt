package com.example.scratched.feature.chat

import com.example.scratched.user.UserProfile
import com.example.scratched.transport.Transport
import com.example.scratched.transport.LinkListener
import com.example.scratched.transport.meshBLE.HostLinkManager
import com.example.scratched.transport.meshBLE.ClientLinkManager
import android.util.Log
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.scratched.transport.meshBLE.BleAdvertiser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

enum class ChatPhase { CLOSED, SETUP, WAITING, READY, ENDED }
data class ChatMessage(val text: String, val outgoing: Boolean)
data class ChatState(
    val phase: ChatPhase = ChatPhase.CLOSED,
    val peer: UserProfile? = null,
    val status: String = "",
    val sending: Boolean = false
)

/** All session changes run on the UI thread. Each callback belongs to exactly one link. */
@SuppressLint("MissingPermission")
class DirectChat(context: Context, private val advertiser: BleAdvertiser) {
    private val app = context.applicationContext
    private val manager get() = app.getSystemService(BluetoothManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(ChatState())
    val state = mutable.asStateFlow()
    private var link: Transport? = null
    private var identityWritten = false
    private var identityReceived = false
    private var incoming = ChatFrames()
    private val frames = ArrayDeque<ByteArray>()
    private var outgoing = ""
    private val timeout = Runnable { end("Телефон не ответил вовремя") }

    val history = ChatHistory.shared(app)

    fun host() = begin(null, null)
    fun connect(address: String, peer: UserProfile) = begin(address, peer)

    private fun begin(address: String?, peer: UserProfile?) {
        close()
        mutable.value = ChatState(ChatPhase.SETUP, peer, "Подключение…")
        guarded {
            val bluetooth = checkNotNull(manager)
            val next = if (address == null) HostLinkManager(app, listener, bluetooth)
                else ClientLinkManager(app, listener, bluetooth.adapter.getRemoteDevice(address))
            link = next
            armTimeout()
            next.open()
        }
    }

    // Transport callbacks are serialized on the main thread and scoped to the active link.
    private val listener = object : LinkListener {
        override fun dispatch(source: Transport, action: () -> Unit) {
            handler.post { if (source === link) guarded(action) }
        }

        override fun onListening(source: Transport) = listen()

        override fun onConnected(source: Transport) {
            if (source is HostLinkManager) accepting()
        }

        override fun onReady(source: Transport) {
            // The client initiates the profile exchange; the host waits for it.
            if (source is ClientLinkManager) {
                check(source.write(introduction())) { "Failed to send client profile" }
            }
        }

        override fun onDataReceived(source: Transport, bytes: ByteArray) {
            if (!identityReceived) {
                identify(bytes)
                identityReceived = true
                if (source is HostLinkManager) {
                    check(source.write(introduction())) { "Failed to send host profile" }
                }
                finishHandshake()
            } else receive(bytes)
        }

        override fun onDataSent(source: Transport) {
            if (!identityWritten) {
                identityWritten = true
                finishHandshake()
            } else acknowledged()
        }

        override fun onDisconnected(source: Transport) = end("Соединение завершено")

        override fun onError(source: Transport, reason: String) = end(reason)
    }

    private fun finishHandshake() {
        if (identityWritten && identityReceived && mutable.value.phase == ChatPhase.SETUP) {
            ready()
        }
    }

    internal fun listen() {
        handler.removeCallbacks(timeout)
        mutable.value = mutable.value.copy(phase = ChatPhase.WAITING, status = "Ожидание собеседника")
        advertiser.startAdvertising()
    }

    fun advertisingFailed(reason: String) {
        if (link is HostLinkManager && mutable.value.phase == ChatPhase.WAITING) end(reason)
    }

    internal fun accepting() {
        mutable.value = mutable.value.copy(phase = ChatPhase.SETUP, status = "Подключение…")
        advertiser.stopAdvertising()
        armTimeout()
    }

    internal fun introduction() = advertiser.profile.encode()

    internal fun identify(bytes: ByteArray) {
        val peer = requireNotNull(UserProfile.decode(bytes))
        val expected = mutable.value.peer
        check(peer.id != advertiser.profile.id && (expected == null || expected.id == peer.id))
        mutable.value = mutable.value.copy(peer = peer)
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
        check(mutable.value.peer != null)
        incoming.accept(bytes)?.let { append(ChatMessage(it, false)) }
    }

    private fun append(message: ChatMessage) {
        val peer = checkNotNull(mutable.value.peer)
        history.save(peer, message)
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
        if (old is HostLinkManager) advertiser.stopAdvertising()
        old?.close()
        identityWritten = false
        identityReceived = false
        incoming = ChatFrames()
        frames.clear()
        outgoing = ""
    }

    private inline fun guarded(action: () -> Unit) {
        try { action() }
        catch (e: SecurityException) {
            Log.e("DirectChat", "Bluetooth permission error", e)
            end("Нет разрешения Bluetooth")
        }
        catch (e: RuntimeException) {
            Log.e("DirectChat", "Bluetooth communication error", e)
            end("Ошибка обмена по Bluetooth")
        }
    }
}

