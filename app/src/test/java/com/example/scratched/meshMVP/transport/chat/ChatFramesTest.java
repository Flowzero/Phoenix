package com.example.scratched.meshMVP.transport.chat;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

import com.example.scratched.feature.chat.ChatFrames;

public class ChatFramesTest {
    @Test public void messagesRoundTripAtFrameAndSizeBoundaries() {
        ChatFrames receiver = new ChatFrames();
        for (String text : new String[]{"а", "a".repeat(19), "b".repeat(20),
                "x".repeat(18) + "🙂 Привет", "я".repeat(512)}) {
            List<byte[]> frames = ChatFrames.encode(text);
            for (int i = 0; i < frames.size(); i++) {
                assertTrue(frames.get(i).length <= 20);
                assertEquals(i == frames.size() - 1 ? text : null, receiver.accept(frames.get(i)));
            }
        }
    }

    @Test public void rejectsMalformedOrOversizeMessages() {
        assertThrows(IllegalArgumentException.class, () -> ChatFrames.encode(""));
        assertThrows(IllegalArgumentException.class, () -> ChatFrames.encode("я".repeat(513)));
        for (byte[] frame : new byte[][]{ {}, {1}, {2, 65}, {1, (byte)0xff}, new byte[21] }) {
            assertThrows(IllegalArgumentException.class, () -> new ChatFrames().accept(frame));
        }
        ChatFrames receiver = new ChatFrames();
        assertThrows(IllegalArgumentException.class, () -> {
            for (int i = 0; i < 54; i++) receiver.accept(new byte[20]);
        });
    }
}
