package com.example.scratched.feature.chat

import com.example.scratched.user.UserProfile

import android.content.ContentValues
import android.database.SQLException
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class Conversation(val peer: UserProfile, val last: ChatMessage)

/** One committed row per completed message, in local insertion order. */
class ChatHistory(context: Context) : SQLiteOpenHelper(
    context.applicationContext, File(context.noBackupFilesDir, "chat-history.db").absolutePath, null, 1
) {
    companion object {
        private var instance: ChatHistory? = null

        @Synchronized fun shared(context: Context): ChatHistory =
            instance ?: ChatHistory(context.applicationContext).also { instance = it }
    }

    private val writer = Executors.newSingleThreadExecutor()
    private val writeError = MutableStateFlow(false)
    val writeFailed = writeError.asStateFlow()

    fun save(peer: UserProfile, message: ChatMessage) {
        writer.execute {
            try {
                append(peer, message)
            } catch (failure: SQLException) {
                Log.e("ChatHistory", "Could not save message", failure)
                writeError.value = true
            }
        }
    }

    private val revision = MutableStateFlow(0L)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE messages (
            id INTEGER PRIMARY KEY, peer_id INTEGER NOT NULL, peer_name TEXT NOT NULL,
            body TEXT NOT NULL, outgoing INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX messages_by_peer ON messages(peer_id, id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
        error("Unsupported chat database version: $oldVersion → $newVersion")

    @Synchronized
    fun append(peer: UserProfile, message: ChatMessage) {
        val values = ContentValues().apply {
            put("peer_id", peer.id)
            put("peer_name", peer.name)
            put("body", message.text)
            put("outgoing", message.outgoing)
        }
        writableDatabase.insertOrThrow("messages", null, values)
        revision.value++
    }

    val conversations = revision.map {
        readableDatabase.rawQuery("""SELECT peer_id, peer_name, body, outgoing FROM messages
            WHERE id IN (SELECT MAX(id) FROM messages GROUP BY peer_id) ORDER BY id DESC""", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Conversation(UserProfile(cursor.getInt(0), cursor.getString(1)),
                    ChatMessage(cursor.getString(2), cursor.getInt(3) != 0)))
            }
        }
    }.flowOn(Dispatchers.IO)

    fun messages(peerId: Int, limit: Int) = revision.map {
        readableDatabase.rawQuery("""SELECT body, outgoing FROM messages
            WHERE peer_id = ? ORDER BY id DESC LIMIT ?""", arrayOf(peerId.toString(), limit.toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(ChatMessage(cursor.getString(0), cursor.getInt(1) != 0))
            }
        }
    }.flowOn(Dispatchers.IO)
}
