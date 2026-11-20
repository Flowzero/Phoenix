package com.example.scratched

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.scratched.meshMVP.transport.chat.*

@Composable
fun DirectChatScreen(state: ChatState, send: (String) -> Unit, close: () -> Unit, modifier: Modifier = Modifier) {
    var draft by remember { mutableStateOf("") }
    BackHandler(onBack = close)
    Column(modifier.imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Прямой чат", style = MaterialTheme.typography.headlineSmall)
        Text(listOfNotNull(state.peer, state.status).joinToString(" · "))
        if (state.phase == ChatPhase.WAITING) {
            Text("На втором телефоне запустите сканирование и выберите это устройство.")
        }
        LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
            items(state.messages.asReversed()) { message ->
                Text((if (message.outgoing) "Вы: " else "Собеседник: ") + message.text,
                    modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        if (state.phase == ChatPhase.READY) {
            OutlinedTextField(value = draft, onValueChange = { draft = it }, maxLines = 3,
                label = { Text("Сообщение") }, modifier = Modifier.fillMaxWidth())
            val bytes = draft.toByteArray().size
            if (bytes > ChatFrames.MAX_BYTES) Text("Максимум ${ChatFrames.MAX_BYTES} байт, сейчас $bytes")
            Button(onClick = { send(draft); draft = "" },
                enabled = !state.sending && draft.isNotBlank() && bytes <= ChatFrames.MAX_BYTES) {
                Text(if (state.sending) "Отправляется…" else "Отправить")
            }
        }
        TextButton(onClick = close) { Text("Закрыть чат") }
    }
}
