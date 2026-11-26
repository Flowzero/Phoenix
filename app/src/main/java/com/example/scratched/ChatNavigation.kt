package com.example.scratched

import android.database.SQLException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scratched.feature.chat.ChatState
import kotlinx.coroutines.flow.catch

@Composable
internal fun ChatNavigation(viewModel: MainViewModel, modifier: Modifier, home: @Composable () -> Unit) {
    var browsing by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    val live by viewModel.chat.state.collectAsState()
    val saveFailed by viewModel.chat.history.writeFailed.collectAsState()
    Column(modifier) {
        if (saveFailed) Text("Часть сообщений не удалось сохранить на этом устройстве.",
            Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
        if (!browsing) {
            Box(Modifier.weight(1f)) { home() }
            if (live.peer == null) {
                HorizontalDivider()
                TextButton(onClick = { browsing = true }, modifier = Modifier.fillMaxWidth()) { Text("Переписки") }
            }
            return@Column
        }

        var readFailed by remember { mutableStateOf(false) }
        val history = viewModel.chat.history
        val threads by remember(history) {
            history.conversations.catch {
                if (it !is SQLException) throw it
                readFailed = true
                emit(emptyList())
            }
        }.collectAsState(initial = emptyList())
        val thread = threads.find { it.peer.id == selected }
        if (thread != null) {
            val people by viewModel.people.collectAsState()
            val nearby = people.find { it.profile.id == thread.peer.id }
            val connected = live.peer?.id == thread.peer.id
            val resume: (() -> Unit)? = when {
                connected -> ({ selected = null; browsing = false })
                nearby != null && live.peer == null -> ({
                    viewModel.openConversation(nearby)
                    selected = null
                    browsing = false
                })
                else -> null
            }
            DirectChatScreen(ChatState(peer = thread.peer,
                status = if (nearby != null || connected) "Сохранённая переписка · рядом" else "Сохранённая переписка"),
                history, {}, { selected = null }, Modifier.fillMaxSize(), saved = true, connect = resume)
        } else {
            val back = { selected = null; browsing = false }
            BackHandler(onBack = back)
            TextButton(onClick = back) { Text("Назад") }
            Text("Переписки", Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.headlineLarge)
            if (threads.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(if (readFailed) "Не удалось загрузить переписки" else "Здесь появятся ваши разговоры")
                }
            } else LazyColumn {
                items(threads, key = { it.peer.id }) { row ->
                    Surface(onClick = { selected = row.peer.id }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            ProfileAvatar(row.peer)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text("${row.peer.name} · ${row.peer.tag}", style = MaterialTheme.typography.titleMedium)
                                Text((if (row.last.outgoing) "Вы: " else "") + row.last.text,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
