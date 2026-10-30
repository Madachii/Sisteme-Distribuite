package ro.ubm.distfs.common.message;

import ro.ubm.distfs.common.Protocol;

import java.util.Objects;
import java.util.UUID;

/**
 * {@code PUT <fileId> <name> <size> <sha256> [<nextHost:port>]}, followed by {@code size} bytes.
 *
 * @param nextNode the node the file should be forwarded to, or null if this is the only copy
 */
public record Put(UUID fileId, String name, long size, String checksum, String nextNode) implements Message {

    public Put {
        Objects.requireNonNull(fileId, "fileId");
        if (name == null || !Protocol.isValidName(name)) {
            throw new IllegalArgumentException("Invalid name: " + name);
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        Objects.requireNonNull(checksum, "checksum");
    }

    @Override
    public MessageType type() {
        return MessageType.PUT;
    }

    @Override
    public String encode() {
        return nextNode == null
                ? Message.line(type(), fileId.toString(), name, Long.toString(size), checksum)
                : Message.line(type(), fileId.toString(), name, Long.toString(size), checksum, nextNode);
    }

    static Put decode(String body) {
        FieldReader reader = new FieldReader(MessageType.PUT, body, 4, 5);

        UUID fileId = reader.nextUUID();
        String name = reader.nextString();
        long size = reader.nextLong();
        String checksum = reader.nextString();
        String nextNode = reader.nextOptionalString();

        return new Put(fileId, name, size, checksum, nextNode);
    }
}
