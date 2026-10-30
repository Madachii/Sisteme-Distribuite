package ro.ubm.distfs.common.message;

/**
 * {@code STORED} - the file was written and its checksum matched.
 */
public record Stored() implements Message {

    @Override
    public MessageType type() {
        return MessageType.STORED;
    }

    @Override
    public String encode() {
        return Message.line(type());
    }

    static Stored decode(String body) {
        if (!body.isEmpty()) {
            throw new IllegalArgumentException("STORED takes no fields, got: " + body);
        }
        return new Stored();
    }
}
