package ro.ubm.distfs.common.message;

import java.util.Objects;

/**
 * {@code FILE <size> <sha256>}, followed by {@code size} bytes.
 */
public record File(long size, String checksum) implements Message {

    public File {
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        Objects.requireNonNull(checksum, "checksum");
    }

    @Override
    public MessageType type() {
        return MessageType.FILE;
    }

    @Override
    public String encode() {
        return Message.line(type(), Long.toString(size), checksum);
    }

    static File decode(String body) {
        String[] parts = body.split(" ");
        if (parts.length != 2) {
            throw new IllegalArgumentException("FILE needs 2 fields, got " + parts.length);
        }
        return new File(Long.parseLong(parts[0]), parts[1]);
    }
}
