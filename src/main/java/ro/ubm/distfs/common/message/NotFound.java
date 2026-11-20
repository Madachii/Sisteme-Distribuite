package ro.ubm.distfs.common.message;

/**
 * {@code NOT_FOUND} - the node does not hold the requested file.
 */
public record NotFound() implements Message {

    @Override
    public MessageType type() {
        return MessageType.NOT_FOUND;
    }

    @Override
    public String encode() {
        return Message.line(type());
    }

    static NotFound decode(String body) {
        FieldReader.expectEmpty(MessageType.NOT_FOUND, body);
        return new NotFound();
    }
}
