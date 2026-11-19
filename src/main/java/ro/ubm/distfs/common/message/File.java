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
        FieldReader reader = new FieldReader(MessageType.FILE, body, 2, 2);
        return new File(reader.nextLong(), reader.nextString());
    }
}
