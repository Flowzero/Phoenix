package com.example.scratched

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.scratched.meshMVP.transport.chat.*

@Composable
fun DirectChatScreen(state: ChatState, send: (String) -> Unit, close: () -> Unit, modifier: Modifier = Modifier) {
    val peer = state.peer ?: return
    var draft by rememberSaveable(peer.id) { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    BackHandler(onBack = close)
    Column(modifier.imePadding().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "К людям рядом") }
            ProfileAvatar(peer)
            Column(Modifier.padding(start = 12.dp)) {
                Text(peer.name, style = MaterialTheme.typography.titleLarge)
                Text(if (state.phase == ChatPhase.READY) "Рядом · ${peer.tag}" else state.status,
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp))
        if (state.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(if (state.phase == ChatPhase.READY) "Начните разговор" else state.status, color = colors.onSurfaceVariant)
            }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true,
            contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.messages.asReversed()) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.outgoing) Arrangement.End else Arrangement.Start) {
                    Surface(shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 300.dp),
                        color = if (message.outgoing) colors.primaryContainer else colors.surfaceContainerHigh) {
                        Text(message.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
        }
        if (state.phase == ChatPhase.ENDED) {
            OutlinedButton(onClick = close, modifier = Modifier.fillMaxWidth()) { Text("Вернуться к людям рядом") }
        } else {
            val tooLong = draft.toByteArray().size > ChatFrames.MAX_BYTES
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text("Сообщение") },
                    shape = RoundedCornerShape(22.dp), maxLines = 4, isError = tooLong)
                FilledIconButton(onClick = { send(draft); draft = "" },
                    enabled = state.phase == ChatPhase.READY && !state.sending && draft.isNotBlank() && !tooLong) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Отправить")
                }
            }
            if (tooLong) Text("Сократите сообщение до ${ChatFrames.MAX_BYTES} байт", color = colors.error)
        }
    }
}
