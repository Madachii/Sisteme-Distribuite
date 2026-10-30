package ro.ubm.distfs.common.message;

import java.util.Objects;
import java.util.UUID;

/**
 * {@code GET <fileId>}
 */
public record Get(UUID fileId) implements Message {

    public Get {
        Objects.requireNonNull(fileId, "fileId");
    }

    @Override
    public MessageType type() {
        return MessageType.GET;
    }

    @Override
    public String encode() {
        return Message.line(type(), fileId.toString());
    }

    static Get decode(String body) {
        return new Get(UUID.fromString(body));
    }
}
