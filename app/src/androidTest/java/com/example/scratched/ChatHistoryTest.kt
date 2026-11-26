package com.example.scratched

import com.example.scratched.user.UserProfile

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.scratched.history.ChatHistory
import com.example.scratched.meshMVP.transport.chat.ChatMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatHistoryTest {
    @Test fun conversationsSurviveReopenAndStaySeparateById() = runBlocking {
        // The instrumentation package has separate storage from the installed messenger.
        val context = InstrumentationRegistry.getInstrumentation().context
        context.noBackupFilesDir.resolve("chat-history.db").delete()
        val anna = UserProfile(1, "Анна")
        val other = UserProfile(2, "Анна")
        val reply = ChatMessage("Привет 🌙", false)
        ChatHistory(context).use { db ->
            repeat(125) { db.append(anna, ChatMessage("Сообщение $it", true)) }
            db.append(other, ChatMessage("Другой диалог", false))
            db.append(anna.copy(name = "Аня"), reply)
        }
        ChatHistory(context).use { db ->
            val rows = db.conversations.first()
            assertEquals(listOf("Аня", "Анна"), rows.map { it.peer.name })
            assertEquals(reply, rows.first().last)
            assertEquals(101, db.messages(anna.id, 101).first().size)
            val messages = db.messages(anna.id, 200).first()
            assertEquals(126, messages.size)
            assertEquals(reply, messages.first())
            assertEquals(ChatMessage("Сообщение 0", true), messages.last())
            assertEquals(listOf(ChatMessage("Другой диалог", false)), db.messages(other.id, 200).first())
        }
    }
}
