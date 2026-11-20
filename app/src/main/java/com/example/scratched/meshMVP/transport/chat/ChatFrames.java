package com.example.scratched.meshMVP.transport.chat;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Ordered GATT writes/indications: one final flag and up to 19 bytes at the default MTU. */
public final class ChatFrames {
    public static final int MAX_BYTES = 1024;
    private static final int PAYLOAD = 19;
    private final ByteArrayOutputStream received = new ByteArrayOutputStream();

    public static List<byte[]> encode(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException();
        List<byte[]> frames = new ArrayList<>();
        for (int offset = 0; offset < bytes.length; offset += PAYLOAD) {
            int count = Math.min(PAYLOAD, bytes.length - offset);
            byte[] frame = new byte[count + 1];
            frame[0] = (byte) (offset + count == bytes.length ? 1 : 0);
            System.arraycopy(bytes, offset, frame, 1, count);
            frames.add(frame);
        }
        return frames;
    }

    /** Returns a message only after its final frame. Discard the session on invalid input. */
    public String accept(byte[] frame) {
        if (frame.length < 2 || frame.length > PAYLOAD + 1 ||
                (frame[0] != 0 && frame[0] != 1) ||
                received.size() + frame.length - 1 > MAX_BYTES) throw new IllegalArgumentException();
        received.write(frame, 1, frame.length - 1);
        if (frame[0] == 0) return null;
        byte[] message = received.toByteArray();
        received.reset();
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(message)).toString();
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException(invalid);
        }
    }
}
