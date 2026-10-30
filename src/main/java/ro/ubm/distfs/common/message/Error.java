package ro.ubm.distfs.common.message;

import java.util.Objects;

/**
 * {@code ERROR <reason>} - the reason is the rest of the line, so it may contain spaces.
 */
public record Error(String reason) implements Message {

    public Error {
        Objects.requireNonNull(reason, "reason");
    }

    @Override
    public MessageType type() {
        return MessageType.ERROR;
    }

    @Override
    public String encode() {
        return reason.isEmpty() ? Message.line(type()) : Message.line(type(), reason.replace('\n', ' '));
    }

    static Error decode(String body) {
        return new Error(body);
    }
}
