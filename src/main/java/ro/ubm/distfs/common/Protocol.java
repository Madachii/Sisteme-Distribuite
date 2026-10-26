package ro.ubm.distfs.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Wire format for TCP messages between client and node.
 * Every message starts with its type as a UTF string, followed by its fields.
 *
 * PUT:  id, name, size (long), checksum, nextNode ("" if none), then size bytes
 *       -> STORED | ERROR, reason
 * GET:  id
 *       -> FILE, size (long), then size bytes | NOT_FOUND
 */
public final class Protocol {

    public static final String PUT = "PUT";
    public static final String GET = "GET";
    public static final String STORED = "STORED";
    public static final String FILE = "FILE";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String ERROR = "ERROR";

    private static final int BUFFER_SIZE = 64 * 1024;

    private Protocol() {
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
}
