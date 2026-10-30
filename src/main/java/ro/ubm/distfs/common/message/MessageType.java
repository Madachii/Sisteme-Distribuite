package ro.ubm.distfs.common.message;

/**
 * Message names on the wire (docs/cerinte.md).
 */
public enum MessageType {
    HEARTBEAT,
    PUT,
    GET,
    STORED,
    FILE,
    NOT_FOUND,
    ERROR;

    public static MessageType from(String token) {
        for (MessageType type : values()) {
            if (type.name().equals(token)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + token);
    }
}
