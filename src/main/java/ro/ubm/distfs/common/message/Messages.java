package ro.ubm.distfs.common.message;

/**
 * Turns a wire line into the message it names.
 */
public final class Messages {

    private Messages() {
    }

    /**
     * @throws IllegalArgumentException if the line is not a well-formed message
     */
    public static Message decode(String line) {
        String trimmed = line.strip();
        int space = trimmed.indexOf(' ');
        String tag = space < 0 ? trimmed : trimmed.substring(0, space);
        String body = space < 0 ? "" : trimmed.substring(space + 1);

        return switch (MessageType.from(tag)) {
            case HEARTBEAT -> Heartbeat.decode(body);
            case PUT -> Put.decode(body);
            case GET -> Get.decode(body);
            case STORED -> Stored.decode(body);
            case FILE -> File.decode(body);
            case NOT_FOUND -> NotFound.decode(body);
            case ERROR -> Error.decode(body);
        };
    }
}
