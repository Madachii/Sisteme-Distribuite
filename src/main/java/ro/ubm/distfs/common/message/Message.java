package ro.ubm.distfs.common.message;

/**
 * A protocol message: a type tag followed by space-separated fields on one line.
 */
public sealed interface Message
        permits Heartbeat, Put, Get, Stored, File, NotFound, Error {

    MessageType type();

    /**
     * The full wire line, without the trailing newline.
     */
    String encode();

    /**
     * A message that carries no fields.
     */
    static String line(MessageType type) {
        return type.name();
    }

    /**
     * A message followed by its fields, in wire order.
     */
    static String line(MessageType type, String... fields) {
        return type.name() + " " + String.join(" ", fields);
    }
}
