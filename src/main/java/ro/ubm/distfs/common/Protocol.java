package ro.ubm.distfs.common;

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
 * TCP wire format (docs/cerinte.md, section 4): every message is one text line ending in {@code \n},
 * the message name followed by space-separated fields. One request and one response per connection.
 *
 * PUT <fileId> <name> <size> <sha256> [<nextHost:port>]\n + size bytes
 *       -> STORED | ERROR <reason>
 * GET <fileId>
 *       -> FILE <size> <sha256>\n + size bytes | NOT_FOUND
 */
public final class Protocol {

    public static final String PUT = "PUT";
    public static final String GET = "GET";
    public static final String STORED = "STORED";
    public static final String FILE = "FILE";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String ERROR = "ERROR";

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_LINE = 8 * 1024;

    private Protocol() {
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
}
