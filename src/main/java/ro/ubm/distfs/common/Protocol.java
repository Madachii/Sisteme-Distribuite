package ro.ubm.distfs.common;

import ro.ubm.distfs.common.message.Message;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Line framing shared by all messages (docs/cerinte.md): one text line ending in {@code \n},
 * the message name followed by space-separated fields. TCP uses one request and one
 * response per connection; the heartbeat is a single multicast datagram.
 *
 * The message set and its fields are defined by
 * {@link ro.ubm.distfs.common.message.MessageType} and the {@link Message} records.
 */
public final class Protocol {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_LINE = 8 * 1024;

    private Protocol() {
    }

    public static void writeLine(OutputStream out, Message message) throws IOException {
        writeLine(out, message.encode());
    }

    public static void writeLine(OutputStream out, String... fields) throws IOException {
        out.write((String.join(" ", fields) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Reads one line byte by byte, so the bytes that follow it stay in {@code in}.
     */
    public static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException("Connection closed before end of line");
            }
            if (b == '\n') {
                return line.toString(StandardCharsets.UTF_8).strip();
            }
            if (line.size() >= MAX_LINE) {
                throw new IOException("Line longer than " + MAX_LINE + " bytes");
            }
            line.write(b);
        }
    }

    /**
     * File names are single fields on the line, so they cannot contain whitespace or '/'.
     */
    public static boolean isValidName(String name) {
        return !name.isEmpty() && name.chars().noneMatch(c -> Character.isWhitespace(c) || c == '/');
    }

    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String hex(MessageDigest digest) {
        return HexFormat.of().formatHex(digest.digest());
    }

    public static void copy(InputStream in, OutputStream out, long size, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long remaining = size;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw new IOException("Connection closed with " + remaining + " bytes left");
            }
            if (digest != null) {
                digest.update(buffer, 0, read);
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    public static String checksum(InputStream in, long size) throws IOException {
        MessageDigest digest = newDigest();
        copy(in, OutputStream.nullOutputStream(), size, digest);
        return hex(digest);
    }

    /**
     * Copies exactly {@code size} bytes while hashing them, then checks the result against
     * {@code expected}. Nothing is written beyond the given stream, so callers can stage the
     * bytes in a temporary file and only publish them once the checksum matches.
     */
    public static void copyVerified(InputStream in, OutputStream out, long size, String expected) throws IOException {
        MessageDigest digest = newDigest();
        copy(in, out, size, digest);
        String actual = hex(digest);
        if (!actual.equalsIgnoreCase(expected)) {
            throw new IOException("Checksum mismatch: expected " + expected + ", got " + actual);
        }
    }
}
