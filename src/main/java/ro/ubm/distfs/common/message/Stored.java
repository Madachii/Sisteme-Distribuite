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
        FieldReader.expectEmpty(MessageType.STORED, body);
        return new Stored();
    }
}
